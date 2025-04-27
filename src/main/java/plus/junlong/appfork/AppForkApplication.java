package plus.junlong.appfork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Junlong
 */
@SpringBootApplication
public class AppForkApplication {

    public static void main(String[] args) {
        if (!System.getProperties().containsKey("user.timezone")) {
            // 如果时区属性为空则设置默认时区
            System.setProperty("user.timezone", "Asia/Shanghai");
        }
        SpringApplication.run(AppForkApplication.class, args);
    }

}
