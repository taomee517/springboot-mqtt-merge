package org.example.mqtt.client.handlers;

/**
 * @author 罗涛
 * @title IPublish
 * @date 2020/10/14 20:30
 */
public interface IPublish {
    /**
     * 发布
     *
     * @param qos         连接方式
     * @param retained    是否保留
     * @param topic       主题
     * @param pushMessage 消息体
     */
    void publish(int qos, boolean retained, String topic, String pushMessage);
}
