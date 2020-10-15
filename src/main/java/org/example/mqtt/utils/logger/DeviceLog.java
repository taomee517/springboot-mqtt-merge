package org.example.mqtt.utils.logger;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.Date;

/**
 * @author 罗涛
 * @title DeviceLog
 * @date 2020/7/3 9:22
 */
@Data
public class DeviceLog {
    //服务器ip
    private String serverIp;

    //消息时间
    @JSONField(format="yyyy-MM-dd HH:mm:ss.SSS")
    private Date msgTime;

    //设备类型
    private Integer sensorType;

    private String deviceId;

    //消息方向 (上行， 下行)
    private StreamType direction;

    //消息内容
    private String content;


}
