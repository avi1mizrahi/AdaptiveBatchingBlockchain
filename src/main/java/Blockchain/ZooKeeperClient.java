package Blockchain;

import ServerCommunication.BlockId;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.FileAppender;
import org.apache.log4j.SimpleLayout;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class ZooKeeperClient implements Watcher {

    private final InetSocketAddress zkAddress = SocketAddressFactory.from("127.0.0.1",
                                                                          2181);

    private static final String    blockchainRootPath = "/Blockchain";
    private static final String    membershipRootPath = "/Membership";
    private final        String    membershipPath;
    private final        Server    server;
    private              ZooKeeper zk;
    private              Integer   lastSeenBlock      = -1;

    private static void LOG(Object msg) {
        System.out.println("[ZkClient] " + msg);
    }

    ZooKeeperClient(@NotNull Server server) {
        try {
            BasicConfigurator.configure(new FileAppender(new SimpleLayout(), "/dev/null"));
        } catch (IOException ignored) {
        }

        this.server = server;
        membershipPath = membershipRootPath + "/" + server.getId();
        LOG(zkAddress);
        try {
            zk = createZooKeeper();
        } catch (IOException e1) {
            // TODO: need to think what to do if there is an error here
            e1.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        int    CLIENT_PORT = 55555;
        int    SERVER_PORT = 44444;
        String LOCALHOST   = "localhost";
        Server server = new ServerBuilder().setId(1)
                                           .setServerPort(SERVER_PORT)
                                           .setBlockWindow(Duration.ofMillis(100)) // TODO: 100 is just to accelerate the tests, don't know what is "good value"
                                           .createServer()
                                           .start();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ZooKeeperClient zookeeperClient = new ZooKeeperClient(server);

    }

    private void init() throws KeeperException, InterruptedException {
        LOG("init");
        // Try to create the membership first block if not exist
        try {
            zk.create(membershipRootPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
            // It's OK
        }

        // Try to create the znode of this sever under /membership.
        zk.create(membershipPath,
                  getMembershipData(),
                  ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);

        // Try to create the blockchain first block if not exist
        try {
            zk.create(blockchainRootPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
            // It's OK
        }

        updateMembership();
        updateBlockchain();
    }

    @NotNull
    private byte[] getMembershipData() {
        return server.getServerAddress().toString().getBytes();
    }

    @NotNull
    private String getData(String path) {
        Stat   stat = new Stat();
        byte[] dataBytes;
        try {
            dataBytes = zk.getData(path, false, stat);
            return new String(dataBytes);
        } catch (KeeperException | InterruptedException e1) {
            e1.printStackTrace();
        }
        return "";
    }

    private BlockId getBlockId(int blockIdx) {
        return getBlockId(String.format("%010d", blockIdx));
    }

    private BlockId getBlockId(String blockIdx) {
        String blockPath = blockchainRootPath + "/" + blockIdx;
        try {
            return BlockId.parseFrom(getData(blockPath).getBytes());
        } catch (InvalidProtocolBufferException ignored) {
            throw new RuntimeException();
        }
    }

    public InetSocketAddress getServerMembershipData(Integer serverId) {
        String memberPath = membershipRootPath + "/" + serverId;
        return SocketAddressFactory.from(getData(memberPath));
    }

    private void updateMembership() throws KeeperException, InterruptedException {
        List<String> children;
        LOG("updateMembership");
        children = zk.getChildren(membershipRootPath, true);

        Set<Integer> view = children.stream()
                                    .map(Integer::parseInt)
                                    .filter(integer -> !integer.equals(server.getId()))
                                    .collect(Collectors.toSet());

        server.onMembershipChange(view);
    }

    void postBlock(BlockId blockId) {
        LOG("postBlock " + blockId);
        // Try to create the znode of this block under /Blockchain.
        zk.create(blockchainRootPath + "/",
                  blockId.toByteArray(),
                  ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.PERSISTENT_SEQUENTIAL,
                  (rc, path, ctx, name) -> {
                      if (rc != KeeperException.Code.OK.intValue()) {
                          server.onBlockChainError(blockId);
                      }

                      String idx = name.substring(blockchainRootPath.length() + 1);
                      LOG("CHAINED! " + blockId + " idx=" +idx);
                  },
                  null);
    }

    private void updateBlockchain() throws KeeperException, InterruptedException {
        LOG("updateBlockchain");
        synchronized (blockchainRootPath) {
            zk.getChildren(blockchainRootPath, true)
              .stream()
              .map(Integer::parseInt)
              .filter(blockIdx -> blockIdx > lastSeenBlock)
              .sorted()
              .forEachOrdered(blockIdx -> {
                  server.onBlockChained(getBlockId(blockIdx), blockIdx);
                  lastSeenBlock = blockIdx;
              });
        }
    }

    @Override
    public void process(WatchedEvent event) {
        try {
            switch (event.getType()) {
                case None:
                    LOG("Watcher called on state change with new state " + event.getState());
                    switch (event.getState()) {
                        case SyncConnected:
                            init();
                            break;
                        case Expired:
                            //TODO: should we retry??
                            zk = createZooKeeper();
                        default:
                            break;
                    }
                    break;
                case NodeChildrenChanged:
                    LOG("Watcher called with event type " + event.getType() + " on znode " +
                                event.getPath());
                    if (event.getPath().equals(membershipRootPath)) {
                        updateMembership();
                    }
                    if (event.getPath().equals(blockchainRootPath)) {
                        updateBlockchain();
                    }

                    break;
                default:
                    LOG("Watcher called with event type " + event.getType());
            }
        } catch (KeeperException | InterruptedException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @NotNull
    @Contract(" -> new")
    private ZooKeeper createZooKeeper() throws IOException {
        return new ZooKeeper(zkAddress.toString(), 3000, this);
    }

}
