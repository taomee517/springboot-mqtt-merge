package org.example.mqtt.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "spring.ignite.config")
public class IgniteProperties {

    /**
     * 持久化缓存内存初始化大小(MB), 默认值: 64
     */
    private int persistenceInitialSize = 64;

    /**
     * 持久化缓存占用内存最大值(MB), 默认值: 128
     */
    private int persistenceMaxSize = 128;

    /**
     * 持久化磁盘存储路径
     */
    private String persistenceStorePath;

    /**
     * 非持久化缓存内存初始化大小(MB), 默认值: 64
     */
    private int NotPersistenceInitialSize = 64;

    /**
     * 非持久化缓存占用内存最大值(MB), 默认值: 128
     */
    private int NotPersistenceMaxSize = 128;

    /**
     * 集群数据同步主题
     */
    private String clusterInternalTopic;

    /**
     * 节点名称
     */
    private String brokerId;

    /**
     * 静态IP方式集群
     */
    private List<String> staticIpAddresses;

    /**
     * 开启组播方式集群
     */
    private boolean enableMulticastGroup;

    /**
     * 组播地址
     */
    private String multicastGroup;
}
