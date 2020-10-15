package org.example.mqtt.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author 罗涛
 * @title MqttConfig
 * @date 2020/10/13 13:52
 */

@Data
@Component
@ConfigurationProperties(prefix = "spring.mqtt.server")
public class MqttServerProperties {
    private Integer tcpPort;
    private Integer sslPort;
    private Integer wsPort;
    private Integer wssPort;



    /**
     * WebSocket Path值, 默认值 /mqtt
     */
    private String websocketPath = "/mqtt";

    private String username;
    private String password;
    private Boolean authCheckEnable;

    private String serverKeyPath;
    private String rootKeyPath;

    /**
     * SSL密钥文件密码
     */
    private String sslPassword;
    private Boolean sslEnable;
}
