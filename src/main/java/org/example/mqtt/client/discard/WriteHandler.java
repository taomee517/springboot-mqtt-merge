package org.example.mqtt.client.discard;

/**
 * @author 罗涛
 * @title ReadHandler
 * @date 2020/10/14 19:35
 */
public interface WriteHandler extends MessageHandler{
    void messageWrite(Object msg);
}
