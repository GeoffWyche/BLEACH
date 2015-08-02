package com.gxwtech.RileyLink;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import no.nordicsemi.puckcentral.bluetooth.gatt.GattCharacteristicReadCallback;
import no.nordicsemi.puckcentral.bluetooth.gatt.GattManager;
import no.nordicsemi.puckcentral.bluetooth.gatt.GattOperationBundle;
import no.nordicsemi.puckcentral.bluetooth.gatt.operations.GattCharacteristicReadOperation;
import no.nordicsemi.puckcentral.bluetooth.gatt.operations.GattCharacteristicWriteOperation;

/**
 * Created by geoff on 7/27/15.
 */
public class RileyLinkCommand {
    public int nOps = 0;
    public GattOperationBundle bundle = new GattOperationBundle();
    public BluetoothDevice mDevice = null;
    public GattManager mGattManager = null;
    public RileyLinkCommand(BluetoothDevice device, GattManager gattManager) {
        mDevice = device;
        mGattManager = gattManager;
    }

    public BluetoothDevice getDevice() { return mDevice; }

    public boolean addWrite(final byte[] pkt) {
        final byte[] minimedRFData = RileyLinkUtil.composeRFStream(pkt);

        bundle.addOperation(new GattCharacteristicWriteOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(GattAttributes.GLUCOSELINK_TX_PACKET_UUID), minimedRFData));

        bundle.addOperation(new GattCharacteristicWriteOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(GattAttributes.GLUCOSELINK_TX_TRIGGER_UUID), new byte[] {0x01}));

        return true;
    }
    public boolean addRead(byte[] pkt, GattCharacteristicReadCallback cb) {
        bundle.addOperation(new GattCharacteristicReadOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(GattAttributes.GLUCOSELINK_RX_PACKET_UUID),cb));

        return true;
    }
    public void run() {
        mGattManager.queue(bundle);
    }
}
