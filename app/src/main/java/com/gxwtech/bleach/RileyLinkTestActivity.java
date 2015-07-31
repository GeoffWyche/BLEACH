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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import org.droidparts.activity.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gxwtech.RileyLink.GattAttributes;
import com.gxwtech.RileyLink.RileyLink;
import com.gxwtech.RileyLink.RileyLinkCommand;
import com.gxwtech.RileyLink.RileyLinkUtil;
import com.gxwtech.droidbits.persist.PersistentString;

import org.droidparts.adapter.widget.ArrayAdapter;
import org.droidparts.annotation.inject.InjectDependency;
import org.droidparts.concurrent.task.SimpleAsyncTask;
import org.droidparts.util.L;
import org.joda.time.DateTime;

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
    public String mDeviceName;
    public String mDeviceAddress;
    public RileyLink mRileyLink;
    BluetoothGatt mGatt = null;
    BluetoothDevice mDevice = null;
    int tpcounter = 0;

    private GattManager mGattManager;

    public ArrayList<String> msgList = new ArrayList<>();
    ArrayAdapter<String> msgListAdapter = null;
    public Handler mHandler;

    //public GattManager mGattManager;

    //public CharaWidget mPacketCountWidget;
    //public CharaWidget mRxPacketWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_riley_link_test);
        mHandler = new Handler();
        mGattManager = new GattManager();
        mGattManager.addCharacteristicChangeListener(UUID.fromString(GattAttributes.GLUCOSELINK_PACKET_COUNT),this);

        /*
        mPacketCountWidget = new CharaWidget(getApplicationContext(),R.id.textView_PacketCountLabel,
                R.id.textView_PacketCountValue,R.id.button_PacketCountReadButton);
        mRxPacketWidget = new CharaWidget(getApplicationContext(),R.id.textView_RxPacketLabel,
                R.id.textView_RxPacketValue,R.id.button_RxPacketReadButton);
*/
        Intent intent = getIntent();
        if (intent!=null) {
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
            return;
        }

        //gatt = mGattManager.getGatt(getDevice());
        gatt = mGatt;

        if (gatt == null) {
            L.e("no gatt to connect");
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
            L.d("onServicesDiscovered", "Characteristic (" + characteristic.getUuid() + ") is NOTIFY");
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
    }


    BluetoothAdapter getAdapter() {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        return bluetoothAdapter;
    }

    public String toHexString(byte[] bs) {
        if (bs==null) {
            return "(null)";
        }
        if (bs.length == 0) {
            return "(empty)";
        }
        String rval = String.format("%02X", bs[0]);
        if (bs.length > 1) {
            for (int i=1; i<bs.length; i++) {
                rval += String.format(" %02X",bs[i]);
            }
        }
        return rval;
    }

    // this packet is: "turn on RF for 3 minutes"
    private final byte[] pkt_rfOn3min = new byte[] {(byte)0xa7, 0x01, 0x46, 0x73, 0x24, (byte)0x80,
            0x02, 0x55, 0x00, 0x00, 0x00, 0x5d, 0x17, 0x01, 0x03};

    private byte[] pkt_pressdown = new byte[] {(byte)0xa7, 0x01, 0x46, 0x73, 0x24,
            (byte)0x80, 0x01, 0x00, 0x01, 0x00, 0x00, 0x5b, (byte)0x9e, 0x04, (byte)0xc1};

    public int initButtonClicks = 0;
    public void onInitButtonClick(View view) {
        initButtonClicks++;
        if (mDeviceAddress == null) {
            return;
        }
        RileyLinkCommand cmd = new RileyLinkCommand(mDevice,mGattManager);
        cmd.addWrite(new byte[]{(byte) 0xa7});
        cmd.run();
        lm("Sent 0xa7 packet (x" + initButtonClicks + ")");
/*
        lm("(90 att + rf0n3min)");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendButtonDown();
                lm("ButtonDown");
            }
        }, 17 * 1000); // 17 seconds delay
*/
    }

    public void sendButtonDown() {
        RileyLinkCommand cmdButtonDown = new RileyLinkCommand(mDevice,mGattManager);
        cmdButtonDown.addWrite(pkt_pressdown);
        cmdButtonDown.run();
    }

    public void onPressDownButtonClick(View view) {
        if (mDeviceAddress == null) {
            return;
        }
        sendButtonDown();
        lm("(sent press-down packet)");
    }

    public void sendRFOn(){
        RileyLinkCommand cmdRFOn = new RileyLinkCommand(mDevice,mGattManager);
        cmdRFOn.addWrite(pkt_rfOn3min);
        cmdRFOn.run();
    }

    public void onRFOnClick(View view) {
        if (mDeviceAddress == null) return;
        sendRFOn();
        lm("(sent RF-On packet");
    }

    public void onPacketCountReadButtonClick(View view) {
        //setNotify();
        onCharaRead(GattAttributes.GLUCOSELINK_PACKET_COUNT, R.id.textView_PacketCountValue);
    }

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
        //bundle.addOperation(new GattCharacteristicWriteOperation(getDevice(),UUID.fromString(GattAttributes.GLUCOSELINK_SERVICE_UUID),
        //        UUID.fromString(GattAttributes.GLUCOSELINK_BUFFERCLEAR),new byte[] {0}));

        mGattManager.queue(bundle);

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
                msgList.add(0,s);
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
        lm("onResume Ready");
        RileyLinkUtil.test();

        if (getDevice()!=null) {
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
                        enableButton(R.id.button_connected,false);
                        return;
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        L.e("Connected to service!");
                        enableButton(R.id.button_connected, true);
                        lm("Connected -- discovering services");
                        setGatt(gatt);
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        L.e("Link disconnected");
                        enableButton(R.id.button_connected,false);
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
                                    setNotify();
                                    lm("setNotify()");

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
                                                i,perm,u.toString(),valueString);
                                        i++;
                                        Log.e("GGW",s);
                                    }
                                }
                            }
                        }
                    }
                    mRileyLink.setServiceUUIDs(serviceUUIDs);
                    L.e("Now has services: " + mRileyLink.getServiceUUIDs());
                    lm("Now has services: " + mRileyLink.getServiceUUIDs());

                    enableButton(R.id.button_services_found, true);


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
}
