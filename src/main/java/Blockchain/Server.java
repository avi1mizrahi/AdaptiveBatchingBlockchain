package Blockchain;

import Blockchain.Transaction.*;
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
    private final int               id;
    private final InetSocketAddress address;

    private final BatchingStrategy batchingStrategy;
    private final BlockBuilder     blockBuilder;
    private final Ledger           ledger = new Ledger();

    private final ConcurrentHashMap<BlockId, BlockMsg> pending = new ConcurrentHashMap<>();
    private final BoundedMap<TxId, Transaction.Result> results = new BoundedMap<>(1 << 15);

    private final Set<Integer>             lostPeerIds       = new HashSet<>();// TODO: protect
    private final Map<Integer, PeerServer> peers             = new HashMap<>();// TODO: protect
    private final Map<Integer, Integer>    lastChainedByPeer = new HashMap<>();// TODO: protect

    private final io.grpc.Server  serverListener;
    private final ZooKeeperClient zkClient;


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

    private void LOG(String msg) {
        System.out.println("[SERVER] " + msg);
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
        LOG("onMembershipChange: newView" + newView);
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
            lastChainedByPeer.putIfAbsent(serverId, -1);
            peers.computeIfAbsent(serverId,
                                  id -> new PeerServer(zkClient.getServerMembershipData(id)));
        }
    }

    private void cleanUpServerBlocks(int serverId) {
        LOG("cleaning server " + serverId);
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
        Integer blockIdx = lastChainedByPeer.compute(blockId.getServerId(), (peer, idx) -> {
            int newIdx = blockId.getSerialNumber();
            if (idx + 1 != newIdx)
                return idx;
            return newIdx;
        });

        if (blockIdx != blockId.getSerialNumber()) // TODO: can we do better?
            throw new RuntimeException("Missing Block! got " + blockId +
                                               " but last chained is " + blockIdx);

        var blockMsg = pending.remove(blockId);
        if (blockMsg == null) {
            var message = "Block " + blockId + " wasn't received yet";
            LOG(message);
            throw new RuntimeException(message);//TODO remove, what if it's not here yet? need to pull
        }

        Block block = Block.from(blockMsg);
        ledger.apply(block);
        results.putAll(block.getResults());

        LOG( " appended!");
        System.out.println(block);
    }

    private void pushBlock(@NotNull Block block) {
        LOG("pushBlock: " + block.getId());
        BlockMsg blockMsg = block.toBlockMsg();
        //TODO: send this to others
        //TODO: wait for more than half
        pending.putIfAbsent(block.getId(), blockMsg);
    }

    private void trySealBlock() {
        if (blockBuilder.isEmpty()) {
            return;
        }

        LOG("sealing block");

        Block block = blockBuilder.seal();

        pushBlock(block);
        zkClient.postBlock(block.getId());
        LOG("consensus reached");
        // TODO: should apply only after we sure it is the latest, try to bring the rest if not
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

            LOG("pushBlock requested " + blockId);

            if (lostPeerIds.contains(blockId.getServerId())) {
                LOG("pushBlock rejected");
                return; // don't listen to this zombie
            }

            pending.putIfAbsent(blockId, block);

            responseObserver.onNext(PushBlockRsp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void pullBlock(PullBlockReq request, StreamObserver<PullBlockRsp> responseObserver) {
            BlockMsg block = pending.get(request.getId());

            LOG("pullBlock requested " + block.getId());

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
            LOG("Create account");

            return blockBuilder.append(new NewAccountTx());
        }
    }

    public TxId deleteAccount(int id) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var account = Account.from(id);
            LOG("Delete " + account);

            return blockBuilder.append(new DeleteAccountTx(account));
        }
    }

    public TxId addAmount(int id, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var account = Account.from(id);
            LOG(account + ", add " + amount);

            return blockBuilder.append(new DepositTx(account, amount));

        }
    }

    public Integer getAmount(int id) {
        var account = Account.from(id);
        LOG("getAmount " + account);

        return ledger.get(account);
    }

    public TxId transfer(int from, int to, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            var accountFrom = Account.from(from);
            var accountTo   = Account.from(to);

            LOG("from " + from + " to " + to + " : " + amount);

            return blockBuilder.append(new TransferTx(accountFrom, accountTo, amount));
        }
    }

    public Transaction.Result getTxStatus(TxId txId) {
        Transaction.Result result = results.remove(txId);
        if (result == null) {
            return null;//TODO: check in the blockchain and return if it is there
        }
        return result;
    }
}
