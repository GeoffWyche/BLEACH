package com.gxwtech.RileyLink;

import java.util.HashMap;

/**
 * Created by geoff on 7/10/15.
 */
public class GattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    // NOTE: these uuid strings must be lower case!
    public static String GLUCOSELINK_SERVICE_UUID = "d39f1890-17eb-11e4-8c21-0800200c9a66";
    //public static String GLUCOSELINK_BATTERY_SERVICE = "180f";
    public static String GLUCOSELINK_BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    public static String GLUCOSELINK_RX_PACKET_UUID = "2fb1a490-1940-11e4-8c21-0800200c9a66";
    public static String GLUCOSELINK_CHANNEL_UUID = "d93b2af0-1ea8-11e4-8c21-0800200c9a66";
    public static String GLUCOSELINK_PACKET_COUNT = "41825a20-7402-11e4-8c21-0800200c9a66";
    public static String GLUCOSELINK_TX_PACKET_UUID = "2fb1a490-1941-11e4-8c21-0800200c9a66";
    public static String GLUCOSELINK_TX_TRIGGER_UUID = "2fb1a490-1942-11e4-8c21-0800200c9a66";
    //public static String GLUCOSELINK_BATTERY_UUID = "2A19";
    public static String GLUCOSELINK_BATTERY_UUID = "00002a19-0000-1000-8000-00805f9b34fb";


    static {
        // Sample Services.
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        attributes.put(GLUCOSELINK_BATTERY_SERVICE,"RileyLink Battery Service");
        attributes.put(GLUCOSELINK_BATTERY_UUID,"RileyLink Battery Level");

        attributes.put(GLUCOSELINK_SERVICE_UUID,"RileyLink Service");
        attributes.put(GLUCOSELINK_CHANNEL_UUID,"RileyLink Channel");
        attributes.put(GLUCOSELINK_PACKET_COUNT,"RileyLink Packet Count");
        attributes.put(GLUCOSELINK_RX_PACKET_UUID,"RileyLink RX Packet");
        attributes.put(GLUCOSELINK_TX_PACKET_UUID,"RileyLink TX Packet");
        attributes.put(GLUCOSELINK_TX_TRIGGER_UUID,"RileyLink TX Trigger");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}