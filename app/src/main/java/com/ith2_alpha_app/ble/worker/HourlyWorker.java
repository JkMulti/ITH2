package com.ith2_alpha_app.ble.worker;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ith2_alpha_app.ble.BLEService;

import java.nio.charset.StandardCharsets;

public class HourlyWorker extends Worker {

    private BLEService mBleService;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        writeData(((System.currentTimeMillis() / 1000) + 3600) + "@");
                        getApplicationContext().unregisterReceiver(mGattUpdateReceiver);
                        if (mBleService.serviceStatus) {
                            getApplicationContext().unbindService(mServiceConnection);
                        }
                        mBleService.close();
                        mBleService = null;

                    }
                }, 1000);
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {

            } else if (BLEService.ACTION_GATT_CONNECTING.equals(action)) {

            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };

    private void writeData(String text) {
        if (mBleService == null) {
            return;
        }
        mBleService.writeCharacteristic(text.getBytes(StandardCharsets.UTF_8));
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTING);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

            mBleService = ((BLEService.LocalBinder) iBinder).getService();
            if (!mBleService.initialize()) {
                Log.i("HWM", "Service Not Connected!");
                return;
            }

            mBleService.connectBackground(MyPreference.Companion.newInstance(getApplicationContext()).getAddress());
            Log.i("HWM", "Service Connected!");

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleService = null;
        }
    };

    public HourlyWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {

        if (MyPreference.Companion.newInstance(getApplicationContext()).getAddress().equals("0")) {
            return Result.success();
        }

        // Do the work here--in this case, upload the images.

        getApplicationContext().bindService(new Intent(getApplicationContext(), BLEService.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE);

        // getApplicationContext().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        // Indicate whether the work finished successfully with the Result
        return Result.success();
    }
}
