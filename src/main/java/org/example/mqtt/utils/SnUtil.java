//package org.example.mqtt.utils;
//
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class SnUtil {
//    private static AtomicInteger atomitSn = new AtomicInteger(1);
//    private static ConcurrentHashMap<String,AtomicInteger> SN_MAP = new ConcurrentHashMap<>();
//
//    public static int getSn(){
//        int sn = atomitSn.getAndAdd(1);
//        return sn & 0xffff;
//    }
//
//    public static int getSn(String imei){
//        AtomicInteger snAto = null;
//        if(!SN_MAP.containsKey(imei)){
//            snAto = new AtomicInteger(1);
//            SN_MAP.put(imei, snAto);
//            return snAto.get();
//        }
//        snAto = SN_MAP.get(imei);
//        int sn = snAto.addAndGet(1);
//        return sn & 0xffff;
//    }
//}
