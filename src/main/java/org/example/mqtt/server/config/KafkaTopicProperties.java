package org.example.mqtt.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author 罗涛
 * @title KafkaTopicProperties
 * @date 2020/9/18 19:18
 */

@Data
@Component
@ConfigurationProperties(prefix = "acceptor.kafka.topic")
public class KafkaTopicProperties {
    public String data;
    public String event;
    public String deviceLog;
}
