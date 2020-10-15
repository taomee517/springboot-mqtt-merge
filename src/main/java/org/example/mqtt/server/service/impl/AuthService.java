package org.example.mqtt.server.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.example.mqtt.server.config.MqttServerProperties;
import org.example.mqtt.server.service.IAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService implements IAuthService {

    @Autowired
    MqttServerProperties mqttServerProperties;

    @Override
    public boolean checkValid(String user, String pwd) {
        Boolean authCheckEnable = mqttServerProperties.getAuthCheckEnable();
        if (!authCheckEnable) return true;
        if(StringUtils.isEmpty(user)) return false;
        if(StringUtils.isEmpty(pwd)) return false;
        String authUser = mqttServerProperties.getUsername();
        String authPwd = mqttServerProperties.getPassword();
        return StringUtils.equalsIgnoreCase(user, authUser) && StringUtils.equalsIgnoreCase(pwd, authPwd);
    }
}
