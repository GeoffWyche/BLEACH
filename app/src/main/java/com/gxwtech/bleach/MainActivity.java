package com.gxwtech.bleach;

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
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gxwtech.RileyLink.RileyLink;
import com.gxwtech.droidbits.persist.PersistentBoolean;
import com.gxwtech.droidbits.persist.PersistentString;
import com.gxwtech.droidbits.persist.PreferenceBackedStorage;

import org.droidparts.activity.Activity;
import org.droidparts.adapter.widget.ArrayAdapter;
import org.droidparts.annotation.inject.InjectDependency;
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

public class MainActivity extends Activity {
    @InjectDependency
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
        p = getApplicationContext().getSharedPreferences(Constants.PREF_STORAGE_NAME, 0);
        mRLAddress = new PersistentString(p, "RileyLinkAddress", "(empty)");
        //mRLAddress.set("invalid");
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
        /*
        This is done in Application

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
        */
        boolean isGoodAddress = ((BluetoothManager) getSystemService(Context
                .BLUETOOTH_SERVICE)).getAdapter().checkBluetoothAddress(mRLAddress.get());
        isGoodAddress = false;


        if (!isGoodAddress) {
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
        if (mLeDeviceListAdapter != null) {
            mLeDeviceListAdapter.clear();
        }
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
