package org.example.mqtt.server.context;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.IgniteCache;
import org.example.mqtt.server.context.mqtt.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 罗涛
 * @title ContextManager
 * @date 2020/6/22 15:11
 */
@Slf4j
@Component
public class ContextManager {
    public static AttributeKey<String> MQTT_CLIENT_ID = AttributeKey.newInstance("mqtt_client_id");
    public static Map<String, SessionStore> sessionStoreMap = new ConcurrentHashMap<>();

//    public  Map<String, ConcurrentHashMap<Integer, DupPublishMessageStore>> dupPublishMessageStoreMap = new ConcurrentHashMap<>();
//    public  Map<String, ConcurrentHashMap<Integer, DupPubRelMessageStore>> dupPubRelMessageStoreMap = new ConcurrentHashMap<>();
//    public  Map<String, RetainMessageStore> retainMessageStoreMap = new ConcurrentHashMap<>();
//    public  Map<String, ConcurrentHashMap<String, SubscribeStore>> subscribeNotWildcardCache = new ConcurrentHashMap<>();
//    public  Map<String, ConcurrentHashMap<String, SubscribeStore>> subscribeWildcardCache = new ConcurrentHashMap<>();

    @Resource
    private IgniteCache<Integer, Integer> messageIdCache;

    @Resource
    private IgniteCache<String, RetainMessageStore> retainMessageCache;

    @Resource
    private IgniteCache<String, ConcurrentHashMap<Integer, DupPublishMessageStore>> dupPublishMessageCache;

    @Resource
    private IgniteCache<String, ConcurrentHashMap<Integer, DupPubRelMessageStore>> dupPubRelMessageCache;

    @Resource
    private IgniteCache<String, ConcurrentHashMap<String, SubscribeStore>> subscribeNotWildcardCache;

    @Resource
    private IgniteCache<String, ConcurrentHashMap<String, SubscribeStore>> subscribeWildcardCache;



    public  void putClientId(Channel channel, String clientId){
        channel.attr(MQTT_CLIENT_ID).set(clientId);
    }

    public  String getClientId(Channel channel){
        Attribute<String> attr = channel.attr(MQTT_CLIENT_ID);
        if(Objects.isNull(attr)){
            return null;
        }
        String clientId = channel.attr(MQTT_CLIENT_ID).get();
        return clientId;
    }

    public  void removeClientId(Channel channel){
        channel.attr(MQTT_CLIENT_ID).set(null);
    }

    public  boolean containsSessionStore(String clientIdentifier) {
        return sessionStoreMap.containsKey(clientIdentifier);
    }

    public  SessionStore getSessionStore(String clientIdentifier) {
        return sessionStoreMap.get(clientIdentifier);
    }

    public  void clearSessionStore(String clientIdentifier) {
        sessionStoreMap.remove(clientIdentifier);
    }

    public  void putSessionStore(String clientIdentifier, SessionStore session) {
        sessionStoreMap.put(clientIdentifier, session);
    }

    public  void putDupPublishMessage(String clientIdentifier, DupPublishMessageStore dupPublishMessageStore) {
        ConcurrentHashMap<Integer, DupPublishMessageStore> map = dupPublishMessageCache.containsKey(clientIdentifier) ? dupPublishMessageCache.get(clientIdentifier) : new ConcurrentHashMap<Integer, DupPublishMessageStore>();
        map.put(dupPublishMessageStore.getMessageId(), dupPublishMessageStore);
        dupPublishMessageCache.put(clientIdentifier, map);
    }

    public  List<DupPublishMessageStore> getDupPublishMessage(String clientIdentifier) {
        if (dupPublishMessageCache.containsKey(clientIdentifier)) {
            ConcurrentHashMap<Integer, DupPublishMessageStore> map = dupPublishMessageCache.get(clientIdentifier);
            Collection<DupPublishMessageStore> collection = map.values();
            return new ArrayList<DupPublishMessageStore>(collection);
        }
        return Collections.emptyList();
    }

    public  void removeDupPublishMessage(String clientIdentifier) {
        if (dupPublishMessageCache.containsKey(clientIdentifier)) {
            ConcurrentHashMap<Integer, DupPublishMessageStore> map = dupPublishMessageCache.get(clientIdentifier);
//            map.forEach((messageId, dupPublishMessageStore) -> {
//                messageIdService.releaseMessageId(messageId);  //todo
//            });
            map.clear();
            dupPublishMessageCache.remove(clientIdentifier);
        }
    }

    public  void removeDupPublishMessage(String clientIdentifier,int messageId) {
        if (dupPublishMessageCache.containsKey(clientIdentifier)) {
            ConcurrentHashMap<Integer, DupPublishMessageStore> map = dupPublishMessageCache.get(clientIdentifier);
            if (map.containsKey(messageId)) {
                map.remove(messageId);
                if (map.size() > 0) {
                    dupPublishMessageCache.put(clientIdentifier, map);
                } else {
                    dupPublishMessageCache.remove(clientIdentifier);
                }
            }
        }

    }

    public  void putDupPubRelMessage(String clientIdentifier, DupPubRelMessageStore dupPubRelMessageStore) {
        ConcurrentHashMap<Integer, DupPubRelMessageStore> map = dupPubRelMessageCache.containsKey(clientIdentifier) ? dupPubRelMessageCache.get(clientIdentifier) : new ConcurrentHashMap<Integer, DupPubRelMessageStore>();
        map.put(dupPubRelMessageStore.getMessageId(), dupPubRelMessageStore);
        dupPubRelMessageCache.put(clientIdentifier, map);
    }


    public  List<DupPubRelMessageStore> getDupPubRelMessage(String clientIdentifier) {
        if (dupPubRelMessageCache.containsKey(clientIdentifier)) {
            ConcurrentHashMap<Integer, DupPubRelMessageStore> map = dupPubRelMessageCache.get(clientIdentifier);
            Collection<DupPubRelMessageStore> collection = map.values();
            return new ArrayList<DupPubRelMessageStore>(collection);
        }
        return Collections.emptyList();
    }

    public  void removeDupPubRelMessage(String clientId, int messageId) {
        if (dupPubRelMessageCache.containsKey(clientId)) {
            ConcurrentHashMap<Integer, DupPubRelMessageStore> map = dupPubRelMessageCache.get(clientId);
            if (map.containsKey(messageId)) {
                map.remove(messageId);
                if (map.size() > 0) {
                    dupPubRelMessageCache.put(clientId, map);
                } else {
                    dupPubRelMessageCache.remove(clientId);
                }
            }
        }
    }

    public  void removeDupPubRelMessage(String clientIdentifier) {
        if (dupPubRelMessageCache.containsKey(clientIdentifier)) {
            ConcurrentHashMap<Integer, DupPubRelMessageStore> map = dupPubRelMessageCache.get(clientIdentifier);
//            map.forEach((messageId, dupPubRelMessageStore) -> {
//                messageIdService.releaseMessageId(messageId);
//            });
            map.clear();
            dupPubRelMessageCache.remove(clientIdentifier);
        }
    }


    public  void putSubscribeMessage(String topicFilter, SubscribeStore subscribeStore) {
        //含通配符的topic
        if (StringUtils.contains(topicFilter, '#') || StringUtils.contains(topicFilter, '+')) {
            ConcurrentHashMap<String, SubscribeStore> map =
                    subscribeWildcardCache.containsKey(topicFilter) ? subscribeWildcardCache.get(topicFilter) : new ConcurrentHashMap<String, SubscribeStore>();
            map.put(subscribeStore.getClientId(), subscribeStore);
            subscribeWildcardCache.put(topicFilter, map);
        } else {
            ConcurrentHashMap<String, SubscribeStore> map =
                    subscribeNotWildcardCache.containsKey(topicFilter) ? subscribeNotWildcardCache.get(topicFilter) : new ConcurrentHashMap<String, SubscribeStore>();
            map.put(subscribeStore.getClientId(), subscribeStore);
            subscribeNotWildcardCache.put(topicFilter, map);
        }
    }

    public  void removeSubscribeMessage(String topicFilter, String clientId) {
        if (StringUtils.contains(topicFilter, '#') || StringUtils.contains(topicFilter, '+')) {
            if (subscribeWildcardCache.containsKey(topicFilter)) {
                ConcurrentHashMap<String, SubscribeStore> map = subscribeWildcardCache.get(topicFilter);
                if (map.containsKey(clientId)) {
                    map.remove(clientId);
                    if (map.size() > 0) {
                        subscribeWildcardCache.put(topicFilter, map);
                    } else {
                        subscribeWildcardCache.remove(topicFilter);
                    }
                }
            }
        } else {
            if (subscribeNotWildcardCache.containsKey(topicFilter)) {
                ConcurrentHashMap<String, SubscribeStore> map = subscribeNotWildcardCache.get(topicFilter);
                if (map.containsKey(clientId)) {
                    map.remove(clientId);
                    if (map.size() > 0) {
                        subscribeNotWildcardCache.put(topicFilter, map);
                    } else {
                        subscribeNotWildcardCache.remove(topicFilter);
                    }
                }
            }
        }
    }

    public  void removeSubscribeMessage(String clientId) {
        subscribeNotWildcardCache.forEach(entry -> {
            ConcurrentHashMap<String, SubscribeStore> map = entry.getValue();
            if (map.containsKey(clientId)) {
                map.remove(clientId);
                if (map.size() > 0) {
                    subscribeNotWildcardCache.put(entry.getKey(), map);
                } else {
                    subscribeNotWildcardCache.remove(entry.getKey());
                }
            }
        });
        subscribeWildcardCache.forEach(entry -> {
            ConcurrentHashMap<String, SubscribeStore> map = entry.getValue();
            if (map.containsKey(clientId)) {
                map.remove(clientId);
                if (map.size() > 0) {
                    subscribeWildcardCache.put(entry.getKey(), map);
                } else {
                    subscribeWildcardCache.remove(entry.getKey());
                }
            }
        });
    }


    public  List<SubscribeStore> searchSubscribeMessage(String topic) {
        List<SubscribeStore> subscribeStores = new ArrayList<SubscribeStore>();
        if (subscribeNotWildcardCache.containsKey(topic)) {
            ConcurrentHashMap<String, SubscribeStore> map = subscribeNotWildcardCache.get(topic);
            Collection<SubscribeStore> collection = map.values();
            List<SubscribeStore> list = new ArrayList<SubscribeStore>(collection);
            subscribeStores.addAll(list);
        }
        //含通配符的topic
        subscribeWildcardCache.forEach(sData -> {
            String key = sData.getKey();
            ConcurrentHashMap<String, SubscribeStore> entry = sData.getValue();
            if (StringUtils.split(topic, '/').length >= StringUtils.split(key, '/').length) {
                List<String> splitTopics = Arrays.asList(StringUtils.split(topic, '/'));
                List<String> splitTopicFilters = Arrays.asList(StringUtils.split(key, '/'));
                String newTopicFilter = "";
                for (int i = 0; i < splitTopicFilters.size(); i++) {
                    String value = splitTopicFilters.get(i);
                    if (value.equals("+")) {
                        newTopicFilter = newTopicFilter + "+/";
                    } else if (value.equals("#")) {
                        newTopicFilter = newTopicFilter + "#/";
                        break;
                    } else {
                        newTopicFilter = newTopicFilter + splitTopics.get(i) + "/";
                    }
                }
                newTopicFilter = StringUtils.removeEnd(newTopicFilter, "/");
                if (key.equals(newTopicFilter)) {
                    Collection<SubscribeStore> collection = entry.values();
                    List<SubscribeStore> list = new ArrayList<SubscribeStore>(collection);
                    subscribeStores.addAll(list);
                }
            }
        });
        return subscribeStores;
    }

    public  void putRetainMessage(String topic, RetainMessageStore retainMessageStore) {
        retainMessageCache.put(topic, retainMessageStore);
    }

    public  RetainMessageStore getRetainMessage(String topic) {
        return retainMessageCache.get(topic);
    }

    public  void removeRetainMessage(String topic) {
        retainMessageCache.remove(topic);
    }

    public  boolean containsRetainMessageKey(String topic) {
        return retainMessageCache.containsKey(topic);
    }

    public  List<RetainMessageStore> searchRetainMessage(String topicFilter) {
        List<RetainMessageStore> retainMessageStores = new ArrayList<RetainMessageStore>();
        if (!StringUtils.contains(topicFilter, '#') && !StringUtils.contains(topicFilter, '+')) {
            if (retainMessageCache.containsKey(topicFilter)) {
                retainMessageStores.add(retainMessageCache.get(topicFilter));
            }
        }else {
            retainMessageCache.forEach(sData -> {
                String topic = sData.getKey();
                RetainMessageStore retainMessageStore = sData.getValue();
                if (StringUtils.split(topic, '/').length >= StringUtils.split(topicFilter, '/').length) {
                    List<String> splitTopics = Arrays.asList(StringUtils.split(topic, '/'));
                    List<String> splitTopicFilters = Arrays.asList(StringUtils.split(topicFilter, '/'));
                    String newTopicFilter = "";
                    for (int i = 0; i < splitTopicFilters.size(); i++) {
                        String value = splitTopicFilters.get(i);
                        if (value.equals("+")) {
                            newTopicFilter = newTopicFilter + "+/";
                        } else if (value.equals("#")) {
                            newTopicFilter = newTopicFilter + "#/";
                            break;
                        } else {
                            newTopicFilter = newTopicFilter + splitTopics.get(i) + "/";
                        }
                    }
                    newTopicFilter = StringUtils.removeEnd(newTopicFilter, "/");
                    if (topicFilter.equals(newTopicFilter)) {
                        retainMessageStores.add(retainMessageStore);
                    }
                }
            });
        }
        return retainMessageStores;
    }

}
