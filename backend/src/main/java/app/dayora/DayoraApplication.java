package app.dayora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DayoraApplication {
    public static void main(String[] args) {
        SpringApplication.run(DayoraApplication.class, args);
    }
}
