/**
 * Copyright (c) 2018, Mr.Wang (recallcode@aliyun.com) All rights reserved.
 */

package org.example.mqtt.server.context.mqtt;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

import java.io.Serializable;

/**
 * 会话存储
 */
public class SessionStore implements Serializable {

	private String clientId;

	private Channel channel;

	private boolean cleanSession;

	private MqttPublishMessage willMessage;

	public SessionStore(String clientId, Channel channel, boolean cleanSession, MqttPublishMessage willMessage) {
		this.clientId = clientId;
		this.channel = channel;
		this.cleanSession = cleanSession;
		this.willMessage = willMessage;
	}

	public String getClientId() {
		return clientId;
	}

	public SessionStore setClientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	public Channel getChannel() {
		return channel;
	}

	public SessionStore setChannel(Channel channel) {
		this.channel = channel;
		return this;
	}

	public boolean isCleanSession() {
		return cleanSession;
	}

	public SessionStore setCleanSession(boolean cleanSession) {
		this.cleanSession = cleanSession;
		return this;
	}

	public MqttPublishMessage getWillMessage() {
		return willMessage;
	}

	public SessionStore setWillMessage(MqttPublishMessage willMessage) {
		this.willMessage = willMessage;
		return this;
	}
}
