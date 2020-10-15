package org.example.mqtt.server.service;

public interface IAuthService {
    boolean checkValid(String user, String pwd);
}
