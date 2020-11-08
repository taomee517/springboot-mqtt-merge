package org.example.mqtt.client;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.example.mqtt.client.config.MqttClientProperties;
import org.example.mqtt.client.context.ProtocolContext;
import org.example.mqtt.client.handlers.PushCallback;
import org.example.mqtt.client.protocol.IMqttProtocol;
import org.example.mqtt.utils.tls.SslContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author 罗涛
 * @title MqttPushClient
 * @date 2020/10/10 17:38
 */
@Getter
@Slf4j
@Component
@DependsOn("nettyServer")
public class MqttPushBootstrap implements SmartLifecycle {
    @Autowired
    MqttClientProperties mqttClientProperties;

    @Autowired
    ProtocolContext protocolContext;

    private static Set<MqttClient> clients = new HashSet<>();
    private boolean running;
    private MemoryPersistence memoryPersistence;
    private MqttConnectOptions mqttConnectOptions;


    @Override
    public void start() {
        try {
            memoryPersistence = new MemoryPersistence();
            mqttConnectOptions = buildCommonOption();
            Set<IMqttProtocol> mqtts = protocolContext.MQTTS;
            for (IMqttProtocol protocol: mqtts) {
                MqttClient client = connect(protocol);
                subscribeDefaultTopics(client, protocol);
                log.info("与mqttServer连接建立成功:{}", client.isConnected());
            }
            running = true;
        } catch (Exception e) {
            log.error("初始化MqttClient时发生异常：" + e.getMessage(), e);
//            start();
        }
    }

    @Override
    public void stop() {
        try {
            if (!CollectionUtils.isEmpty(clients)) {
                for(MqttClient client: clients) {
                    if (client.isConnected()) {
//                        client.disconnect();
                        client.close();
                        memoryPersistence.close();
                    }
                }
            }
            running = false;
        } catch (MqttException e) {
            log.error("MqttClient关闭时发生异常：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 客户端连接
     */
    public MqttClient connect(IMqttProtocol protocol) throws Exception {
        if(Objects.isNull(memoryPersistence)) return null;
        String clientId = protocol.clientId();
        String username = protocol.username();
        String password = protocol.password();
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(password.toCharArray());
        String hostUrl = mqttClientProperties.getHostUrl();
        MqttClient client = new MqttClient(hostUrl, clientId, memoryPersistence);
        PushCallback pushCallback = new PushCallback(this, client, protocol);
        client.setCallback(pushCallback);
        client.connect(mqttConnectOptions);
        clients.add(client);
        return client;
    }

    public void subscribeDefaultTopics(MqttClient client, IMqttProtocol protocol) throws MqttException {
        // 以/#结尾表示订阅所有以test开头的主题
        List<String> defaultTopics = protocol.defaultTopics();
        if(!CollectionUtils.isEmpty(defaultTopics)){
            String [] topicArray = defaultTopics.toArray(new String[defaultTopics.size()]);
            log.info("订阅默认主题:{}", defaultTopics.toString());
            client.subscribe(topicArray);
        }
    }


    public MqttConnectOptions buildCommonOption() throws Exception {
        int timeout = mqttClientProperties.getTimeout();
        int keepalive = mqttClientProperties.getKeepalive();
        MqttConnectOptions options = new MqttConnectOptions();
//        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(timeout);
        options.setKeepAliveInterval(keepalive);

        Boolean sslEnable = mqttClientProperties.getSslEnable();
        if (sslEnable) {
            String sslPassword = mqttClientProperties.getSslPassword();
            String clientCertPath = mqttClientProperties.getClientCertPath();
            String rootCertPath = mqttClientProperties.getRootCertPath();
//            String clientCertPath = "D:\\scripts\\ca-key\\prod\\client.jks";
//            String rootCertPath = "D:\\scripts\\ca-key\\prod\\root.jks";
            SSLContext clientContext = SslContextUtil.getClientContext(clientCertPath, sslPassword, rootCertPath, sslPassword);
            SSLSocketFactory socketFactory = clientContext.getSocketFactory();
            options.setSocketFactory(socketFactory);
        }
        return options;
    }


}


