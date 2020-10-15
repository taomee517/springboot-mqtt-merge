package org.example.mqtt.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author 罗涛
 * @title MqttConfig
 * @date 2020/10/10 17:37
 */

@Setter
@Getter
@Component
@ConfigurationProperties("spring.mqtt.client")
public class MqttClientProperties {

    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 连接地址
     */
    private String hostUrl;
    /**
     * 客户Id
     */
    private String clientId;

    /**
     * 超时时间
     */
    private int timeout;
    /**
     * 保持连接数
     */
    private int keepalive;
}


