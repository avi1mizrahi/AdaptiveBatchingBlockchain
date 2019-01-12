package App;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class Config {
    @Bean
    public static int id() {
        return id;
    }

    @Bean
    public static String host() {
        return host;
    }

    static int    id;
    static String host;


}

@SpringBootApplication
public class Application {

    @Autowired
    public Application(ApplicationArguments args) {
        List<String> argv = args.getNonOptionArgs();
        if (!argv.isEmpty()) {
            System.out.println("Got argv:" + argv);
            Config.id = Integer.valueOf(argv.get(0));
            Config.host = argv.get(1);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}