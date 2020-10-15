/**
 * Copyright (c) 2018, Mr.Wang (recallcode@aliyun.com) All rights reserved.
 */

package org.example.mqtt.server.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.mqtt.server.context.ContextManager;
import org.example.mqtt.server.context.mqtt.*;
import org.example.mqtt.server.ignite.ClusterCommunication;
import org.example.mqtt.server.ignite.InternalMessage;
import org.example.mqtt.server.service.IAuthService;
import org.example.mqtt.server.service.IMqttService;
import org.example.mqtt.server.service.IMsgIdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 协议处理
 */
@Slf4j
@Component
public class MqttProcessor {
	@Autowired
	ContextManager contextManager;

	@Autowired
	IMqttService mqttService;

	@Autowired
	IAuthService authService;

	@Autowired
	IMsgIdService msgIdService;

	@Autowired
	ClusterCommunication clusterCommunication;

	public void processConnect(Channel channel, MqttConnectMessage msg) {
		// 消息解码器出现异常
		if (msg.decoderResult().isFailure()) {
			Throwable cause = msg.decoderResult().cause();
			if (cause instanceof MqttUnacceptableProtocolVersionException) {
				// 不支持的协议版本
				MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
						new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false), null);
				channel.writeAndFlush(connAckMessage);
				channel.close();
				return;
			} else if (cause instanceof MqttIdentifierRejectedException) {
				// 不合格的clientId
				MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
						new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
				channel.writeAndFlush(connAckMessage);
				channel.close();
				return;
			}
			channel.close();
			return;
		}
		// clientId为空或null的情况, 这里要求客户端必须提供clientId, 不管cleanSession是否为1, 此处没有参考标准协议实现
		if (StringUtils.isBlank(msg.payload().clientIdentifier())) {
			MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
					new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
					new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
			channel.writeAndFlush(connAckMessage);
			channel.close();
			return;
		}
		// 用户名和密码验证, 这里要求客户端连接时必须提供用户名和密码, 不管是否设置用户名标志和密码标志为1, 此处没有参考标准协议实现
		String username = msg.payload().userName();
		String password = msg.payload().passwordInBytes() == null ? null : new String(msg.payload().passwordInBytes(), CharsetUtil.UTF_8);
		if (!authService.checkValid(username, password)) {
			MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
					new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
					new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, false), null);
			channel.writeAndFlush(connAckMessage);
			channel.close();
			return;
		}
		// 如果会话中已存储这个新连接的clientId, 就关闭之前该clientId的连接
		String clientIdentifier = msg.payload().clientIdentifier();
		if (mqttService.containsSession(clientIdentifier)) {
			SessionStore session = mqttService.getSession(clientIdentifier);
			Channel previous = session.getChannel();
			Boolean cleanSession = session.isCleanSession();
			if (cleanSession) {
				mqttService.removeSession(clientIdentifier);
				mqttService.removeSubscribeByClient(clientIdentifier);
				mqttService.removeDupPublishMessageByClient(clientIdentifier);
				mqttService.removeDupPubRelMessageByClient(clientIdentifier);
			}
			previous.close();
		}
		// 处理遗嘱信息
		boolean isCleanSession = msg.variableHeader().isCleanSession();
		SessionStore sessionStore = new SessionStore(msg.payload().clientIdentifier(), channel, isCleanSession, null);
		if (msg.variableHeader().isWillFlag()) {
			MqttPublishMessage willMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
					new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(msg.variableHeader().willQos()), msg.variableHeader().isWillRetain(), 0),
					new MqttPublishVariableHeader(msg.payload().willTopic(), 0), Unpooled.buffer().writeBytes(msg.payload().willMessageInBytes()));
			sessionStore.setWillMessage(willMessage);
		}
		// 处理连接心跳包
		if (msg.variableHeader().keepAliveTimeSeconds() > 0) {
			if (channel.pipeline().names().contains("idle")) {
				channel.pipeline().remove("idle");
			}
			channel.pipeline().addFirst("idle", new IdleStateHandler(0, 0, Math.round(msg.variableHeader().keepAliveTimeSeconds() * 1.5f)));
		}
		// 至此存储会话信息及返回接受客户端连接
		mqttService.putSession(clientIdentifier, sessionStore);
		// 将clientId存储到channel的map中
		contextManager.putClientId(channel,clientIdentifier);
		Boolean sessionPresent = mqttService.containsSession(clientIdentifier) && !isCleanSession;
		MqttConnAckMessage okResp = (MqttConnAckMessage) MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
				new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent), null);
		channel.writeAndFlush(okResp);
		log.debug("CONNECT - clientId: {}, cleanSession: {}", clientIdentifier, isCleanSession);
		// 如果cleanSession为0, 需要重发同一clientId存储的未完成的QoS1和QoS2的DUP消息
		if (!isCleanSession) {
			List<DupPublishMessageStore> dupPublishMessageStoreList = mqttService.getDupPublishMessage(clientIdentifier);
			List<DupPubRelMessageStore> dupPubRelMessageStoreList = mqttService.getDupPubRelMessage(clientIdentifier);
			if (!CollectionUtils.isEmpty(dupPublishMessageStoreList)) {
				dupPublishMessageStoreList.forEach(dupPublishMessageStore -> {
					MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
							new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.valueOf(dupPublishMessageStore.getMqttQoS()), false, 0),
							new MqttPublishVariableHeader(dupPublishMessageStore.getTopic(), dupPublishMessageStore.getMessageId()), Unpooled.buffer().writeBytes(dupPublishMessageStore.getMessageBytes()));
					channel.writeAndFlush(publishMessage);
				});
			}
			if (!CollectionUtils.isEmpty(dupPubRelMessageStoreList)) {
				dupPubRelMessageStoreList.forEach(dupPubRelMessageStore -> {
					MqttMessage pubRelMessage = MqttMessageFactory.newMessage(
							new MqttFixedHeader(MqttMessageType.PUBREL, true, MqttQoS.AT_MOST_ONCE, false, 0),
							MqttMessageIdVariableHeader.from(dupPubRelMessageStore.getMessageId()), null);
					channel.writeAndFlush(pubRelMessage);
				});
			}
		}
	}

	public void processDisConnect(Channel channel, MqttMessage msg) {
		String clientId = contextManager.getClientId(channel);
		SessionStore sessionStore = mqttService.getSession(clientId);
		if (sessionStore.isCleanSession()) {
			mqttService.removeSubscribeByClient(clientId);
			mqttService.removeDupPublishMessageByClient(clientId);
			mqttService.removeDupPubRelMessageByClient(clientId);
		}
		log.debug("DISCONNECT - clientId: {}, cleanSession: {}", clientId, sessionStore.isCleanSession());
		mqttService.removeSession(clientId);
		channel.close();
	}

	public void processPingReq(Channel channel, MqttMessage msg) {
		MqttMessage pingRespMessage = MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0), null, null);
		String clientId = contextManager.getClientId(channel);
		log.debug("PINGREQ - clientId: {}", clientId);
		channel.writeAndFlush(pingRespMessage);
	}

	public void processPubAck(Channel channel, MqttMessageIdVariableHeader variableHeader) {
		int messageId = variableHeader.messageId();
		String clientId = contextManager.getClientId(channel);
		log.debug("PUBACK - clientId: {}, messageId: {}", clientId, messageId);
		mqttService.removeDupPublishMessage(clientId, messageId);
		msgIdService.releaseMessageId(messageId);
	}

	public void processPubComp(Channel channel, MqttMessageIdVariableHeader variableHeader) {
		int messageId = variableHeader.messageId();
		String clientId = contextManager.getClientId(channel);
		log.debug("PUBCOMP - clientId: {}, messageId: {}", clientId, messageId);
		mqttService.removeDupPubRelMessage(clientId, messageId);
		msgIdService.releaseMessageId(messageId);
	}

	public void processPublish(Channel channel, MqttPublishMessage msg) {
		// QoS=0
		ByteBuf payload = msg.payload();
		MqttQoS mqttQoS = msg.fixedHeader().qosLevel();
		String topicName = msg.variableHeader().topicName();
		if (mqttQoS == MqttQoS.AT_MOST_ONCE) {
			byte[] messageBytes = new byte[payload.readableBytes()];
			payload.getBytes(payload.readerIndex(), messageBytes);
			InternalMessage internalMessage = new InternalMessage().setTopic(topicName)
					.setMqttQoS(mqttQoS.value()).setMessageBytes(messageBytes)
					.setDup(false).setRetain(false);
			clusterCommunication.internalSend(internalMessage);
			this.sendPublishMessage(topicName, mqttQoS, messageBytes, false, false);
		}
		// QoS=1
		if (mqttQoS == MqttQoS.AT_LEAST_ONCE) {
			byte[] messageBytes = new byte[payload.readableBytes()];
			payload.getBytes(payload.readerIndex(), messageBytes);
			InternalMessage internalMessage = new InternalMessage().setTopic(topicName)
					.setMqttQoS(mqttQoS.value()).setMessageBytes(messageBytes)
					.setDup(false).setRetain(false);
			clusterCommunication.internalSend(internalMessage);
			this.sendPublishMessage(topicName, mqttQoS, messageBytes, false, false);
			this.sendPubAckMessage(channel, msg.variableHeader().packetId());
		}
		// QoS=2
		if (mqttQoS == MqttQoS.EXACTLY_ONCE) {
			byte[] messageBytes = new byte[payload.readableBytes()];
			payload.getBytes(payload.readerIndex(), messageBytes);
			InternalMessage internalMessage = new InternalMessage().setTopic(topicName)
					.setMqttQoS(mqttQoS.value()).setMessageBytes(messageBytes)
					.setDup(false).setRetain(false);
			clusterCommunication.internalSend(internalMessage);
			this.sendPublishMessage(topicName, mqttQoS, messageBytes, false, false);
			this.sendPubRecMessage(channel, msg.variableHeader().packetId());
		}
		// retain=1, 保留消息
		if (msg.fixedHeader().isRetain()) {
			byte[] messageBytes = new byte[payload.readableBytes()];
			payload.getBytes(payload.readerIndex(), messageBytes);
			if (messageBytes.length == 0) {
				mqttService.removeRetainMessage(topicName);
			} else {
				RetainMessageStore retainMessageStore = new RetainMessageStore().setTopic(topicName).setMqttQoS(mqttQoS.value())
						.setMessageBytes(messageBytes);
				mqttService.putRetainMessage(msg.variableHeader().topicName(), retainMessageStore);
			}
		}
	}


	public void processPubRec(Channel channel, MqttMessageIdVariableHeader variableHeader) {
		int messageId = variableHeader.messageId();
		MqttMessage pubRelMessage = MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0),
				MqttMessageIdVariableHeader.from(messageId), null);
		String clientId = contextManager.getClientId(channel);
		log.debug("PUBREC - clientId: {}, messageId: {}", clientId, messageId);
		mqttService.removeDupPublishMessage(clientId, variableHeader.messageId());
		DupPubRelMessageStore dupPubRelMessageStore = new DupPubRelMessageStore().setClientId(clientId)
				.setMessageId(messageId);
		mqttService.putDupPubRelMessage(clientId, dupPubRelMessageStore);
		channel.writeAndFlush(pubRelMessage);
	}

	public void processPubRel(Channel channel, MqttMessageIdVariableHeader variableHeader) {
		int messageId = variableHeader.messageId();
		MqttMessage pubCompMessage = MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0),
				MqttMessageIdVariableHeader.from(messageId), null);
		String clientId = contextManager.getClientId(channel);
		log.debug("PUBREL - clientId: {}, messageId: {}", clientId, messageId);
		channel.writeAndFlush(pubCompMessage);
	}

	public void processSubscribe(Channel channel, MqttSubscribeMessage msg) {
		List<MqttTopicSubscription> topicSubscriptions = msg.payload().topicSubscriptions();
		String clientId = contextManager.getClientId(channel);
		if(CollectionUtils.isEmpty(topicSubscriptions)){
			log.info("订阅的topic为空，clientId = {}", clientId);
			return;
		}
		if (this.validTopicFilter(topicSubscriptions)) {
			List<Integer> mqttQoSList = new ArrayList<Integer>();
			topicSubscriptions.forEach(topicSubscription -> {
				String topicFilter = topicSubscription.topicName();
				MqttQoS mqttQoS = topicSubscription.qualityOfService();
				SubscribeStore subscribeStore = new SubscribeStore(clientId, topicFilter, mqttQoS.value());
				mqttService.putSubscribeMessage(topicFilter, subscribeStore);
				mqttQoSList.add(mqttQoS.value());
				log.debug("SUBSCRIBE - clientId: {}, topFilter: {}, QoS: {}", clientId, topicFilter, mqttQoS.value());
			});
			MqttSubAckMessage subAckMessage = (MqttSubAckMessage) MqttMessageFactory.newMessage(
					new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
					MqttMessageIdVariableHeader.from(msg.variableHeader().messageId()),
					new MqttSubAckPayload(mqttQoSList));
			channel.writeAndFlush(subAckMessage);
			// 发布保留消息
			topicSubscriptions.forEach(topicSubscription -> {
				String topicFilter = topicSubscription.topicName();
				MqttQoS mqttQoS = topicSubscription.qualityOfService();
				this.sendRetainMessage(channel, topicFilter, mqttQoS);
			});
		} else {
			log.warn("topic格式不合法！ topic = {}", topicSubscriptions);
			channel.close();
		}
	}

	public void processUnSubscribe(Channel channel, MqttUnsubscribeMessage msg) {
		List<String> topicFilters = msg.payload().topics();
		String clientId = contextManager.getClientId(channel);
		if(CollectionUtils.isEmpty(topicFilters)){
			log.warn("取消订阅的topic list为空！ clientId = {}", clientId);
			return;
		}
		topicFilters.forEach(topicFilter -> {
			mqttService.removeSubscribeMessage(topicFilter, clientId);
			log.debug("UNSUBSCRIBE - clientId: {}, topicFilter: {}", clientId, topicFilter);
		});
		MqttUnsubAckMessage unsubAckMessage = (MqttUnsubAckMessage) MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
				MqttMessageIdVariableHeader.from(msg.variableHeader().messageId()), null);
		channel.writeAndFlush(unsubAckMessage);
	}


	private void sendPublishMessage(String topic, MqttQoS mqttQoS, byte[] messageBytes, boolean retain, boolean dup) {
		List<SubscribeStore> subscribeStores = mqttService.searchSubscribe(topic);
		if(CollectionUtils.isEmpty(subscribeStores)){
			log.info("topic: {} 的订阅者为空", topic);
			return;
		}
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
					log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", clientId, topic, respQoS.value(), messageId);
					DupPublishMessageStore dupPublishMessageStore = new DupPublishMessageStore().setClientId(clientId)
							.setTopic(topic).setMqttQoS(respQoS.value()).setMessageBytes(messageBytes);
					mqttService.putDupPublishMessage(clientId, dupPublishMessageStore);
					mqttService.getSession(clientId).getChannel().writeAndFlush(publishMessage);
				}
				if (respQoS == MqttQoS.EXACTLY_ONCE) {
					int messageId = msgIdService.getNextMessageId();
					MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
							new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
							new MqttPublishVariableHeader(topic, messageId), Unpooled.buffer().writeBytes(messageBytes));
					log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", clientId, topic, respQoS.value(), messageId);
					DupPublishMessageStore dupPublishMessageStore = new DupPublishMessageStore().setClientId(clientId)
							.setTopic(topic).setMqttQoS(respQoS.value()).setMessageBytes(messageBytes);
					mqttService.putDupPublishMessage(clientId, dupPublishMessageStore);
					mqttService.getSession(clientId).getChannel().writeAndFlush(publishMessage);
				}
			}
		});
	}

	private void sendPubAckMessage(Channel channel, int messageId) {
		MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
				MqttMessageIdVariableHeader.from(messageId), null);
		channel.writeAndFlush(pubAckMessage);
	}

	private void sendPubRecMessage(Channel channel, int messageId) {
		MqttMessage pubRecMessage = MqttMessageFactory.newMessage(
				new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
				MqttMessageIdVariableHeader.from(messageId), null);
		channel.writeAndFlush(pubRecMessage);
	}


	private void sendRetainMessage(Channel channel, String topicFilter, MqttQoS mqttQoS) {
		List<RetainMessageStore> retainMessageStores = mqttService.searchRetainMessage(topicFilter);
		if(CollectionUtils.isEmpty(retainMessageStores)){
			return;
		}
		retainMessageStores.forEach(retainMessageStore -> {
			MqttQoS respQoS = retainMessageStore.getMqttQoS() > mqttQoS.value() ? mqttQoS : MqttQoS.valueOf(retainMessageStore.getMqttQoS());
			String clientId = contextManager.getClientId(channel);
			if (respQoS == MqttQoS.AT_MOST_ONCE) {
				MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
						new MqttPublishVariableHeader(retainMessageStore.getTopic(), 0), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
				log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}", clientId, retainMessageStore.getTopic(), respQoS.value());
				channel.writeAndFlush(publishMessage);
			}
			if (respQoS == MqttQoS.AT_LEAST_ONCE) {
				int messageId = msgIdService.getNextMessageId();
				MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
						new MqttPublishVariableHeader(retainMessageStore.getTopic(), messageId), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
				log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", clientId, retainMessageStore.getTopic(), respQoS.value(), messageId);
				channel.writeAndFlush(publishMessage);
			}
			if (respQoS == MqttQoS.EXACTLY_ONCE) {
				int messageId = msgIdService.getNextMessageId();
				MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
						new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
						new MqttPublishVariableHeader(retainMessageStore.getTopic(), messageId), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
				log.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", clientId , retainMessageStore.getTopic(), respQoS.value(), messageId);
				channel.writeAndFlush(publishMessage);
			}
		});
	}


	private boolean validTopicFilter(List<MqttTopicSubscription> topicSubscriptions) {
		for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
			String topicFilter = topicSubscription.topicName();
			boolean b = mqttService.topicValidate(topicFilter);
			if(b){
				continue;
			}
			return b;
		}
		return true;
	}
}
