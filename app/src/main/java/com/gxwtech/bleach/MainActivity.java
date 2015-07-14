package com.gxwtech.bleach;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gxwtech.RileyLink.RileyLink;
import com.gxwtech.droidbits.persist.PersistentBoolean;
import com.gxwtech.droidbits.persist.PersistentString;
import com.gxwtech.droidbits.persist.PreferenceBackedStorage;

import org.droidparts.concurrent.task.AsyncTaskResultListener;
import org.droidparts.concurrent.task.SimpleAsyncTask;
import org.droidparts.util.L;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.puckcentral.bluetooth.gatt.GattManager;

/*
  Flow:
  Check for BLE capability
  Check for BLE permission
  Check existing RileyLink address
    No: prompt to scan and select
  Present connect button
    Connect button opens control window
 */

public class MainActivity extends ActionBarActivity {
    GattManager mGattManager;
    //PreferenceBackedStorage mStorage;
    PersistentString mRLAddress;
    SharedPreferences p;
    RileyLink mRileyLink;
    //BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    ArrayAdapter<String> mLeDeviceListAdapter;
    boolean mScanning = false;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    List<String> mDevList = new ArrayList<>();
    //ArrayAdapter<String> adapter = null;


    Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();

        /*
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mDevList);
        ((ListView)findViewById(R.id.scan_list)).setAdapter(adapter);
*/
        p = getApplicationContext().getSharedPreferences(Constants.PREF_STORAGE_NAME, 0);
        mRLAddress = new PersistentString(p, "RileyLinkAddress", "(empty)");
        L.e("RileyLinkAddress is " + mRLAddress.get());


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    @Override
    protected void onResume() {
        super.onResume();
        L.e("CheckForBLE capability");
        CheckForBLECapability();
        //CheckForBLEPermission();
        L.e("CheckFor BLE enabled");
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!getAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (mRLAddress.get().equals(mRLAddress.getDefaultValue())) {
            L.e("Bad address, scan");
            // value is unusable.  Prompt to Scan.

            // Initializes list view adapter.

            mLeDeviceListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mDevList);
            ListView lv = (ListView)findViewById(R.id.scan_list);
            lv.setAdapter(mLeDeviceListAdapter);

            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final String deviceAddress = mLeDeviceListAdapter.getItem(position);
                    final BluetoothDevice device = getAdapter().getRemoteDevice(deviceAddress);
                    if (device == null) {
                        L.e("Device for address " + deviceAddress + " not found");
                        return;
                    }

                    // record address of device, so we don't have to scan again
                    mRLAddress.set(device.getAddress());

                    if (mScanning) {
                        getAdapter().stopLeScan(mLeScanCallback);
                        mScanning = false;
                    }
                    launchRileyLinkActivity(device, new RileyLink(device.getAddress()));
                }
            });

            scanLeDevice(true);

        } else {
            L.e("Good address: " + mRLAddress.get());
            // value is presumed usable
            BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context
                    .BLUETOOTH_SERVICE)).getAdapter();
            mRileyLink = new RileyLink(mRLAddress.get());
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mRileyLink.getAddress());
            L.e("Launch RileyLinkActivity");
            launchRileyLinkActivity(device, new RileyLink(mRLAddress.get()));
        }
    }

    public void onScanButtonClicked(View view) {
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    BluetoothAdapter getAdapter() {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context
                .BLUETOOTH_SERVICE)).getAdapter();
        return bluetoothAdapter;
    }

    public void launchRileyLinkActivity(BluetoothDevice device, RileyLink rileyLink) {

        final Intent intent = new Intent(this, RileyLinkTestActivity.class);
        intent.putExtra(RileyLinkTestActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(RileyLinkTestActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());

        device.connectGatt(MainActivity.this, true, new BluetoothGattCallback() {
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
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    L.e("Link disconnected");
                    gatt.close();
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
                }
                mRileyLink.setServiceUUIDs(serviceUUIDs);
                L.e("Now has services: " + mRileyLink.getServiceUUIDs());
                gatt.disconnect();
            }
        });


        startActivity(intent);

    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            L.e("scanLEDevice - start scanning");
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    getAdapter().stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            getAdapter().startLeScan(mLeScanCallback);
        } else {
            L.e("scanLEDevice - stop scanning");
            mScanning = false;
            getAdapter().stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }



    private class FetchRileyLinkServices extends SimpleAsyncTask<Void> {
        public RileyLink mRileyLink;
        public FetchRileyLinkServices(Context ctx, AsyncTaskResultListener<Void> resultListener, RileyLink rileyLink) {
            super(ctx, resultListener);
            mRileyLink = rileyLink;
        }

        @Override
        protected Void onExecute() throws Exception {
            BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context
                    .BLUETOOTH_SERVICE)).getAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mRileyLink.getAddress());
            L.e("Starting service discovery");
            device.connectGatt(MainActivity.this, true, new BluetoothGattCallback() {
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
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        L.e("Link disconnected");
                        gatt.close();
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
                    }
                    mRileyLink.setServiceUUIDs(serviceUUIDs);
                    L.e("Now has services: " + mRileyLink.getServiceUUIDs());
                    gatt.disconnect();
                }
            });
            return null;
        }
    }




    public void CheckForBLECapability() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    public void CheckForBLEPermission() {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(Context
                .BLUETOOTH_SERVICE)).getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is turned off on this device currently", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                Toast.makeText(this, "The android version of this device is not compatible with Bluetooth Low Energy", Toast.LENGTH_LONG).show();
                finish();
            }
        }

    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    L.e("onLeScan: device " + device.toString());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.add(device.getAddress().toString());
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

}
