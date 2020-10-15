package org.example.mqtt.server.handlers;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.mqtt.server.config.KafkaTopicProperties;
import org.example.mqtt.server.context.ContextManager;
import org.example.mqtt.server.service.IMqttService;
import org.example.mqtt.utils.logger.DeviceLog;
import org.example.mqtt.utils.logger.StreamType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Slf4j
@Sharable
@Component
public class MqttLoggerHandler extends ChannelOutboundHandlerAdapter{

    @Autowired
    KafkaTemplate jsonKafka;

    @Autowired
    KafkaTopicProperties kafkaTopicProperties;

    @Autowired
    IMqttService mqttService;

    @Autowired
    ContextManager contextManager;

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            String clientId = contextManager.getClientId(ctx.channel());
            if(StringUtils.isNotEmpty(clientId)) {
                if (msg instanceof MqttMessage) {
                    MqttMessage mqttMessage = (MqttMessage) msg;
                    MqttMessageType mqttMessageType = mqttMessage.fixedHeader().messageType();
                    Map<String, Object> content = new HashMap<>();
                    content.put("fixedHeader", mqttMessage.fixedHeader().toString());
                    switch (mqttMessageType) {
                        case CONNACK:
                        case DISCONNECT:
                        case PINGRESP:
                        case SUBACK:
                        case UNSUBACK:
                        case PUBACK:
                        case UNSUBSCRIBE:
                        case CONNECT:
                        case PINGREQ:
                        case PUBREC:
                        case PUBCOMP:
                        default:
                            break;
                    }
                    Object variableHeader = mqttMessage.variableHeader();
                    if(Objects.nonNull(variableHeader)){
                        content.put("variableHeader", variableHeader.toString());
                    }
                    Object payload = mqttMessage.payload();
                    log.info("下行消息：clientId = {}, msgType={}, payload={}",clientId, mqttMessageType, payload);
                    DeviceLog deviceLog = new DeviceLog();
                    deviceLog.setMsgTime(new Date());
                    deviceLog.setDeviceId(clientId);
                    deviceLog.setDirection(StreamType.DOWN_STREAM);
                    String localIp = System.getProperty("local-ip");
                    deviceLog.setServerIp(localIp);
                    if(Objects.nonNull(payload)){
                        if(payload instanceof ByteBuf){
                            ByteBuf byteBuf = (ByteBuf) payload;
                            byte[] bytes = new byte[byteBuf.readableBytes()];
                            byteBuf.getBytes(0, bytes);
                            String plainPayload = mqttService.parsePayload(bytes);
                            content.put("payload", plainPayload);
                        }else {
                            content.put("payload", payload.toString());
                        }
                    }
                    deviceLog.setContent(JSON.toJSONString(content));
                    deviceLog.setSensorType(99999);
                    jsonKafka.send(kafkaTopicProperties.deviceLog, deviceLog);
                } else {
                    log.warn("暂未支持的消息类型，type = {}", msg.getClass());
                }
            }
        } finally {
            ctx.writeAndFlush(msg, promise);
        }
    }


}
