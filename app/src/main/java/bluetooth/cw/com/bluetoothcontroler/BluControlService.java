package bluetooth.cw.com.bluetoothcontroler;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;

public class BluControlService extends Service {

    private static final String TAG = "BluControlService";
    private static final int WHAT_STATE_CONNECTED = 2;
    private static final int WHAT_STATE_DISCONNECTED = 3;
    private static final int WHAT_WRITE_REQUEST = 4;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mLeAdvertiser;
    private BluetoothGattServer mGattServer;

    protected String mConnDeviceAddress = null;

    Handler mHandler = new Handler() {
        private long currentTimeMillis = System.currentTimeMillis();

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_STATE_CONNECTED:
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    break;
                case WHAT_STATE_DISCONNECTED:

                    break;
                case WHAT_WRITE_REQUEST:
                    String value = (String) msg.obj;
                    Log.i(TAG, "onCharacteristicWriteRequest:" + new String(value));
                    invokeKeyEvent(Integer.parseInt(value));
                    break;
            }
        };
    };

    public BluControlService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
       return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initBluetooth();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private void initBluetooth() {
        // 初始化蓝牙
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mBluetoothAdapter.setName("周边 " + mBluetoothAdapter.getAddress());

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(mReceiver, intentFilter);

        // We need to enforce that Bluetooth is first enabled, and take the user to settings to enable it if they have not done so.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {//启用蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ToastUtils.showToast(this, "蓝牙不可用");
            return;
        }

        // Check for Bluetooth LE Support.  In production, our manifest entry will keep this from installing on these devices, but this will allow test devices or other sideloads to report whether or not the feature exists.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ToastUtils.showToast(this, "No LE Support.");
            return;
        }

        //Check for advertising support. Not all devices are enabled to advertise Bluetooth LE data.
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            ToastUtils.showToast(this, "No Advertising Support.");
            return;
        }
        startAdvertise();
    }

    public void startAdvertise() {
        if (mLeAdvertiser == null)
            return;

        mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        addServiceToGattServer();

        //AdvertisementData.Builder dataBuilder = new AdvertisementData.Builder();
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        dataBuilder.setIncludeDeviceName(true);// must need true,otherwise can not be discovered when central scan
        //dataBuilder.setIncludeTxPowerLevel(false); //necessity to fit in 31 byte advertisement
        //dataBuilder.setServiceUuids(serviceUuids);
        dataBuilder.addServiceUuid(new ParcelUuid(Constants.SERVICE_UUID));

        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        //settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        //settingsBuilder.setType(AdvertiseSettings.ADVERTISE_TYPE_CONNECTABLE);
        settingsBuilder.setConnectable(true);

        mLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), mAdvertiseCallback);
    }

    private void addServiceToGattServer() {
        BluetoothGattService mGattService = new BluetoothGattService(Constants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // alert level char.

        int property = BluetoothGattCharacteristic.PROPERTY_NOTIFY//
                | BluetoothGattCharacteristic.PROPERTY_INDICATE//
                | BluetoothGattCharacteristic.PROPERTY_READ//
                | BluetoothGattCharacteristic.PROPERTY_WRITE//
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                | BluetoothGattCharacteristic.PERMISSION_READ
                | BluetoothGattCharacteristic.PERMISSION_WRITE;

        BluetoothGattCharacteristic mCharacter = new BluetoothGattCharacteristic(//
                Constants.CHARACTERISTIC_UUID, //
                property//
                , BluetoothGattCharacteristic.PERMISSION_READ | //
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        mGattService.addCharacteristic(mCharacter);
        mGattServer.addService(mGattService);
    }

    /**
     * 监听经典蓝牙广播
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, device.getName() + " 已连接");
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, device.getName() + " 已断开");
            }
        }
    };

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(TAG, "AdvertiseCallbackImpl->onStartSuccess is being invoked!!!");
            Log.d(TAG, "SETTING MODE:" + settingsInEffect.getMode());
            Log.d(TAG, "SETTING TIMEOUT:" + settingsInEffect.getTimeout());
            Log.d(TAG, "SETTING TxPowerLevel:" + settingsInEffect.getTxPowerLevel());
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.d(TAG, "AdvertiseCallbackImpl->onStartFailure is being invoked!!!");
            Log.d(TAG, "errorCode is :" + errorCode);
        }
    };

    // 方法都是异步的方法，因此在回调中不要写大量的工作
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        /**
         * 当连接状态发生变化的时候的回调，比如连接和断开的时候都会回调参数说明
         * device 产生回调的远端设备引用
         * status 回调的时候的状态信息
         * newState 进入的新的状态的信息
         */
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            mConnDeviceAddress = device.getAddress();
            //sgetConnectState();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "已连接!" + device.getName() + ", " + device.getAddress());
                Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "已断开");
                Message.obtain(mHandler, WHAT_STATE_DISCONNECTED).sendToTarget();
            }
        }

        /**
         * 当有服务请求读 characteristic 的时候
         * device发请求的远端设备引用
         * requestId 请求Id
         * offset 偏移量
         * characteristic被请求的 characteristic
         */
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG, "onCharacteristicReadRequest");
        }

        /**
         * characteristic 需要写的 characteristic
         * preparedWrite 是不是需要保存到队列之后执行写入操作的
         * responseNeeded 需不需要返回数据的
         * value 需要写的数据
         */
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Message.obtain(mHandler, WHAT_WRITE_REQUEST, new String(value)).sendToTarget();;
        }

        /**
         * 类似characteristic的读
         */
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(TAG, "onDescriptorReadRequest");
        }

        /**
         * 类似characteristic的写
         */
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, "onDescriptorWriteRequest");
        }

        /**
         * 执行将保存到队列中的数据写入到characteristic的回调
         */
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(TAG, "onExecuteWrite");
        }

        /**
         * 会回调当有服务端的数据传送到客户端的时候
         */
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(TAG, "onNotificationSent");
        }

        /**
         * 服务添加之后的回调
         */
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG, "onServiceAdded");
        }

        /**
         * 请求MTU改变之后的回调
         */
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(TAG, "onMtuChanged");
        }

    };

    private void invokeKeyEvent(int keyCode) {
        Intent intent = new Intent();
        intent.setPackage("com.rgk.projector.stadisable");
        intent.setAction("com.rgk.projector.send.keyevent");
        intent.putExtra("keycode", keyCode);
        startService(intent);
    }
}
