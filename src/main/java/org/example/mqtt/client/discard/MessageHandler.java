package org.example.mqtt.client.discard;

/**
 * @author 罗涛
 * @title MessageHandler
 * @date 2020/10/14 19:37
 */
public interface MessageHandler {
    void exceptionCaught(Throwable throwable);
}
