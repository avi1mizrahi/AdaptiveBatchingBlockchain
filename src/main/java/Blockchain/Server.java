package Blockchain;

import Blockchain.Batch.BatcherProxy;
import Blockchain.Batch.BatchingStrategy;
import Blockchain.Batch.TimedAdaptiveBatching;
import Blockchain.Transaction.*;
import ServerCommunication.*;
import io.grpc.stub.StreamObserver;
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
    private final int               faultSetSize;

    private       BatchingStrategy batchingStrategy;
    private final BlockBuilder     blockBuilder;
    private final Ledger           ledger           = new Ledger();

    private final ConcurrentHashMap<Integer, PeerServer> peers   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockId, BlockMsg>   pending = new ConcurrentHashMap<>();
    private final BoundedMap<TxId, Transaction.Result>   results = new BoundedMap<>(1 << 10);

    private final io.grpc.Server  serverListener;
    private final ZooKeeperClient zkClient;


    Server(int id, InetSocketAddress myAddress, BatchingStrategy batchingStrategy, int faultSetSize) {
        LOG(String.format("Created; id=%d, port=%s", id, myAddress));

        this.id = id;
        this.faultSetSize = faultSetSize;
        this.batchingStrategy = batchingStrategy;
        blockBuilder = new BlockBuilder(id);
        address = SocketAddressFactory.from(myAddress.getHostName(), myAddress.getPort());
        serverListener = io.grpc.ServerBuilder.forPort(myAddress.getPort())
                                              .addService(new ServerRpc())
                                              .build();
        zkClient = new ZooKeeperClient(this);
    }

    private static void LOG(Object msg) {
        System.out.println("[SERVER] " + msg);
    }

    public void changeStrategy(BatchingStrategy batchingStrategy) {
        this.batchingStrategy.shutdown();
        this.batchingStrategy = batchingStrategy;
        batchingStrategy.start(new Batcher());
    }

    public Server start() throws IOException {
        batchingStrategy.start(new Batcher());
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
            final PeerServer peer = peers.replace(peerId, null);
            if (peer != null) {
                peer.shutdown();
                cleanUpServerBlocks(peerId);
            }
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

    void onBlockChainError(ServerCommunication.BlockId blockIdMsg) {
        LOG("onBlockChainError " + BlockId.from(blockIdMsg));
        zkClient.postBlock(blockIdMsg);
    }

    void onBlockChained(ServerCommunication.BlockId blockIdMsg, Integer idx) {
        var blockId = BlockId.from(blockIdMsg);
        LOG("onBlockChained " + blockId);

        int chainSize = ledger.chainSize();
        if (chainSize != idx) {// actually it should never happen, zk keep the block state
            LOG("not in order: " + chainSize + "!=" + idx);
            return;
        }

        BlockMsg blockMsg;
        while ((blockMsg = pending.remove(blockId)) == null) {
            LOG("Block " + blockId + " wasn't received yet");
            pullBlock(blockId);

            try {
                Thread.sleep(Duration.ofSeconds(3).toMillis());
            } catch (InterruptedException ignored) {
            }
        }

        Block block = Block.from(blockMsg);
        ledger.apply(block);
        synchronized (results) {
            results.putAll(block.getResults());
        }

        LOG( " appended! " + blockId + " idx=" + idx);
        System.out.println(block);
    }

    private boolean pushBlock(@NotNull Block block) throws InterruptedException {
        LOG("pushBlock: " + block.getId());
        BlockMsg blockMsg = block.toBlockMsg();

        PushBlockReq req = PushBlockReq.newBuilder().setBlock(blockMsg).build();

        final var finished = new Semaphore(0);
        final var nOk      = new AtomicInteger(0);

        class PushBlockObserver implements StreamObserver<PushBlockRsp> {
            @Override
            public void onNext(PushBlockRsp value) {
                if (value.getSuccess()) nOk.incrementAndGet();
            }

            @Override
            public void onError(Throwable t) {
                // time out will be reached on semaphore,
                // no need to handle this case
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

        int nPeerToWait = Integer.min(faultSetSize, peers.size());

        finished.tryAcquire(nPeerToWait, 10, TimeUnit.SECONDS);

        if (nOk.get() < peers.size()) {
            return false;
        }

        pending.put(block.getId(), blockMsg);
        return true;
    }

    private void pullBlock(@NotNull BlockId blockId) {
        LOG("pullBlock: " + blockId);

        var req = PullBlockReq.newBuilder().setId(blockId.toBlockIdMsg()).build();

        class PullBlockObserver implements StreamObserver<PullBlockRsp> {
            @Override
            public void onNext(PullBlockRsp value) {
                if (value.getSuccess() && value.hasBlock()) {
                    pending.putIfAbsent(blockId, value.getBlock());
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        }

        // pick a random peer and ask him for this block
        peers.values()
             .stream()
             .skip((int) (peers.size() * Math.random()))
             .findFirst()
             .ifPresent(peerServer -> peerServer.stub()
                                                .withDeadlineAfter(5, TimeUnit.SECONDS)
                                                .pullBlock(req, new PullBlockObserver()));
    }

    private void trySealBlock() throws InterruptedException {
        if (blockBuilder.isEmpty()) {
            return;
        }

        LOG("sealing block");

        final Block   block = blockBuilder.seal();
        final BlockId id    = block.getId();

        int tries = 50;
        for (; tries > 0 ; --tries) {
            if (pushBlock(block)) break;
        }
        if (tries == 0) throw new RuntimeException("Failed to push block " + id);

        zkClient.postBlock(id.toBlockIdMsg());
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

            if (peers.get(blockId.getServerId()) == null) {
                LOG("pushBlock rejected");
                return; // don't listen to this zombie
            }

            pending.putIfAbsent(blockId, block);

            responseObserver.onNext(PushBlockRsp.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void pullBlock(PullBlockReq request, StreamObserver<PullBlockRsp> responseObserver) {
            BlockId id = BlockId.from(request.getId());
            LOG("pullBlock requested " + id);

            BlockMsg blockMsg;
            blockMsg = Optional.ofNullable(pending.get(id)) // check in the pending list
                               .orElseGet(
                                       () -> ledger.getBlock(id) // else check in the ledger
                                                   .map(Block::toBlockMsg) // transform it if found
                                                   .orElse(null));  // at least we tried
            var builder = PullBlockRsp.newBuilder();
            if (blockMsg != null) {
                builder.setBlock(blockMsg).setSuccess(true);
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

    public Set<Account> getAccounts() {
        //LOG("getAccounts");

        return ledger.getAccounts();
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

    private class Batcher implements BatcherProxy {
        @Override
        public void batch() throws InterruptedException {
            trySealBlock();
        }

        @Override
        public void interrupted() {
            shutdown();
        }
    }
}
