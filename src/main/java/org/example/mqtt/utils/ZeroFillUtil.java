package org.example.mqtt.utils;

public class ZeroFillUtil {

    public static String zeroFillStr(int value, int strLen){
        String strValue = String.valueOf(value);
        int valueLen = strValue.length();
        if(valueLen<strLen){
            for(int i=0;i<strLen-valueLen;i++){
                strValue = "0" + strValue;
            }
        }
        return strValue;
    }

    public static String zeroFillStr(String value, int strLen){
        int valueLen = value.length();
        if(valueLen<strLen){
            for(int i=0;i<strLen-valueLen;i++){
                value = "0" + value;
            }
        }else{
            value = value.substring(valueLen-strLen);
        }
        return value;
    }

    public static String getZeroFilledHex(int value, int offset){
        String hex = Integer.toHexString(value);
        if(hex.length()<offset){
            return zeroFillStr(hex,offset);
        }
        return hex;
    }

}
