package org.example.mqtt.client.protocol;

import java.util.List;

/**
 * @author 罗涛
 * @title IMqttProtocol
 * @date 2020/10/14 19:44
 */
public interface IMqttProtocol {
    // 设备类型
    int sensorType();

    String clientId();

    String username();

    String password();


    // 连接建立后默认订阅的主题
    List<String> defaultTopics();

    // 校验消息是否完整
    boolean messageValidate(byte[] payload);

    // 消息解析
    Object parse(byte[] payload);


}
