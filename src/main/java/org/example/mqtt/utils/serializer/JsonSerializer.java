package org.example.mqtt.utils.serializer;

import com.alibaba.fastjson.JSON;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/**
 * @author 罗涛
 * @title JsonSerializer
 * @date 2020/7/16 11:06
 */
public class JsonSerializer implements Serializer<Object> {
    @Override
    public void configure(Map<String, ?> map, boolean b) {

    }

    @Override
    public byte[] serialize(String s, Object obj) {
        return JSON.toJSONBytes(obj);
    }

    @Override
    public void close() {

    }
}