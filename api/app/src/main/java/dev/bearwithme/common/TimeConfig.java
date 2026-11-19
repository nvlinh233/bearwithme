package dev.bearwithme.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FR-D3: all day boundaries use Asia/Ho_Chi_Minh. Inject {@link Clock} instead of calling {@code now()}. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
