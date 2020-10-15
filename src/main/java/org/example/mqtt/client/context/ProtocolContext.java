package org.example.mqtt.client.context;

import org.example.mqtt.client.protocol.IMqttProtocol;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class ProtocolContext implements InitializingBean, ApplicationContextAware {
    
    public Set<IMqttProtocol> MQTTS = new HashSet<>();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, IMqttProtocol> protocolMap = applicationContext.getBeansOfType(IMqttProtocol.class);
        Collection<IMqttProtocol> protocols = protocolMap.values();
        for(IMqttProtocol protocol:protocols){
            MQTTS.add(protocol);
        }
    }

    public IMqttProtocol getProtocolByPort(int sensorType){
        for(IMqttProtocol protocol: MQTTS){
            if(protocol.sensorType() == sensorType){
                return protocol;
            }
        }
        return null;
    }
}
