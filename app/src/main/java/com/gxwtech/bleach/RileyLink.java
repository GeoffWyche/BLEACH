package com.gxwtech.bleach;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by geoff on 7/10/15.
 */
public class RileyLink {
    private ArrayList<UUID> mServiceUUIDs;
    private String mAddress;
    public RileyLink(ArrayList<UUID> serviceUUIDs,String address) {
        mServiceUUIDs = serviceUUIDs;
        mAddress = address;
    }
    public void setServiceUUIDs(ArrayList<UUID> serviceUUIDs) {
        mServiceUUIDs = serviceUUIDs;
    }
    public ArrayList<UUID> getServiceUUIDs() {
        return mServiceUUIDs;
    }
    public String getAddress() {
        return mAddress;
    }

}
