package com.gxwtech.bleach;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import org.droidparts.activity.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gxwtech.RileyLink.CRC;
import com.gxwtech.RileyLink.GattAttributes;
import com.gxwtech.RileyLink.ReadBatteryLevelCommand;
import com.gxwtech.RileyLink.RileyLink;
import com.gxwtech.RileyLink.RileyLinkCommand;
import com.gxwtech.RileyLink.RileyLinkUtil;
import com.gxwtech.droidbits.persist.PersistentString;

import org.droidparts.adapter.widget.ArrayAdapter;
import org.droidparts.annotation.inject.InjectDependency;
import org.droidparts.concurrent.task.SimpleAsyncTask;
import org.droidparts.util.L;
import org.joda.time.DateTime;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.puckcentral.bluetooth.gatt.CharacteristicChangeListener;
import no.nordicsemi.puckcentral.bluetooth.gatt.GattCharacteristicReadCallback;
import no.nordicsemi.puckcentral.bluetooth.gatt.GattManager;
import no.nordicsemi.puckcentral.bluetooth.gatt.GattOperationBundle;
import no.nordicsemi.puckcentral.bluetooth.gatt.operations.GattCharacteristicReadOperation;
import no.nordicsemi.puckcentral.bluetooth.gatt.operations.GattCharacteristicWriteOperation;
import no.nordicsemi.puckcentral.bluetooth.gatt.operations.GattSetNotificationOperation;


public class RileyLinkTestActivity extends Activity implements CharacteristicChangeListener {
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    BroadcastReceiver mBroadcastReceiver;
    public String mDeviceName;
    public String mDeviceAddress;
    public RileyLink mRileyLink;
    BluetoothGatt mGatt = null;
    BluetoothDevice mDevice = null;
    public static final int WAKEUP_COUNTER_MAX = 90; // number of wakeup packets to send
    public static final int WAKEUP_PERIOD = 80; // milliseconds delay per wakeup packet
    int wakeupCounter = WAKEUP_COUNTER_MAX;
    public static final int RX_POLL_PERIOD_MS = 1000; // one second poll interval on RxPacket
    // (will drain, if anything is received)

    private GattManager mGattManager;

    public ArrayList<String> msgList = new ArrayList<>();
    ArrayAdapter<String> msgListAdapter = null;
    public Handler mHandler;

    int txchan = 0;
    int rxchan = 2;
    boolean mPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_riley_link_test);
        mHandler = new Handler();
        mGattManager = new GattManager();
        mGattManager.addCharacteristicChangeListener(UUID.fromString(GattAttributes.GLUCOSELINK_PACKET_COUNT), this);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRAS_DEVICE_NAME)) {
                mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
            }
            if (intent.hasExtra(EXTRAS_DEVICE_ADDRESS)) {
                mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
            }
        }
        PersistentString mRLAddress;
        mRLAddress = new PersistentString(getApplicationContext()
                .getSharedPreferences(Constants.PREF_STORAGE_NAME, 0), "RileyLinkAddress", "(empty)");
        L.e("RileyLinkAddress is " + mRLAddress.get());
        mRileyLink = new RileyLink(mRLAddress.get());

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            }
        };

    }

    public final BluetoothDevice getDevice() {
        if (mDevice == null) {
            mDevice = getAdapter().getRemoteDevice(mDeviceAddress);
        }
        return mDevice;
    }


    public void setNotify() {
        BluetoothGatt gatt;
        if (getDevice() == null) {
            lm("Get device failed");
            return;
        }

        gatt = mGatt;

        if (gatt == null) {
            lm("no gatt to connect");
            return;
        }

        BluetoothGattCharacteristic characteristic = gatt.
                getService(UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID)).
                getCharacteristic(UUID.fromString(GattAttributes.GLUCOSELINK_PACKET_COUNT));

        BluetoothGattDescriptor descriptor = characteristic.getDescriptors().get(0);
        if (0 != (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
            // It's an indicate characteristic
            L.d("onServicesDiscovered", "Characteristic (" + characteristic.getUuid() + ") is INDICATE");
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        } else {
            // It's a notify characteristic
            lm("Characteristic (" + characteristic.getUuid() + ") is NOTIFY");
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
        lm("notify has been set in RileyLink");
    }


    BluetoothAdapter getAdapter() {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        return bluetoothAdapter;
    }

    public String toHexString(byte[] bs) {
        if (bs == null) {
            return "(null)";
        }
        if (bs.length == 0) {
            return "(empty)";
        }
        String rval = String.format("%02X", bs[0]);
        if (bs.length > 1) {
            for (int i = 1; i < bs.length; i++) {
                rval += String.format(" %02X", bs[i]);
            }
        }
        return rval;
    }

    // this packet is: "turn on RF for 10 minutes"
    // needs checksum appended?
    private final byte[] pkt_rfOn10min = new byte[]{(byte) 0xa7, 0x01, 0x46, 0x73, 0x24, (byte) 0x80,
            0x02, 0x55, 0x00, 0x00, 0x00, 0x5d, 0x17, 0x01, 0x0a};

    public byte[] makeRFOnPacket() {
        return new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, 0x5d, 0x00, (byte) 0xc5};
    }

    // THIS PACKET WORKED! (after sending 90+ attention packets AND receiving a response packet
    // public byte[] pkt_readPumpModel = new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, 0x06, 0x00};
    public byte[] pkt_readPumpModel = new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, (byte)0x8d, 0x00};
    // anatomy:
    // 0xa7: attention byte
    // 0x46 0x73 0x24: pump serial
    // 0x8d: read RTC
    // 0x00: ?? (number parameters?  first parameter?)
    // (not shown: checksum of first 6 bytes
    // the response:  0d 00 a7 46 73 24 8d 09 03 37 32 32 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 7e

    public byte[] pkt_readPumpRTC = new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, 0x70};

    public byte[] makePressDownPacket() {
        byte[] p = new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, 0x5b, (byte)0x80, 0x01, 0x04};
        return p;
    }

    public void onConfigureButtonClick(View view) {
        doInitSequence();
        updateChanDisp();
    }

    public void onIncTxCButtonClick(View view) {
        txchan++;
        if (txchan > 4) { txchan = 0; }
        updateChanDisp();
        RileyLinkCommand dummy = new RileyLinkCommand(getDevice(),mGattManager);
        dummy.setTransmitChannel(txchan);
    }

    public void onIncRxCButtonClick(View view) {
        rxchan++;
        if (rxchan > 4) { rxchan = 0; }
        updateChanDisp();
        RileyLinkCommand dummy = new RileyLinkCommand(getDevice(),mGattManager);
        dummy.setReceiveChannel(rxchan);
    }

    public void updateChanDisp() {
        Button b = (Button)findViewById(R.id.button_incRxChan);
        b.setText("RxC:"+rxchan);
        b = (Button)findViewById(R.id.button_incTxChan);
        b.setText("TxC:" + txchan);
    }

    public void doInitSequence() {

        setNotify();
        lm("setNotify()");
        RileyLinkCommand dummy = new RileyLinkCommand(getDevice(),mGattManager);

        dummy.setTransmitChannel(txchan);
        lm("tx channel " + txchan);

        // we can receive from the Carelink better on channel 1, (? no proof)
        // but better from the Minimed on Channel 2 (?)
        dummy.setReceiveChannel(rxchan);
        lm("rx channel " + rxchan);

    }

    public void onReadBatteryLevelClick(View view) {
        readBatteryLevel();
    }

    public void readBatteryLevel() {
        ReadBatteryLevelCommand cmdReadBatteryLevel = new ReadBatteryLevelCommand(mDevice, mGattManager, new GattCharacteristicReadCallback() {
            @Override
            public void call(final byte[] characteristic) {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.textView_batteryLevelValue)).setText(RileyLinkUtil.toHexString(characteristic));
                    }
                });
            }});
        cmdReadBatteryLevel.run();
    }

    public void sendSimplePacket(byte[] data) {
        RileyLinkCommand cmd = new RileyLinkCommand(mDevice, mGattManager);
        //cmdRFOn.addWrite(pkt_rfOn10min);
        cmd.addWriteWithChecksum(data);
        cmd.run();
        lm("Wrote pkt " + toHexString(data));
    }

    public void sendWakeupSequence() {
        lm("Sending 90 packet wakeup");
        wakeupCounter = WAKEUP_COUNTER_MAX;
        sendNextAttentionPacket();
    }

    public void onPacketCountReadButtonClick(View view) {
        onCharaRead(GattAttributes.GLUCOSELINK_PACKET_COUNT, R.id.textView_PacketCountValue);
    }

    /*
    public void onRxPacketReadButtonClick(View view) {
        String CharaUUID = GattAttributes.GLUCOSELINK_RX_PACKET_UUID;
        final int textViewId =R.id.textView_RxPacketValue;
        if (mDeviceAddress == null) {
            return;
        }
        GattOperationBundle bundle = new GattOperationBundle();
        bundle.addOperation(new GattCharacteristicReadOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(CharaUUID), new GattCharacteristicReadCallback() {
            @Override
            public void call(byte[] characteristic) {
                final String text = toHexString(characteristic);
                lm(text);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(textViewId)).setText(text);
                    }
                });
            }
        }));
        mGattManager.queue(bundle);

    }
    */

    public void onInit90ButtonClick(View view) {
        sendWakeupSequence();
    }

    public void onReqModelNumberButtonClick(View view) {
        sendSimplePacket(pkt_readPumpModel);
    }

    public void onSendButtonDownButtonClick(View view) {
        sendSimplePacket(makePressDownPacket());
    }



    public void onCharaRead(String CharaUUID, final int textViewId) {
        if (mDeviceAddress == null) {
            return;
        }
        GattOperationBundle bundle = new GattOperationBundle();
        bundle.addOperation(new GattCharacteristicReadOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(CharaUUID), new GattCharacteristicReadCallback() {
            @Override
            public void call(byte[] characteristic) {
                final String text = toHexString(characteristic);
                lm(text);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(textViewId)).setText(text);
                    }
                });
            }
        }));
        mGattManager.queue(bundle);

    }

    public void lm(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgList.add(0, s);
                msgListAdapter.notifyDataSetChanged();
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_riley_link_test, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void enableButton(final int id, final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((Button) findViewById(id)).setEnabled(enable);
            }
        });
    }

    public void setGatt(BluetoothGatt gatt) {
        mGatt = gatt;
    }

    public void closeGatt() {
        if (mGatt != null) {
            mGatt.close();
        }
    }

    @Override
    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
        String timestamp = DateTime.now().toString();
        String valueStr = toHexString(characteristic.getValue());
        final String msg = "Note: " + timestamp + ":" + valueStr;
        lm(msg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (msgListAdapter != null) {
            msgListAdapter.clear();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        msgListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgList);
        ListView lv = (ListView)findViewById(R.id.listView_msglist);
        lv.setAdapter(msgListAdapter);

        if (getDevice() != null) {
            lm("Connecting");
            closeGatt();
            getDevice().connectGatt(RileyLinkTestActivity.this, true, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);

                    L.e("Got status " + status + " and state " + newState);
                    // Catch-all for a variety of undocumented error codes.
                    // Documented at https://code.google.com/r/naranjomanuel-opensource-broadcom-ble/source/browse/api/java/src/com/broadcom/bt/le/api/BleConstants.java?r=f535f31ec89eb3076a2b75ddf586f4b3fc44384b
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        L.e("Ouch! Disconnecting! status: " + status + " newState " + newState);
                        gatt.disconnect();
                        return;
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        L.e("Connected to service!");
                        lm("Connected -- discovering services");
                        setGatt(gatt);
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        L.e("Link disconnected");
                        //gatt.close();
                    } else {
                        L.e("Received something else, ");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    ArrayList<UUID> serviceUUIDs = mRileyLink.getServiceUUIDs();

                    for (BluetoothGattService service : gatt.getServices()) {
                        serviceUUIDs.add(service.getUuid());
                        if (service.getUuid().equals(UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID))) {
                            List<BluetoothGattCharacteristic> clist = service.getCharacteristics();
                            for (BluetoothGattCharacteristic ch : clist) {
                                if (ch.getUuid().equals(UUID.fromString(GattAttributes.GLUCOSELINK_PACKET_COUNT))) {
                                    Log.e("GGW", "Found Packet Count chara");

                                    List<BluetoothGattDescriptor> dlist = ch.getDescriptors();
                                    int i = 0;
                                    for (BluetoothGattDescriptor d : dlist) {
                                        int perm = d.getPermissions();
                                        UUID u = d.getUuid();
                                        byte[] value = d.getValue();
                                        String valueString = "(empty)";
                                        if (value == null) {
                                            valueString = "(null)";
                                        } else if (value.length > 0) {
                                            valueString = toHexString(value);
                                        }
                                        String s = String.format("%d:perm=0x%X,UUID=%s,value=%s",
                                                i, perm, u.toString(), valueString);
                                        i++;
                                        Log.e("GGW", s);
                                    }
                                }
                            }
                        }
                    }
                    mRileyLink.setServiceUUIDs(serviceUUIDs);
                    L.e("Now has services: " + mRileyLink.getServiceUUIDs());
                    lm("Now has services: " + mRileyLink.getServiceUUIDs());

                    enableButton(R.id.button_Init, true);
                    enableButton(R.id.button_batteryLevel, true);
                    //enableButton(R.id.button_RxPacketReadButton,true);
                    enableButton(R.id.button_PacketCountReadButton, true);
                    enableButton(R.id.button_incRxChan, true);
                    enableButton(R.id.button_incTxChan, true);

                    new SimpleAsyncTask<Void>(getApplicationContext(), null) {

                        @Override
                        protected Void onExecute() throws Exception {
                            /*
                            BluetoothAdapter adapter = ((BluetoothManager) getApplicationContext().getSystemService
                                    (Context.BLUETOOTH_SERVICE)).getAdapter();
                                    */
                            //lm("Gathering descriptors for packetCount");

                            lm("Setting Notifications for packetCount");
                            mGattManager.queue(new GattSetNotificationOperation(
                                    getDevice(),
                                    UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                                    UUID.fromString(GattAttributes.GLUCOSELINK_PACKET_COUNT)));

                            // Android BLE stack might have issues connecting to
                            // multiple Gatt services right after another.
                            // See: http://stackoverflow.com/questions/21237093/android-4-3-how-to-connect-to-multiple-bluetooth-low-energy-devices
                            Thread.sleep(1000);
                            return null;
                        }
                    }.execute();
                    gatt.disconnect();
                }
            });
        }

        Toast.makeText(this, "Yay! we got this far.", Toast.LENGTH_SHORT).show();
    }

    public void sendNextAttentionPacket() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // send the packet
                RileyLinkCommand cmdRFOn = new RileyLinkCommand(mDevice, mGattManager);
                byte[] wakeup = new byte[]{(byte) 0xa7, 0x46, 0x73, 0x24, 0x5d, 0x00};
                //byte[] check = RileyLinkUtil.encodeData(wakeup);
                cmdRFOn.addWriteWithChecksum(wakeup);
                cmdRFOn.run();
                // decrement counter
                wakeupCounter--;
                if (wakeupCounter > 0) {
                    // reschedule
                    sendNextAttentionPacket();
                } else {
                    lm("wakeup cycle complete");
                    if (!mPolling) {
                        mPolling = true;
                        pollRxPacket(); // start polling packets when we've done the configure
                    }
                    //sendButtonDown();
                    //lm("button down sent");
                    //sendSimplePacket(pkt_readPumpRTC);
                    //lm("sent readpump-rtc");

                    //sendSimplePacket(pkt_readPumpModel);
                    //lm("sent readpump-model");
                }
            }
        }, WAKEUP_PERIOD /* millis */);
    }

    public void handleReceivedRxPacket(byte[] pkt) {
        // enqueue in a receive buffer
        // This packet is from the Rx Packet buffer, but as it is asynchronous,
        // we don't know if this is a minimed response, or a carelink packet, or what.
        // Inspect packet contents.
        // If it is a "response to attention command (i.e. payload is 0x06, 0x00, <CRC=0x6E for my pump>)
        // then we've gotten the pump's attention.
        // Can: simply list all packets received (in log messages)
        // Can: keep track of unique packets received, alert on new ones.

        // if: length 3, malformed packet (noise), increment noise counter
        if (pkt.length < 3) {
            // bad packet from rileylink ???
            // drop it on the floor.
        }
        if (pkt.length == 3) {
            // malformed packet
            // increase malformed packet counter
        }
        // if: new packet, print it.
        // check packet against recorded packets

        // if: checksum is wrong, note it.
        // calculate checksum for packet
        /*
        must match from byte[2] to [length -2]
        byte crc = CRC.crc8(pkt,pkt.length - 1);
        if (crc != pkt[pkt.length-1]) {
            // crc mismatch
            lm("CRC mismatch");
        }
        */
        // if: is an "attention response" packet, note that we have received a response
        /*
        if (pkt.length == 7) {
            if ((pkt[4] == 0x06) && (pkt[5] == 0x00) && (pkt[6] == 0x6e)) {
                // it is an attention response packet
            }
        }
        */
        // show statistics for noise packets, checksum correct, checksum wrong, attention responses received, other
        // updateStatistics();
        lm("recvd:" + toHexString(pkt));

    }

    public boolean getNextRxPacket() {
        if (mDeviceAddress == null) {
            return false; // means fail, don't call us again until the device is connected.
        }
        GattOperationBundle bundle = new GattOperationBundle();
        bundle.addOperation(new GattCharacteristicReadOperation(getDevice(), UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
                UUID.fromString(GattAttributes.GLUCOSELINK_RX_PACKET_UUID), new GattCharacteristicReadCallback() {
            @Override
            public void call(byte[] characteristic) {
                // if response is empty, start next delayed poll
                if ((characteristic == null) || (characteristic.length == 0)) {
                    return;
                } else {
                    // else, drain RxPacket
                    handleReceivedRxPacket(characteristic);
                    getNextRxPacket();
                }
            }
        }));

        mGattManager.queue(bundle);
        return true; // means operation succeeded, can call us again (means mDeviceAddress was not null)

    }

    public void pollRxPacket() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getNextRxPacket()) {
                    pollRxPacket();
                }
            }
        }, RX_POLL_PERIOD_MS /* millis */);
    }


}
