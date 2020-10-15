package org.example.mqtt.server.handlers;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.mqtt.server.config.KafkaTopicProperties;
import org.example.mqtt.server.context.ContextManager;
import org.example.mqtt.server.context.mqtt.SessionStore;
import org.example.mqtt.server.service.IMqttService;
import org.example.mqtt.utils.BytesUtil;
import org.example.mqtt.utils.logger.DeviceLog;
import org.example.mqtt.utils.logger.StreamType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author 罗涛
 * @title BrokerHandler
 * @date 2020/9/10 18:05
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class BrokerHandler extends ChannelDuplexHandler{

    @Autowired
    MqttProcessor mqttProcessor;

    @Autowired
    IMqttService mqttService;

    @Autowired
    KafkaTemplate jsonKafka;

    @Autowired
    KafkaTopicProperties kafkaTopicProperties;

    @Autowired
    ContextManager contextManager;


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.warn("建立连接, serial = {},  local = {}, remote = {}", ctx.channel().id(), ctx.channel().localAddress(), ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("断开连接, serial = {},  local = {}, remote = {}", ctx.channel().id(), ctx.channel().localAddress(), ctx.channel().remoteAddress());
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MqttMessage message = (MqttMessage) msg;
        if(Objects.isNull(message.fixedHeader())) return;
        MqttMessageType mqttMessageType = message.fixedHeader().messageType();
        log.info("消息类型：{}", mqttMessageType);
        recordUpstreamLog(message,ctx);
        switch (mqttMessageType){
            case CONNECT:
                MqttConnectPayload payload = ((MqttConnectPayload) message.payload());
                log.info("connect payload:{}", payload);
                mqttProcessor.processConnect(ctx.channel(), ((MqttConnectMessage) message));
                break;
            case PUBLISH:
                ByteBuf publishPayload = (ByteBuf) message.payload();
                byte[] bytes = new byte[publishPayload.readableBytes()];
                publishPayload.getBytes(publishPayload.readerIndex(), bytes);
                String hex = BytesUtil.bytes2HexWithBlank(bytes, true);
                String ascii = new String(bytes);
                log.info("publish payload: hex = {}, ascii = {}", hex, ascii);
                mqttProcessor.processPublish(ctx.channel(),(MqttPublishMessage) message);
                break;
            case PUBACK:
                MqttMessageIdVariableHeader pubAckVariableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
                log.info("puback header:{}", pubAckVariableHeader);
                mqttProcessor.processPubAck(ctx.channel(), pubAckVariableHeader);
                break;
            case PUBREC:
                MqttMessageIdVariableHeader pubRecVariableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
                log.info("pubrec header:{}", pubRecVariableHeader);
                mqttProcessor.processPubRec(ctx.channel(), pubRecVariableHeader);
                break;
            case PUBREL:
                MqttMessageIdVariableHeader pubRelVariableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
                log.info("pubRel header:{}", pubRelVariableHeader);
                mqttProcessor.processPubRel(ctx.channel(), pubRelVariableHeader);
                break;
            case PUBCOMP:
                MqttMessageIdVariableHeader pubCompVariableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
                log.info("pubComp header:{}", pubCompVariableHeader);
                mqttProcessor.processPubComp(ctx.channel(), pubCompVariableHeader);
                break;
            case SUBSCRIBE:
                MqttSubscribePayload subscribePayload = ((MqttSubscribePayload) message.payload());
                log.info("subscribe payload:{}", subscribePayload);
                mqttProcessor.processSubscribe(ctx.channel(), ((MqttSubscribeMessage) message));
                break;
            case UNSUBSCRIBE:
                MqttUnsubscribePayload unsubscribePayload = (MqttUnsubscribePayload) message.payload();
                log.info("unsubscribe payload:{}", unsubscribePayload);
                mqttProcessor.processUnSubscribe(ctx.channel(), ((MqttUnsubscribeMessage) message));
                break;
            case PINGREQ:
                mqttProcessor.processPingReq(ctx.channel(), message);
                break;
            case DISCONNECT:
                mqttProcessor.processDisConnect(ctx.channel(), message);
            case CONNACK:
                MqttConnAckMessage connAckMessage = ((MqttConnAckMessage) message.payload());
                log.info("connect ack payload:{}", connAckMessage);
                break;
            case SUBACK:
                MqttSubAckPayload subAckMessage = (MqttSubAckPayload) message.payload();
                log.info("suback payload:{}", subAckMessage);
                break;
            case UNSUBACK:
            case PINGRESP:
            default:
                break;
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            // 远程主机强迫关闭了一个现有的连接的异常
            ctx.close();
        } else {
            log.error(cause.getMessage(), cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.ALL_IDLE) {
                String clientId = contextManager.getClientId(ctx.channel());
                Channel channel = ctx.channel();
                // 发送遗嘱消息
                if (mqttService.containsSession(clientId)) {
                    SessionStore sessionStore = mqttService.getSession(clientId);
                    if (sessionStore.getWillMessage() != null) {
                        mqttProcessor.processPublish(ctx.channel(),sessionStore.getWillMessage());
                    }
                }
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof MqttMessage) {
            ctx.writeAndFlush(msg, promise);
        }else {
            log.warn("非MQTT消息类型：{}", msg.getClass());
            ctx.writeAndFlush(msg, promise);
        }
    }

    public void recordUpstreamLog(MqttMessage message, ChannelHandlerContext context){
        String clientId = contextManager.getClientId(context.channel());
        MqttMessageType mqttMessageType = message.fixedHeader().messageType();
        Map<String, Object> content = new HashMap<>();
        content.put("fixedHeader", message.fixedHeader().toString());
        switch (mqttMessageType){
            case CONNECT:
                clientId = ((MqttConnectPayload) message.payload()).clientIdentifier();
            case DISCONNECT:
            case PINGREQ:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case PUBLISH:
            case PUBREL:
            case PUBREC:
            case PUBCOMP:
            default:
                break;
        }
        Object variableHeader = message.variableHeader();
        if(Objects.nonNull(variableHeader)){
            content.put("variableHeader", variableHeader.toString());
        }
        Object payload = message.payload();
        log.info("上行消息：clientId = {}, msgType={}, payload={}",clientId, mqttMessageType, payload);
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setMsgTime(new Date());
        deviceLog.setDeviceId(clientId);
        deviceLog.setDirection(StreamType.UP_STREAM);
        String localIp = System.getProperty("local-ip");
        deviceLog.setServerIp(localIp);
        if(Objects.nonNull(payload)){
            if(payload instanceof ByteBuf){
                ByteBuf byteBuf = (ByteBuf) payload;
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.getBytes(0, bytes);
                //从channel中获取protocol 实例
                String plainPayload = mqttService.parsePayload(bytes);
                content.put("payload", plainPayload);
            }else {
                content.put("payload", payload.toString());
            }
        }
        deviceLog.setContent(JSON.toJSONString(content));
        deviceLog.setSensorType(99999);
        jsonKafka.send(kafkaTopicProperties.deviceLog, deviceLog);
    }

}
