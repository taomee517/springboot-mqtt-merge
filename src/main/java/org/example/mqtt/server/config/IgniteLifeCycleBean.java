package org.example.mqtt.server.config;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.lifecycle.LifecycleBean;
import org.apache.ignite.lifecycle.LifecycleEventType;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class IgniteLifeCycleBean implements LifecycleBean, ApplicationListener<ApplicationReadyEvent>
{
    Logger logger = LoggerFactory.getLogger(IgniteLifeCycleBean.class);

    @IgniteInstanceResource
    Ignite ignite;

    @Override
    public void onLifecycleEvent(LifecycleEventType lifecycleEventType) throws IgniteException{
        if(lifecycleEventType == LifecycleEventType.BEFORE_NODE_START){
            logger.info("Ignite instance {} exists.", ignite.name());
        } else if (lifecycleEventType == LifecycleEventType.AFTER_NODE_START){
            logger.info("Loading cache data.");
        } else if (lifecycleEventType == LifecycleEventType.BEFORE_NODE_STOP){
            logger.info("Terminating Ignite instance {}", ignite.name());
            ignite.cache("short-cache").clear();
        } else if (lifecycleEventType == LifecycleEventType.AFTER_NODE_STOP){
            logger.info("Node stopped.");
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent){
        loadCache();
    }

    public void loadCache(){
        logger.info("Pre-loading cache with spring data");
    }
}
