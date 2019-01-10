package App;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
class Config {
    static AtomicInteger id = new AtomicInteger(1);

    @Bean
    int id() {
        return Config.id.getAndIncrement();
    }
}

@SpringBootApplication
public class Application {

    @Autowired
    public Application(ApplicationArguments args) {
        List<String> argv = args.getNonOptionArgs();
        if (!argv.isEmpty())
            Config.id.set(Integer.valueOf(argv.get(0)));
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}