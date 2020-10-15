package org.example.mqtt.server.service.impl;

import org.apache.ignite.IgniteCache;
import org.example.mqtt.server.service.IMsgIdService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.locks.Lock;

/**
 * @author 罗涛
 * @title MsgIdService
 * @date 2020/10/14 10:29
 */
@Service
public class MsgIdService implements IMsgIdService {

    private final int MIN_MSG_ID = 1;

    private final int MAX_MSG_ID = 65535;

    private final int lock = 0;

    @Resource
    private IgniteCache<Integer, Integer> messageIdCache;

    private int nextMsgId = MIN_MSG_ID - 1;

    @Override
    public int getNextMessageId() {
        Lock lock = messageIdCache.lock(this.lock);
        lock.lock();
        try {
            do {
                nextMsgId++;
                if (nextMsgId > MAX_MSG_ID) {
                    nextMsgId = MIN_MSG_ID;
                }
            } while (messageIdCache.containsKey(nextMsgId));
            messageIdCache.put(nextMsgId, nextMsgId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return nextMsgId;
    }

    @Override
    public void releaseMessageId(int messageId) {
        Lock lock = messageIdCache.lock(this.lock);
        lock.lock();
        try {
            messageIdCache.remove(messageId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
