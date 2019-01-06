import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ZooKeeperClient implements Watcher {

    private static String blockchainRootPath = "/Blockchain";
    private static String membershipRootPath = "/Membership";
    private ZooKeeper zk;
    private Server server;
    private String membershipPath;


    ZooKeeperClient(Server server) {
        this.server = server;
        membershipPath = membershipRootPath + "/" + server.getId();
        try {
            System.out.println(server.zkAddress);
            this.zk = new ZooKeeper(server.zkAddress, 3000, this);
        } catch (IOException e1) {
            // TODO - need to think what to do if there is an error here
            e1.printStackTrace();
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
            zk.create(membershipPath, getMembershipData(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        updateMembership();
        updateBlockchain();
    }

    private byte[] getMembershipData() {
        String data = server.getHostname() + "::" + server.getServerPort();
        return data.getBytes();
    }

    String[] getServerMembershipData(Integer serverId) {
        Stat memberStat = new Stat();
        String memberPath = membershipRootPath + "/" + serverId;
        byte[] dataBytes = new byte[0];
        try {
            dataBytes = zk.getData(memberPath, false, memberStat);
        } catch (KeeperException | InterruptedException e1) {
            e1.printStackTrace();
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {

            }
        }
        return getServerData(dataBytes);
    }

    private String[] getServerData(byte[] data) {
        return new String(data).split("::");
    }

    public void updateServerMembershipNode() {
        try {
            zk.setData(membershipPath, getMembershipData(), zk.exists(membershipPath, false).getVersion());
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updateMembership() {
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
            } catch (InterruptedException e) {}
        }
        server.onMembershipChange(serversView);
    }

    private void updateBlockchain() {
        //TODO: add getting and apllying the changes to server
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.None) {
            System.out.println("Watcher called on state change with new state " + event.getState());
            switch (event.getState()) {
                case SyncConnected:
                    init();
                    break;
                case Expired:
                    try {
                        this.zk = new ZooKeeper(server.zkAddress, 3000, this);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                default:
                    break;
            }
        }
        else if (event.getType() == Event.EventType.NodeChildrenChanged) {
            System.out.println("Watcher called with event type " + event.getType() + " on znode " + event.getPath());
            if (event.getPath().equals(blockchainRootPath)) {
                updateBlockchain();
            }
            if (event.getPath().equals(membershipRootPath)) {
                updateMembership();
            }
        }
        else {
            System.out.println("Watcher called with event type " + event.getType());
        }
    }

    public static void main(String [] args) throws IOException {
        int CLIENT_PORT = 55555;
        int SERVER_PORT = 44444;
        String LOCALHOST = "localhost";
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
}
