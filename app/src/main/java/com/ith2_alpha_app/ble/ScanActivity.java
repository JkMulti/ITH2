package com.ith2_alpha_app.ble;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOCATION = 1;
    private static final long SCAN_PERIOD = 30000;
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRAS_CHARACTERISTIC_INDEX = "extras_characteristic_index";

    private BluetoothLeScanner mBLEScanner;

    private BluetoothAdapter mBluetoothAdapter;

    private boolean mBluetoothStatus = false;
    private BluetoothGatt mGatt;

    private List<ScanFilter> filters;

    private Handler mHandler;
    private Runnable mRunnable;

    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    private ScanCallback mScanCallback;

    private boolean mScanning;

    private ScanSettings settings;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                switch (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE)) {
                    case 10:
                        mBluetoothStatus = true;
                        new AlertDialog.Builder(ScanActivity.this).setTitle(R.string.app_name)
                                .setMessage(R.string.error_bluetooth_off)
                                .setIcon(R.mipmap.ic_launcher)
                                .setCancelable(false)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        startActivityForResult(new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE"), 1);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        finish();
                                    }
                                });
                        return;

                    default:
                        return;
                }
            }
        }
    };

    private ListView deviceList;
    private ArrayAdapter<String> bondAdapter;
    private ArrayList<String> bondList;
    private ArrayList<String> bondName;

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return false;
        } else {
            return true;
        }
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        bondList = new ArrayList<>();
        bondName = new ArrayList<>();

        checkLocationPermission();

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
            finish();
        }
        registerReceiver(bluetoothReceiver, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
        mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_LONG).show();
            finish();
        }

        deviceList = findViewById(R.id.listView);

        bondAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, bondList);
        deviceList.setAdapter(bondAdapter);
        deviceList.setOnItemClickListener(myClickListener);

    }

    private AdapterView.OnItemClickListener myClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);
            String name = ((TextView) v).getText().toString().replaceAll(address,"");
            Intent i = new Intent(ScanActivity.this, ConnectActivity.class);
            i.putExtra(EXTRA_ADDRESS, address);
            i.putExtra(EXTRA_NAME, name.replaceAll("\n",""));
            i.putExtra(EXTRAS_CHARACTERISTIC_INDEX, bondList.indexOf(info));
            startActivity(i);
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Request location updates:
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setLECallbacks();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build();
                ScanFilter filter = new ScanFilter.Builder().setDeviceName((String) null).build();
                filters = new ArrayList<ScanFilter>();
                filters.add(filter);
            }
            scanLeDevice(true);
        } else {
            assert mBluetoothAdapter != null;
            if (!mBluetoothAdapter.isEnabled() && !mBluetoothStatus) {
                startActivityForResult(new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE"), 1);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter.isEnabled()) {
            if (!(mHandler == null || mRunnable == null)) {
                this.mHandler.removeCallbacks(this.mRunnable);
            }
            scanLeDevice(false);
            bondList.clear();
            bondAdapter.notifyDataSetChanged();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
    }

    private void setLECallbacks() {
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice bt = result.getDevice();
                String dDetail;
                dDetail = bt.getName() + "\n" + bt.getAddress();

                if (bt.getName() == null || "null".equals(bt.getName())) {
                    dDetail = "Unknown" + "\n" + bt.getAddress();
                }

                Log.i("BDA", "Address: "+ bt.getAddress());
                if (!bondList.contains(dDetail)) {
                    bondList.add(dDetail);
                    if (bt.getName() == null || "null".equals(bt.getName())) {
                        bondName.add("Unknown");
                    } else {
                        bondName.add(bt.getName());
                    }
                    bondAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };
    }

    private void scanLeDevice(boolean enable) {
        if (enable) {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    if (mBluetoothAdapter.isEnabled()) {
                        mBLEScanner.stopScan(mScanCallback);
                    }
                }
            };
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);
            mScanning = true;
            mBLEScanner.startScan(filters, settings, mScanCallback);
        } else {
            mScanning = false;
            if (mBluetoothAdapter.isEnabled()) {
                mBLEScanner.stopScan(mScanCallback);
            }
        }
    }
}