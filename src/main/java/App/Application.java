package App;

import Blockchain.Server;
import Blockchain.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Arrays;

@SpringBootApplication
public class Application {
    static public Server server;

    public Application() throws IOException {
        int port = 33333;
        init(port);
    }

    static private void init(int port) throws IOException {
        server = new ServerBuilder().setId(1)
                                    .setServerPort(port)
                                    .createServer()
                                    .start();
    }

    public static void main(String[] args) throws IOException {
        int port = 33333;
        if (args.length > 0) {
            port = Integer.parseInt(args[args.length - 1]);
        }
        init(port);
        String[] argsList = Arrays.copyOfRange(args, 1, args.length);
        SpringApplication.run(Application.class, argsList);
    }
}