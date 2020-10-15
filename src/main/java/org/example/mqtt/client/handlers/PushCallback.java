package org.example.mqtt.client.handlers;

import com.alibaba.fastjson.JSON;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.example.mqtt.client.MqttPushBootstrap;
import org.example.mqtt.client.protocol.IMqttProtocol;

import java.io.EOFException;

/**
 * @author 罗涛
 * @title PushCallback
 * @date 2020/10/10 17:39
 */

@Slf4j
@NoArgsConstructor
public class PushCallback implements MqttCallback, IPublish{

    MqttClient client;
    IMqttProtocol mqttProtocol;
    MqttPushBootstrap bootstrap;

    public PushCallback(MqttPushBootstrap bootstrap, MqttClient client, IMqttProtocol mqttProtocol) {
        this.bootstrap = bootstrap;
        this.client = client;
        this.mqttProtocol = mqttProtocol;
    }

    @Override
    public void connectionLost(Throwable throwable) {
        if(throwable.getCause() instanceof EOFException) return;
        log.warn("处理发生异常，导致连接断开，自动重连！" + throwable.getMessage(), throwable);
        try {
            if (client.isConnected()) {
                client.disconnect();
                client.close();
            }
            client = bootstrap.connect(mqttProtocol);
            bootstrap.subscribeDefaultTopics(client, mqttProtocol);
        } catch (Exception e){
            log.warn("断开重连发生异常：" + e.getMessage(), e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
//        try {
            byte[] payload = message.getPayload();
            // subscribe后得到的消息会执行到这里面
            log.info("接收消息主题 : " + topic);
            log.info("接收消息Qos : " + message.getQos());
            log.info("接收消息内容 : " + new String(payload));
            boolean b = mqttProtocol.messageValidate(payload);
            if(b){
                Object parse = mqttProtocol.parse(payload);
                //send 2 MQ
                log.info("send 2 MQ : obj = {}", JSON.toJSONString(parse));
            }
//        } catch (Exception e) {
//            log.error("处理订阅消息时发生异常：" + e.getMessage(), e);
//        }

    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        log.info("deliveryComplete---------" + iMqttDeliveryToken.isComplete());
    }


    /**
     * 发布
     *
     * @param qos         连接方式
     * @param retained    是否保留
     * @param topic       主题
     * @param pushMessage 消息体
     */
    @Override
    public void publish(int qos, boolean retained, String topic, String pushMessage) {
        MqttMessage message = new MqttMessage();
        message.setQos(qos);
        message.setRetained(retained);
        message.setPayload(pushMessage.getBytes());
        MqttTopic mTopic = client.getTopic(topic);
        if (null == mTopic) {
            log.error("topic not exist");
        }
        MqttDeliveryToken token;
        try {
            token = mTopic.publish(message);
            token.waitForCompletion();
        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}

