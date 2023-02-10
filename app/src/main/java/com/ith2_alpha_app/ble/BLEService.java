package com.ith2_alpha_app.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service {

    // UUIDs for UART service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455");
    public static UUID TX_UUID   = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616");
    public static UUID RX_UUID   = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3");
    public static UUID DX_UUID   = UUID.fromString("49535343-4c8a-39b3-2f49-511cff073b7e");

    // UUID for the UART BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Device Information service and associated characeristics.
    public static UUID DIS_UUID       = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_MANUF_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_MODEL_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_HWREV_UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_SWREV_UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");

    public static final String ACTION_DATA_AVAILABLE = "com.ITH2_alpha_app.ble.ACTION_DATA_AVAILABLE";
    public static final String ACTION_GATT_CONNECTED = "com.ITH2_alpha_app.ble.ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_CONNECTING = "com.ITH2_alpha_app.ble.ACTION_GATT_CONNECTING";
    public static final String ACTION_GATT_DISCONNECTED = "com.ITH2_alpha_app.ble.ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_SERVICES_DISCOVERED = "com.ITH2_alpha_app.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public static final String EXTRA_DATA = "com.ITH2_alpha_app.ble.EXTRA_DATA";

    private static final int STATE_CONNECTED = 2;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_DISCONNECTED = 0;

    private static final String TAG = BLEService.class.getSimpleName();
    private final IBinder mBinder = new LocalBinder();
    private BluetoothAdapter mBluetoothAdapter;

    private String mBluetoothDeviceAddress;

    private BluetoothGatt mBluetoothGatt;
    private BluetoothManager mBluetoothManager;

    private int mConnectionState = 0;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status != 0) {
                mHandler = new Handler(Looper.getMainLooper());
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connect(mBluetoothDeviceAddress);
                    }
                }, 1000);
            }
            if (newState == BLEService.STATE_CONNECTED) {
                if (status == 0) {
                    mConnectionState = BLEService.STATE_CONNECTED;
                    broadcastUpdate(BLEService.ACTION_GATT_CONNECTED);
                    mBluetoothGatt.discoverServices();
                }
            } else if (newState == 0) {
                mConnectionState = 0;
                broadcastUpdate(BLEService.ACTION_GATT_DISCONNECTED);
            } else if (newState == 1) {
                mConnectionState = 1;
                broadcastUpdate(BLEService.ACTION_GATT_CONNECTING);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == 0) {
                broadcastUpdate(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
                setCharacteristicNotification(gatt.getService(UART_UUID).getCharacteristic(TX_UUID), true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == 0) {
                broadcastUpdate(BLEService.ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            broadcastUpdate(BLEService.ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }

        @Override
        public void onServiceChanged(@NonNull BluetoothGatt gatt) {
            super.onServiceChanged(gatt);
        }
    };



    private Handler mHandler = null;
    public boolean serviceStatus = false;


    private void broadcastUpdate(String action) {
        sendBroadcast(new Intent(action));
    }


    private void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic) {
        Intent intent = new Intent(action);
        byte[] data = characteristic.getValue();
        /*if (data != null && data.length > 0) {
            StringBuilder stringBuilder = new StringBuilder(data.length);
            int length = data.length;
            for (int i = 0; i < length; i++) {
                stringBuilder.append(String.format("%02X ", new Object[]{Byte.valueOf(data[i])}));
            }
            if (BLEGattAttributes.lookup(characteristic.getService().getUuid().toString(), "Unknown Service").matches("Device Information")) {
                intent.putExtra(EXTRA_DATA, stringBuilder.toString() + "\n" + new String(data));
            } else if (BLEGattAttributes.lookup(characteristic.getService().getUuid().toString(), "Unknown Service").matches("Battery")) {
                intent.putExtra(EXTRA_DATA, stringBuilder.toString() + "\n" + Integer.valueOf(stringBuilder.toString().trim(), 16) + "%");
            } else {
                intent.putExtra(EXTRA_DATA, stringBuilder.toString());
            }
        }*/
        String s = new String(data, StandardCharsets.UTF_8);
        intent.putExtra(EXTRA_DATA, s);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public LocalBinder() {
        }

        public BLEService getService() {
            return BLEService.this;
        }
    }

    public IBinder onBind(Intent intent) {
        serviceStatus = true;
        return mBinder;
    }

    public boolean onUnbind(Intent intent) {
        close();
        if (mHandler != null) {
            mHandler.removeCallbacks((Runnable) null);
        }
        serviceStatus = false;
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        return true;
    }

    public boolean connect(String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        mGattCallback.onConnectionStateChange(mBluetoothGatt, 0, 1);
        if (!address.equals(mBluetoothDeviceAddress) || mBluetoothGatt == null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                return false;
            }
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
            mBluetoothDeviceAddress = address;
            mConnectionState = 1;
            return true;
        } else if (!mBluetoothGatt.connect()) {
            return false;
        } else {
            mConnectionState = 1;
            return true;
        }
    }

    public boolean connectBackground(String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        mGattCallback.onConnectionStateChange(mBluetoothGatt, 0, 1);
        if (!address.equals(mBluetoothDeviceAddress) || mBluetoothGatt == null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                return false;
            }
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
            mBluetoothDeviceAddress = address;
            mConnectionState = 1;
            return true;
        } else if (!mBluetoothGatt.connect()) {
            return false;
        } else {
            mConnectionState = 1;
            writeData(((System.currentTimeMillis() / 1000) + 3600) + "@");
            disconnect();
            return true;
        }
    }

    private void writeData(String text) {
        this.writeCharacteristic(text.getBytes(StandardCharsets.UTF_8));
    }

    public void disconnect() {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    public String getDeviceAddress() {
        return mBluetoothDeviceAddress;
    }

    public void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            characteristic.setValue(value);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    public void readCharacteristic() {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            BluetoothGattCharacteristic characteristic = mBluetoothGatt.getService(UART_UUID).getCharacteristic(RX_UUID);
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    public void writeCharacteristic(byte[] value) {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            BluetoothGattCharacteristic characteristic = mBluetoothGatt.getService(UART_UUID).getCharacteristic(TX_UUID);
            characteristic.setValue(value);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }


    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter != null && mBluetoothGatt != null) {
            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) {
            return null;
        }
        return mBluetoothGatt.getServices();
    }
}
