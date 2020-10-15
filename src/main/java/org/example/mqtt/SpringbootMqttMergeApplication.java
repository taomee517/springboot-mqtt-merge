package org.example.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.example.mqtt.utils.SystemUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 罗涛
 * @title BchdAcceptorApplication
 * @date 2020/6/22 14:54
 */
@Slf4j
@SpringBootApplication
public class SpringbootMqttMergeApplication {
    public static void main(String[] args) {
        String localIp = SystemUtil.getRealIP();
        log.info("本机ip:{}", localIp);
        System.setProperty("local-ip", localIp);
        SpringApplication.run(SpringbootMqttMergeApplication.class);
    }
}
