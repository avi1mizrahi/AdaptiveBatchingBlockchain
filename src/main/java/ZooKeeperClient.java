import ServerCommunication.BlockId;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ZooKeeperClient implements Watcher {

    private final InetSocketAddress zkAddress = InetSocketAddress.createUnresolved("127.0.0.1",
                                                                                   2181);

    private static final String    blockchainRootPath = "/Blockchain";
    private static final String    membershipRootPath = "/Membership";
    private final        String    membershipPath;
    private final        Server    server;
    private              ZooKeeper zk;
    private              Integer   lastSeenBlock      = 0;

    ZooKeeperClient(@NotNull Server server) {
        this.server = server;
        membershipPath = membershipRootPath + "/" + server.getId();
        System.out.println(zkAddress);
        try {
            this.zk = createZooKeeper();
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
                                           .setClientPort(CLIENT_PORT)
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

    public void shutdown() {
        try {
            zk.close();
        } catch (InterruptedException ignored) {
        }
    }

    private void init() {
        // Try to create the blockchain first block if not exist
        try {
            zk.create(blockchainRootPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException e) {
            // It's OK
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        // Try to create the membership first block if not exist
        try {
            zk.create(membershipRootPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException e) {
            // It's OK
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        // Try to create the znode of this sever under /membership.
        try {
            zk.create(membershipPath,
                      getMembershipData(),
                      ZooDefs.Ids.OPEN_ACL_UNSAFE,
                      CreateMode.EPHEMERAL_SEQUENTIAL);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        updateMembership();
        updateBlockchain();
    }

    @NotNull
    private byte[] getMembershipData() {
        String data = server.getHostname() + "::" + server.getServerPort();
        return data.getBytes();
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
            try {
                Thread.sleep(3);
            } catch (InterruptedException ignored) {

            }
        }
        return "";
    }

    @Nullable
    private BlockId getBlockId(int blockIdx) {
        String blockPath = blockchainRootPath + "/" + blockIdx;
        try {
            return BlockId.parseFrom(getData(blockPath).getBytes());
        } catch (InvalidProtocolBufferException ignored) {
            assert false;
            return BlockId.getDefaultInstance();
        }
    }

    public InetSocketAddress getServerMembershipData(Integer serverId) {
        String   memberPath = membershipRootPath + "/" + serverId;
        String[] data       = getData(memberPath).split("::");
        return InetSocketAddress.createUnresolved(data[0], Integer.parseInt(data[1]));
    }

    public void updateServerMembershipNode() {
        try {
            zk.setData(membershipPath,
                       getMembershipData(),
                       zk.exists(membershipPath, false).getVersion());
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updateMembership() {
        synchronized (membershipPath) {
            Set<Integer> serversView = new HashSet<>();
            try {
                List<String> children;
                children = zk.getChildren(membershipRootPath, this);
                for (String child : children) {
                    Integer serverId = Integer.parseInt(child);
                    if (!serverId.equals(server.getId())) {
                        serversView.add(serverId);
                    }
                }
            } catch (KeeperException | InterruptedException e1) {
                e1.printStackTrace();
                try {
                    Thread.sleep(3);
                } catch (InterruptedException ignored) {
                }
            }
            server.onMembershipChange(serversView);
        }
    }

    void postBlock(BlockId blockId) {
        // Try to create the znode of this sever under /membership.
        try {
            zk.create(blockchainRootPath,
                      blockId.toByteArray(),
                      ZooDefs.Ids.OPEN_ACL_UNSAFE,
                      CreateMode.PERSISTENT_SEQUENTIAL);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updateBlockchain() {
        synchronized (blockchainRootPath) {
            try {
                zk.getChildren(blockchainRootPath, this)
                  .stream()
                  .map(Integer::parseInt)
                  .filter(blockIdx -> blockIdx > lastSeenBlock)
                  .sorted()
                  .forEachOrdered(blockIdx -> {
                      server.onBlockChained(getBlockId(blockIdx));
                      lastSeenBlock = blockIdx;
                  });

            } catch (KeeperException | InterruptedException e1) {
                e1.printStackTrace();
                try {
                    Thread.sleep(3);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                System.out.println(
                        "Watcher called on state change with new state " + event.getState());
                switch (event.getState()) {
                    case SyncConnected:
                        init();
                        break;
                    case Expired:
                        try {
                            zk = createZooKeeper();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    default:
                        break;
                }
                break;
            case NodeChildrenChanged:
                System.out.println(
                        "Watcher called with event type " + event.getType() + " on znode " +
                                event.getPath());
                if (event.getPath().equals(membershipRootPath)) {
                    updateMembership();
                }
                if (event.getPath().equals(blockchainRootPath)) {
                    updateBlockchain();
                }

                break;
            default:
                System.out.println("Watcher called with event type " + event.getType());
        }
    }

    @NotNull
    @Contract(" -> new")
    private ZooKeeper createZooKeeper() throws IOException {
        return new ZooKeeper(zkAddress.toString(), 3000, this);
    }

}
