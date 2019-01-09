package Blockchain;

import Blockchain.Transaction.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final io.grpc.Server  serverListener;
    private final ZooKeeperClient zkClient;


    Server(int id, int serverPort, Duration blockWindow) {
        this.id = id;
        blockBuilder = new BlockBuilder(id);
        address = SocketAddressFactory.from("localhost", serverPort);
        batchingStrategy = new TimedAdaptiveBatching(new BatcherProxy() {
            @Override
            public void batch() throws InterruptedException {
                trySealBlock();
            }

            @Override
            public void interrupted() {
                shutdown();
            }
        }, blockWindow);
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
            lostPeerIds.add(peerId);
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
        LOG("cleaning server " + serverId);
        // TODO: synchronize with ZK to find the latest block chained by this server.
        //  this can by done once for all disconnected servers
        int latestBlock = Integer.MAX_VALUE - 1; // TODO replace with the above real value

        List<BlockId> toBeDeleted = new ArrayList<>();
        pending.forEachKey(1024, blockId -> {
            if (blockId.getServerId() == serverId && blockId.getSerialNum() > latestBlock) {
                toBeDeleted.add(blockId);
            }
        });

        toBeDeleted.forEach(pending::remove);
    }

    void onBlockChained(ServerCommunication.BlockId blockIdMsg, Integer idx) {
        var blockId = BlockId.from(blockIdMsg);
        LOG("onBlockChained " + blockId);

        int chainSize = ledger.chainSize();
        if (chainSize != idx) {
            LOG("not in order: " + chainSize + "!=" + idx);
            return;
        }

        var blockMsg = pending.remove(blockId);
        if (blockMsg == null) {
            var message = "Block " + blockId + " wasn't received yet";
            LOG(message);
            throw new RuntimeException(message);//TODO remove, what if it's not here yet? need to pull
        }

        Block block = Block.from(blockMsg);
        ledger.apply(block);
        synchronized (results) {
            results.putAll(block.getResults());
        }

        LOG( " appended!");
        System.out.println(block);
    }

    private boolean pushBlock(@NotNull Block block) throws InterruptedException {
        LOG("pushBlock: " + block.getId());
        BlockMsg blockMsg = block.toBlockMsg();

        PushBlockReq req = PushBlockReq.newBuilder().setBlock(blockMsg).build();

        var finished = new Semaphore(0);
        var nOk      = new AtomicInteger(0);

        class PushBlockObserver implements StreamObserver<PushBlockRsp> {
            @Override
            public void onNext(PushBlockRsp value) {
                if (value.getSuccess()) nOk.incrementAndGet();
            }

            @Override
            public void onError(Throwable t) {
                finished.release();
            }

            @Override
            public void onCompleted() {
                finished.release();
            }
        }

        peers.forEach((integer, peerServer) -> peerServer.stub()
                                                         .withDeadlineAfter(5, TimeUnit.SECONDS)
                                                         .pushBlock(req,
                                                                    new PushBlockObserver()));

        //TODO: wait for more than half, not to everyone
        finished.acquire(peers.size());

        if (nOk.get() < peers.size()) {
            return false;
        }

        pending.putIfAbsent(block.getId(), blockMsg);
        return true;
    }

    private void trySealBlock() throws InterruptedException {
        if (blockBuilder.isEmpty()) {
            return;
        }

        LOG("sealing block");

        final Block   block = blockBuilder.seal();
        final BlockId id    = block.getId();

        int tries = 5;
        for (; tries > 0 ; --tries) {
            if (pushBlock(block)) break;
        }
        if (tries == 0) throw new RuntimeException("Failed to push block " + id);

        int idx = -1;

        tries = 5;
        var blockIdMsg = id.toBlockIdMsg();
        for (; tries > 0; --tries) {
            try {
                idx = zkClient.postBlock(blockIdMsg);
                break;
            } catch (KeeperException e) {
                e.printStackTrace();
            }
        }
        if (idx == -1) throw new RuntimeException("Failed to reach consensus on block " + id);
        LOG("consensus reached");
        onBlockChained(blockIdMsg, idx);
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
            var blockId = BlockId.from(block.getId());

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

    public TxId deleteAccount(Account account) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            LOG("Delete " + account);

            return blockBuilder.append(new DeleteAccountTx(account));
        }
    }

    public TxId addAmount(Account account, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {
            LOG(account + ", add " + amount);

            return blockBuilder.append(new DepositTx(account, amount));

        }
    }

    public Integer getAmount(Account account) {
        //LOG("getAmount " + account);

        return ledger.get(account);
    }

    public TxId transfer(Account from, Account to, int amount) {
        try (var ignored = batchingStrategy.createRequestWindow()) {

            LOG("from " + from + " to " + to + " : " + amount);

            return blockBuilder.append(new TransferTx(from, to, amount));
        }
    }

    public Transaction.Result getTxStatus(TxId txId) {
        // LOG("getTxStatus " + txId);
        Transaction.Result result;
        synchronized (results) {
            result = results.get(txId);
        }
        if (result != null) {
            return result;
        }
        return ledger.getStatus(txId).orElse(null);
    }

    public Transaction.Result deleteTxStatus(TxId txId) {
        LOG("deleteTxStatus " + txId);
        Transaction.Result result;
        synchronized (results) {
            result = results.remove(txId);
        }
        return result;
    }
}
