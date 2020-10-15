package org.example.mqtt.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemotingUtil {
    public static final String OS_NAME = System.getProperty("os.name");

    private static final Logger log = LoggerFactory.getLogger(RemotingUtil.class);
    private static boolean isLinuxPlatform = false;
    private static boolean isWindowsPlatform = false;

    static {
        if (OS_NAME != null && OS_NAME.toLowerCase().contains("linux")) {
            isLinuxPlatform = true;
        }

        if (OS_NAME != null && OS_NAME.toLowerCase().contains("windows")) {
            isWindowsPlatform = true;
        }
    }

    public static boolean isLinuxPlatform() {
        return isLinuxPlatform;
    }
}
