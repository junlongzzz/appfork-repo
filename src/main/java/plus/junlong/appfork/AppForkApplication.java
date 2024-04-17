package plus.junlong.appfork;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Junlong
 */
@SpringBootApplication
@AllArgsConstructor
public class AppForkApplication implements CommandLineRunner {

    private final Updater updater;

    public static void main(String[] args) {
        SpringApplication.run(AppForkApplication.class, args);
    }

    @Override
    public void run(String... args) {
        updater.run(args);
    }

}
