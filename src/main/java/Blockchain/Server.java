package Blockchain;

import ServerCommunication.*;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Server {
    private final BatchingStrategy                     batchingStrategy;
    private final int                                  id;
    private final Ledger                               ledger      = new Ledger();
    private final Set<Integer>                         lostPeerIds = new HashSet<>();// TODO: protect
    private final Map<Integer, PeerServer>             peers       = new HashMap<>();// TODO: protect
    private final ConcurrentHashMap<BlockId, BlockMsg> pending     = new ConcurrentHashMap<>();
    private final BoundedMap<TxId, Transaction.Result> results     = new BoundedMap<>(1 << 15);
    private final BlockBuilder                         blockBuilder;
    private final io.grpc.Server                       serverListener;
    private final ZooKeeperClient                      zkClient;
    private final InetSocketAddress                    address;
    private       int                                  myBlockNum  = 0; // can be owned by blockBuilder

    Server(int id, int serverPort, Duration blockWindow) {
        this.id = id;
        blockBuilder = new BlockBuilder(id);
        address = SocketAddressFactory.from("localhost", serverPort);
        batchingStrategy = new TimedAdaptiveBatching(this::trySealBlock, blockWindow);
        serverListener = io.grpc.ServerBuilder.forPort(serverPort)
                                              .addService(new ServerRpc())
                                              .build();
        zkClient = new ZooKeeperClient(this);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 55555;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        Server server = new ServerBuilder().setId(1)
                                           .setServerPort(port - 11111)
                                           .createServer()
                                           .start();
        server.awaitTermination();
    }

    public Server start() throws IOException {
        batchingStrategy.start();
        serverListener.start();
        return this;
    }

    void awaitTermination() throws InterruptedException {
        serverListener.awaitTermination();
    }

    void shutdown() {
        batchingStrategy.shutdown();
        serverListener.shutdown();
        assert blockBuilder.isEmpty();
    }

    void onMembershipChange(Set<Integer> newView) {
        // clean up removed peers
        List<Integer> removedPeersIds = peers.keySet()
                                             .stream()
                                             .flatMap(peerId -> {
                                                 if (newView.contains(peerId))
                                                     return Stream.empty();
                                                 return Stream.of(peerId);
                                             }).collect(Collectors.toList());

        for (Integer peerId : removedPeersIds) {
            final PeerServer peer = peers.remove(peerId);
            peer.shutdown();
            cleanUpServerBlocks(peerId);
        }

        // add new peers
        for (Integer serverId : newView) {
            peers.computeIfAbsent(serverId,
                                  id -> new PeerServer(zkClient.getServerMembershipData(id)));
        }
    }

    private void cleanUpServerBlocks(int serverId) {
        // TODO: synchronize with ZK to find the latest block chained by this server.
        //  this can by done once for all disconnected servers
        int latestBlock = Integer.MAX_VALUE - 1; // TODO replace with the above real value

        lostPeerIds.add(serverId);

        List<BlockId> toBeDeleted = new ArrayList<>();
        pending.forEachKey(1024, blockId -> {
            if (blockId.getServerId() == serverId && blockId.getSerialNumber() > latestBlock) {
                toBeDeleted.add(blockId);
            }
        });

        toBeDeleted.forEach(pending::remove);
    }

    void onBlockChained(BlockId blockId) {
        var blockMsg = pending.remove(blockId);
        assert blockMsg != null;//TODO remove, what if it's not here yet? need to pull

        //TODO: check that it's new
        ledger.apply(Block.from(blockMsg));//TODO: update results
    }

    private BlockId pushBlock(@NotNull Block block) {
        var blockMsgBuilder = BlockMsg.newBuilder();
        block.addToBlockMsg(blockMsgBuilder);

        var blockId = BlockId.newBuilder()
                             .setServerId(id)
                             .setSerialNumber(myBlockNum++)
                             .build();
        blockMsgBuilder.setId(blockId);

        BlockMsg blockMsg = blockMsgBuilder.build();
        //TODO: send this to others
        //TODO: wait for more than half

        return blockId;
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }
        Block block   = blockBuilder.seal();
        var   blockId = pushBlock(block);
        zkClient.postBlock(blockId);
        // TODO: should apply only after we sure it is the latest, try to bring the rest if not
        ledger.apply(block);
        System.out.println("SERVER: appended!");
        System.out.println(block);

    }

    int getId() {
        return id;
    }

    InetSocketAddress getServerAddress() {
        return address;
    }

    private class ServerRpc extends ServerGrpc.ServerImplBase {
        @Override
        public void pushBlock(PushBlockReq request, StreamObserver<PushBlockRsp> responseObserver) {
            var block   = request.getBlock();
            var blockId = block.getId();

            if (lostPeerIds.contains(blockId.getServerId()))
                return; // don't listen to this zombie

            pending.putIfAbsent(blockId, block);

            responseObserver.onNext(PushBlockRsp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void pullBlock(PullBlockReq request, StreamObserver<PullBlockRsp> responseObserver) {
            var block = pending.get(request.getId());

            var builder = PullBlockRsp.newBuilder();
            if (block != null) {
                builder.setBlock(block).setSuccess(true);
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }


    public TxId createAccount() {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            System.out.println("SERVER: Create account");

            return blockBuilder.append(new NewAccountTx());
        }
    }

    public TxId deleteAccount(int id) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var account = Account.from(id);
            System.out.println("SERVER: Delete " + account);

            return blockBuilder.append(new DeleteAccountTx(account));
        }
    }

    public TxId addAmount(int id, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var account = Account.from(id);
            System.out.println("SERVER: " + account + ", add " + amount);

            return blockBuilder.append(new DepositTx(account, amount));

        }
    }

    public Integer getAmount(int id) {
        var account = Account.from(id);
        System.out.println("SERVER: getAmount " + account);

        return ledger.get(account);
    }

    public TxId transfer(int from, int to, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var accountFrom = Account.from(from);
            var accountTo   = Account.from(to);

            System.out.println("SERVER: from " + from + " to " + to + " : " + amount);

            return blockBuilder.append(new TransferTx(accountFrom, accountTo, amount));
        }
    }

    public class TxResult {
        private final Account account;
        private final Boolean isCommitted;

        public TxResult(Account account, Boolean isCommitted) {
            this.account = account;
            this.isCommitted = isCommitted;
        }

        public Account getAccount() {
            return account;
        }

        public boolean getIsCommitted() {
            return isCommitted;
        }
    }

    public TxResult getTxStatus(TxId txId) {
        Transaction.Result result = results.remove(txId);
        if (result == null) {
            //TODO: check in the blockchain
        }
        assert result != null;
        if (result.getClass().equals(NewAccountTx.Result.class)) {
            return new TxResult(((NewAccountTx.Result) result).getNewAccount(), null);
        }
        return new TxResult(null, result.isCommitted());
    }
}
