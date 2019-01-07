package App;

import Blockchain.Server;
import Blockchain.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class Application {
    static public Server server;

    static private void init() throws IOException {
        int port = 33333;
        server = new ServerBuilder().setId(1)
                .setServerPort(port)
                .createServer()
                .start();
    }


    public static void main(String[] args) throws IOException {
        init();
        SpringApplication.run(Application.class, args);
    }
}