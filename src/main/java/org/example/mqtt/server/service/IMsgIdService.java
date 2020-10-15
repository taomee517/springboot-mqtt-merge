package org.example.mqtt.server.service;

/**
 * @author 罗涛
 * @title IMsgIdService
 * @date 2020/10/14 10:29
 */
public interface IMsgIdService {

    // messageId
    int getNextMessageId();

    void releaseMessageId(int messageId);
}
