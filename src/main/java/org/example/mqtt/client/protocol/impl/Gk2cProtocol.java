package org.example.mqtt.client.protocol.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.example.mqtt.client.protocol.IMqttProtocol;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 罗涛
 * @title Gk2cProtocol
 * @date 2020/10/14 19:55
 */
@Component
public class Gk2cProtocol implements IMqttProtocol {

    @Override
    public int sensorType() {
        return 22002;
    }

    @Override
    public String clientId() {
        return "BGK-G2C-MQTT";
    }

    @Override
    public String username() {
        return "admin";
    }

    @Override
    public String password() {
        return "public";
    }

    @Override
    public List<String> defaultTopics() {
        List<String> topics = Arrays.asList("$crsp/#", "$dp/#");
        return topics;
    }

    @Override
    public boolean messageValidate(byte[] payload) {
        return true;
    }

    @Override
    public Object parse(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        Map<String,Integer> map = new HashMap<>();
        map.put("head", (int) buffer.readByte());
        return map;
    }
}
