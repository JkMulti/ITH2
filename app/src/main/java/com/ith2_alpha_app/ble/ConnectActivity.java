package com.ith2_alpha_app.ble;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.ith2_alpha_app.ble.databinding.ActivityConnectBinding;
import com.ith2_alpha_app.ble.worker.HourlyWorker;
import com.ith2_alpha_app.ble.worker.MyPreference;
import com.jaygoo.widget.OnRangeChangedListener;
import com.jaygoo.widget.RangeSeekBar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class ConnectActivity extends AppCompatActivity {

    private static final String TAG = ConnectActivity.class.getSimpleName();

    private BLEService mBleService;

    private boolean mConnected = false;

    private boolean mConnecting = false;

    private String mDeviceAddress;
    private String mDeviceName;

    private int skipIndex = 0;
    private int sleepProgress = 5;
    private int repeatProgress = 1;
    private int modeProgress = 5;
    private int freqProgress = 1;
    private String deviceData = "";

    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mBleService.connect(mDeviceAddress);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ConnectActivity.this, "Command Sent!", Toast.LENGTH_SHORT).show();
                    writeData(((System.currentTimeMillis() / 1000) + 3600) + "@");
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ConnectActivity.this, "Disconnecting after command...", Toast.LENGTH_SHORT).show();
                            mBleService.disconnect();
                            if (binding.repeater.isChecked()) {
                                enqueueWork();
                            }
                        }
                    }, 3000);
                }
            }, 5000);
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                skipIndex = 0;
                mConnected = true;
                mConnecting = false;
                updateConnectionState(R.string.connected);
                if (binding.repeater.isChecked()) {
                    MyPreference.Companion.newInstance(ConnectActivity.this).setAddress(mDeviceAddress);
                } else {
                    MyPreference.Companion.newInstance(ConnectActivity.this).setAddress("0");
                }
                enqueueWork();
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                skipIndex = 0;
                mConnected = false;
                mConnecting = false;
                updateConnectionState(R.string.disconnected);
                clearUI();
            } else if (BLEService.ACTION_GATT_CONNECTING.equals(action)) {
                mConnecting = true;
                mConnected = false;
                updateConnectionState(R.string.connecting);
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BLEService.EXTRA_DATA);
                //displayData(hexToAscii(data.replaceAll(" ", "")));
                displayData(data);
            }
        }
    };

    private static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBleService = ((BLEService.LocalBinder) iBinder).getService();
            if (!mBleService.initialize()) {
                finish();
            }
            Log.i(TAG, "Service Connected!");
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleService = null;
        }
    };

    private void displayData(String data) {
        Log.i(TAG, "Display Data");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.add(data);
                dataAdapter.notifyItemInserted(messages.size() - 1);
                binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });
    }

    private void updateConnectionState(int msgId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.notify.setText(getResources().getString(msgId) + " " + mDeviceName);
                if (getResources().getString(msgId).contains("Connected")) {
                    changeState(true);
                }
            }
        });
    }

    private void clearUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                changeState(false);
            }
        });
    }


    private ActivityConnectBinding binding;

    public Boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }

    }

    private ArrayList<String> messages;
    private DataAdapter dataAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_connect);

        messages = new ArrayList<>();
        Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(ScanActivity.EXTRA_ADDRESS);

        mDeviceName = intent.getStringExtra(ScanActivity.EXTRA_NAME);
        Log.i("BDA", "Address: " + mDeviceAddress);

        dataAdapter = new DataAdapter(this, messages);
        binding.recyclerView.setAdapter(dataAdapter);

        bindService(new Intent(this, BLEService.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        binding.notify.setText("Waiting for connection to " + mDeviceName);

        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBleService != null) {
                    writeData(binding.input.getText().toString());
                }
            }
        });

        binding.connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBleService != null) {
                    mBleService.connect(mDeviceAddress);
                }
            }
        });

        binding.disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBleService != null) {
                    mBleService.disconnect();
                }
            }
        });

        binding.command1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(getResources().getString(R.string.command_one));
            }
        });

        binding.command2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(((System.currentTimeMillis() / 1000) + 3600) + getResources().getString(R.string.command_two));
            }
        });

        binding.command3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData("?");
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mBleService != null) {
                            mBleService.disconnect();
                        }
                        if (binding.repeater.isChecked()) {
                            enqueueWork();
                        }
                    }
                }, 3000);
            }
        });

        binding.command4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(getResources().getString(R.string.command_four));
            }
        });

        binding.commandEraseMemory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(getResources().getString(R.string.command_EM));
            }
        });

        binding.commandReadMemory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(getResources().getString(R.string.command_RM));
            }
        });

        binding.clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                messages.clear();
                dataAdapter.notifyDataSetChanged();
            }
        });

        binding.sbSleep.setOnRangeChangedListener(new OnRangeChangedListener() {
            @Override
            public void onRangeChanged(RangeSeekBar view, float leftValue, float rightValue, boolean isFromUser) {
                Log.i("RSB", "LV: " + leftValue);
                Log.i("RSB", "RV: " + rightValue);
                binding.sleepInd.setText("" + (int) leftValue + "s");
                sleepProgress = (int) leftValue;
            }

            @Override
            public void onStartTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }

            @Override
            public void onStopTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }
        });

        binding.sbMin.setOnRangeChangedListener(new OnRangeChangedListener() {
            @Override
            public void onRangeChanged(RangeSeekBar view, float leftValue, float rightValue, boolean isFromUser) {
                Log.i("RSB", "LV: " + leftValue);
                Log.i("RSB", "RV: " + rightValue);
                binding.repeatInd.setText("" + (int) leftValue + "min");
                repeatProgress = (int) leftValue;
            }

            @Override
            public void onStartTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }

            @Override
            public void onStopTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }
        });

        binding.sbMode.setOnRangeChangedListener(new OnRangeChangedListener() {
            @Override
            public void onRangeChanged(RangeSeekBar view, float leftValue, float rightValue, boolean isFromUser) {
                Log.i("RSB", "LV: " + leftValue);
                Log.i("RSB", "RV: " + rightValue);
                binding.modeInd.setText("" + (int) leftValue + "s");
                modeProgress = (int) leftValue;
            }

            @Override
            public void onStartTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }

            @Override
            public void onStopTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }
        });

        binding.sbFreq.setOnRangeChangedListener(new OnRangeChangedListener() {
            @Override
            public void onRangeChanged(RangeSeekBar view, float leftValue, float rightValue, boolean isFromUser) {
                Log.i("RSB", "LV: " + leftValue);
                Log.i("RSB", "RV: " + rightValue);
                binding.freqInd.setText("" + (int) leftValue + "Hz");
                freqProgress = (int) leftValue;
            }

            @Override
            public void onStartTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }

            @Override
            public void onStopTrackingTouch(RangeSeekBar view, boolean isLeft) {

            }
        });

        binding.sleepSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(String.valueOf(sleepProgress) + "#");
            }
        });

        binding.modeSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(String.valueOf(modeProgress) + "^");
            }
        });

        binding.freqSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeData(String.valueOf(freqProgress) + "p");
            }
        });

        binding.save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateNote(ConnectActivity.this, "Log-" + System.currentTimeMillis() + ".txt", "Hello Dear");
            }
        });

        changeState(false);
        boolean isChecked = MyPreference.Companion.newInstance(this).getChecked();
        binding.repeater.setChecked(isChecked);
        binding.repeater.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    MyPreference.Companion.newInstance(ConnectActivity.this).setAddress(mDeviceAddress);
                } else {
                    handler.removeCallbacks(runnable);
                    MyPreference.Companion.newInstance(ConnectActivity.this).setAddress("0");
                }
                MyPreference.Companion.newInstance(ConnectActivity.this).setChecked(isChecked);
            }
        });
        if (isChecked) {
            //enqueueWork();
        }
    }


    public void enqueueWork() {
        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, (long) repeatProgress * 60* 1000);
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WorkManager.getInstance(this).cancelAllWorkByTag("HW1");
            PeriodicWorkRequest hourlyRequest = new PeriodicWorkRequest
                    .Builder(HourlyWorker.class, repeatProgress, TimeUnit.MINUTES,
                    repeatProgress/2, TimeUnit.MINUTES)
                    .addTag("HW1")
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("HW", ExistingPeriodicWorkPolicy.REPLACE, hourlyRequest);
        }*/
    }


    private void changeState(boolean isConnected) {
        if (isConnected) {
            binding.send.setClickable(true);
            binding.send.setEnabled(true);
            binding.disconnect.setClickable(true);
            binding.disconnect.setEnabled(true);
            binding.connect.setCheckable(false);
            binding.connect.setEnabled(false);
        } else {
            binding.send.setClickable(false);
            binding.send.setEnabled(false);
            binding.disconnect.setClickable(false);
            binding.disconnect.setEnabled(false);
            binding.connect.setCheckable(true);
            binding.connect.setEnabled(true);
        }
    }

    private void writeData(String text) {
        if (mBleService == null) {
            return;
        }
        mBleService.writeCharacteristic(text.getBytes(StandardCharsets.UTF_8));
    }

    private void testData() {
        for (int i = 0; i < 16; i++) {
            messages.add(String.valueOf(System.currentTimeMillis()));
            dataAdapter.notifyItemInserted(messages.size() - 1);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 51 && resultCode == RESULT_OK) {
            if (messages.isEmpty()) {
                Toast.makeText(this, "Log Is Empty", Toast.LENGTH_LONG).show();
            } else {
                Uri uri = data.getData();
                try {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < messages.size(); i++) {
                        builder.append(messages.get(i)).append("\n");
                    }
                    OutputStream stream = getContentResolver().openOutputStream(uri);
                    stream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                    stream.close();
                    Toast.makeText(this, "Log Saved", Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable);
        if (mGattUpdateReceiver != null) {
            unregisterReceiver(this.mGattUpdateReceiver);
        }
        if (mBleService.serviceStatus) {
            unbindService(this.mServiceConnection);
        }
        mBleService.close();
        mBleService = null;
    }

    public void generateNote(Context context, String sFileName, String sBody) {
        Intent txtIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        txtIntent.addCategory(Intent.CATEGORY_OPENABLE);
        txtIntent.setType("text/plain");
        txtIntent.putExtra(Intent.EXTRA_TITLE, sFileName);
        txtIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, "");
        startActivityForResult(txtIntent, 51);
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

}