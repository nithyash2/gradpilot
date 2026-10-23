package com.gradpilot.push;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /** Without VAPID keys the app still runs, just without notifications. */
    @Bean
    PushGateway pushGateway(PushProperties properties, Clock clock) {
        if (!properties.configured()) {
            log.warn("Push notifications are off: set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY to enable them");
            return new PushGateway(null);
        }
        VapidKeys keys = new VapidKeys(properties.publicKey(), properties.privateKey(), properties.subject());
        return new PushGateway(new PushSender(keys, clock));
    }

    /** Holds the sender when push is configured. */
    public record PushGateway(PushSender sender) {

        public boolean enabled() {
            return sender != null;
        }
    }
}
