/**
 * Copyright (c) 2018, Mr.Wang (recallcode@aliyun.com) All rights reserved.
 */

package org.example.mqtt.server.ignite;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.IgniteMessaging;
import org.example.mqtt.server.config.IgniteProperties;
import org.example.mqtt.server.context.mqtt.SubscribeStore;
import org.example.mqtt.server.service.IMqttService;
import org.example.mqtt.server.service.IMsgIdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 内部通信, 基于发布-订阅范式
 */

@Slf4j
@Component
public class ClusterCommunication {

	@Autowired
	IgniteMessaging igniteMessaging;

	@Autowired
	IMqttService mqttService;

	@Autowired
	IMsgIdService msgIdService;

	@Autowired
	IgniteProperties igniteProperties;

	@PostConstruct
	private void internalListen() {
		igniteMessaging.localListen(igniteProperties.getClusterInternalTopic(), (nodeId, msg) -> {
			InternalMessage internalMessage = (InternalMessage) msg;
			this.sendPublishMessage(internalMessage.getTopic(), MqttQoS.valueOf(internalMessage.getMqttQoS()), internalMessage.getMessageBytes(), internalMessage.isRetain(), internalMessage.isDup());
			return true;
		});
	}

	public void internalSend(InternalMessage internalMessage) {
		if (igniteMessaging.clusterGroup().nodes() != null && igniteMessaging.clusterGroup().nodes().size() > 0) {
			igniteMessaging.send(igniteProperties.getClusterInternalTopic(), internalMessage);
		}
	}

	private void sendPublishMessage(String topic, MqttQoS mqttQoS, byte[] messageBytes, boolean retain, boolean dup) {
		List<SubscribeStore> subscribeStores = mqttService.searchSubscribe(topic);
		subscribeStores.forEach(subscribeStore -> {
			String clientId = subscribeStore.getClientId();
			if (mqttService.containsSession(clientId)) {
				// 订阅者收到MQTT消息的QoS级别, 最终取决于发布消息的QoS和主题订阅的QoS
				MqttQoS respQoS = mqttQoS.value() > subscribeStore.getMqttQoS() ? MqttQoS.valueOf(subscribeStore.getMqttQoS()) : mqttQoS;
				if (respQoS == MqttQoS.AT_MOST_ONCE) {
					MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
						new MqttPublishVariableHeader(topic, 0), Unpooled.buffer().writeBytes(messageBytes));
					log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}", clientId, topic, respQoS.value());
					mqttService.getSession(clientId).getChannel().writeAndFlush(publishMessage);
				}
				if (respQoS == MqttQoS.AT_LEAST_ONCE) {
					int messageId = msgIdService.getNextMessageId();
					MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
						new MqttPublishVariableHeader(topic, messageId), Unpooled.buffer().writeBytes(messageBytes));
					log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", subscribeStore.getClientId(), topic, respQoS.value(), messageId);
					mqttService.getSession(clientId).getChannel().writeAndFlush(publishMessage);
				}
				if (respQoS == MqttQoS.EXACTLY_ONCE) {
					int messageId = msgIdService.getNextMessageId();
					MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
						new MqttPublishVariableHeader(topic, messageId), Unpooled.buffer().writeBytes(messageBytes));
					log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", clientId, topic, respQoS.value(), messageId);
					mqttService.getSession(clientId).getChannel().writeAndFlush(publishMessage);
				}
			}
		});
	}

}
