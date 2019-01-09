package App;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ZooKeeperServer {
    private String zkRoot;
    private String zkDataDir;

    public ZooKeeperServer() throws IOException, URISyntaxException {
        Properties prop = new java.util.Properties();

        ClassLoader classLoader = getClass().getClassLoader();
        Path path = Paths.get(classLoader.getResource("config.properties").toURI());
        File file = path.toFile();
        System.out.println("The config file path is " + file.getAbsolutePath());
        FileInputStream input = new FileInputStream(file);

        // Load a properties file
        prop.load(input);

        zkRoot = prop.getProperty("ZK_ROOT");
        zkDataDir = prop.getProperty("ZK_DATA_DIR");

        input.close();
        System.out.println("Loaded properties from properties file");
    }

    private void execute(String ... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        System.out.println(pb.redirectOutput());
        pb.directory(new File(zkRoot, "bin"));
        Process p = pb.start();
        p.waitFor();
    }

    public void start() throws IOException, InterruptedException {
        execute("./zkServer.sh", "start");
    }

    public void stop() throws IOException, InterruptedException {
        execute("./zkServer.sh", "stop");
    }

    public void removeDataDir() throws IOException {
        FileUtils.deleteDirectory(new File(zkDataDir));
    }
}
