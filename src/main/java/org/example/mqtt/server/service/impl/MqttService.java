package org.example.mqtt.server.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.mqtt.server.config.MqttServerProperties;
import org.example.mqtt.server.context.ContextManager;
import org.example.mqtt.server.context.mqtt.*;
import org.example.mqtt.server.service.IMqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author 罗涛
 * @title MqttService
 * @date 2020/9/22 14:14
 */

@Slf4j
@Component
public class MqttService implements IMqttService {

    @Autowired
    MqttServerProperties mqttServerProperties;

    @Autowired
    ContextManager contextManager;

    //MQTT部分
    @Override
    public String parsePayload(byte[] bytes) {
        return new String(bytes);
    }

    @Override
    public boolean topicValidate(String topicFilter) {
//        if(!topicFilter.startsWith("$")){
//            return false;
//        }
        return true;
    }

    //session

    @Override
    public void putSession(String clientIdentifier, SessionStore session) {
        contextManager.putSessionStore(clientIdentifier, session);
    }

    @Override
    public boolean containsSession(String clientIdentifier) {
        return contextManager.containsSessionStore(clientIdentifier);
    }

    @Override
    public SessionStore getSession(String clientIdentifier) {
        return contextManager.getSessionStore(clientIdentifier);
    }

    @Override
    public void removeSession(String clientIdentifier){
        contextManager.clearSessionStore(clientIdentifier);
    }


    //dupPublish Message

    @Override
    public void putDupPublishMessage(String clientId, DupPublishMessageStore dupPublishMessageStore) {
        contextManager.putDupPublishMessage(clientId,dupPublishMessageStore);
    }

    @Override
    public List<DupPublishMessageStore> getDupPublishMessage(String clientIdentifier) {
        return contextManager.getDupPublishMessage(clientIdentifier);
    }

    @Override
    public void removeDupPublishMessageByClient(String clientIdentifier) {
        contextManager.removeDupPublishMessage(clientIdentifier);
    }

    @Override
    public void removeDupPublishMessage(String clientIdentifier, int messageId) {
        contextManager.removeDupPublishMessage(clientIdentifier, messageId);
    }




    //dupPubRel Message

    @Override
    public void putDupPubRelMessage(String clientId, DupPubRelMessageStore dupPubRelMessageStore) {
        contextManager.putDupPubRelMessage(clientId,dupPubRelMessageStore);
    }

    @Override
    public List<DupPubRelMessageStore> getDupPubRelMessage(String clientIdentifier) {
        return contextManager.getDupPubRelMessage(clientIdentifier);
    }


    @Override
    public void removeDupPubRelMessageByClient(String clientIdentifier) {
        contextManager.removeDupPubRelMessage(clientIdentifier);
    }

    @Override
    public void removeDupPubRelMessage(String clientId, int messageId) {
        contextManager.removeDupPubRelMessage(clientId, messageId);
    }



    //subscribe Message

    @Override
    public void putSubscribeMessage(String topicFilter, SubscribeStore subscribeStore) {
        contextManager.putSubscribeMessage(topicFilter, subscribeStore);
    }

    @Override
    public void removeSubscribeMessage(String topicFilter, String clientId) {
        contextManager.removeSubscribeMessage(topicFilter, clientId);
    }

    @Override
    public void removeSubscribeByClient(String clientIdentifier) {
        contextManager.removeSubscribeMessage(clientIdentifier);
    }

    @Override
    public List<SubscribeStore> searchSubscribe(String topic) {
        return contextManager.searchSubscribeMessage(topic);
    }



    //retain Message

    @Override
    public void putRetainMessage(String topicName, RetainMessageStore retainMessageStore) {
        contextManager.putRetainMessage(topicName, retainMessageStore);
    }

    @Override
    public List<RetainMessageStore> searchRetainMessage(String topicFilter) {
        return contextManager.searchRetainMessage(topicFilter);
    }

    @Override
    public void removeRetainMessage(String topicName) {
        contextManager.removeRetainMessage(topicName);
    }
}
