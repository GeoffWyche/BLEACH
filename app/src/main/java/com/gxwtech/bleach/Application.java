package com.gxwtech.bleach;

import android.bluetooth.BluetoothAdapter;

import org.droidparts.AbstractApplication;


public class Application extends AbstractApplication {

    public void onCreate() {
        super.onCreate();
        enableBluetooth();
    }

    public void enableBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter != null) {
            bluetoothAdapter.enable();
        }
    }
}
