package bluetooth.cw.com.bluetoothcontroler;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ControlerActivity extends Activity implements View.OnClickListener{

    private static final String TAG = "Controler_blue";
    private View searchView,controlView;
    private ListView deviceList;
    private Button searchBtn;

    private Button mConnectBtn;

    private ImageButton keyPower,keyBack,keyHome,keyVup,keyVdown,keyMenu;

    private ProgressBar mProgressBar;

    private RoundMenuView roundMenuView;

    private DevicesAdapter mDevicesAdapter;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;

    private static final int WHAT_START_SCAN = 1;
    private static final int WHAT_STOP_SCAN = 2;
    private static final int WHAT_STATE_CONNECTED = 3;
    private static final int WHAT_STATE_DISCONNECTED = 4;
    private static final int WHAT_REFRESH_RSSI = 5;
    private static final int WHAT_CAN_SEND_DATA = 6;
    private static final int WHAT_SEND_KEYEVENT = 7;

    /** 扫描超时时间 */
    final int STOP_SCAN_TIME = 15 * 1000;
    /** 连接等待时间 */
    final int CONNECT_WAIT_TIME = 10 * 1000;

    private boolean mInitStatue = false;

    private boolean mConnectStatus = false;


    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_START_SCAN:
                    mDevicesAdapter.add((BDevice) msg.obj);
                    break;
                case WHAT_STOP_SCAN:
                    stopScan();
                    break;
                case WHAT_STATE_CONNECTED:
                    mConnectStatus = true;
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    mDevicesAdapter.removeAll();
                    mDevicesAdapter.add(device, true);
                    getDeviceLoopRssi();
                    showConnectBtn(false);
                    setBtnEnable(mConnectStatus);
                    break;

                case WHAT_STATE_DISCONNECTED:
                    mConnectStatus = false;
                    BluetoothDevice disDevice = (BluetoothDevice) msg.obj;
                    mDevicesAdapter.refreshConnState(disDevice, getConnectState());
                    mDevicesAdapter.refreshRssi(disDevice.getAddress(), 0);
                    if (mBluetoothGatt != null)
                        mBluetoothGatt.close();
                    showConnectBtn(true);
                    setBtnEnable(mConnectStatus);
                    //refreshSendDataView(false);
                    //startAutoConnect();
                    break;

                case WHAT_REFRESH_RSSI:
                    BDevice bDevice = (BDevice) msg.obj;
                    mDevicesAdapter.refreshRssi(bDevice.mDevice.getAddress(), bDevice.rssi);
                    break;

                case WHAT_CAN_SEND_DATA:
                    setCharacteristicNotification(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID, true);
                    showSearchView(false);
                    break;
                case WHAT_SEND_KEYEVENT:
                    Log.i(TAG,"WHAT_SEND_KEYEVENT = "+msg.arg1 + "  arg2 = "+msg.arg2   );
                    writeCharacteristic(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID,msg.arg1+"");
                default:
                    break;
            }
        }


        private void getDeviceLoopRssi() {
            new Thread(new Runnable() {
                public void run() {
                    while (getConnectState() && mBluetoothGatt != null) {
                        mBluetoothGatt.readRemoteRssi();
                        SystemClock.sleep(2 * 1000);
                    }
                }
            }).start();
        };
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controler);
        getActionBar().setTitle("遥控器");
        initView();
        mInitStatue = initBluetooth();
        setBtnEnable(mConnectStatus);
    }

    @Override
    protected void onResume() {
        showConnectBtn(!getConnectState());
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        stopScan();
        //断开任何活动连接
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }

        mHandler.removeCallbacksAndMessages(null);
        if (mReceiver != null)
            unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void showSearchView(Boolean value){
        if(value){
            searchView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            controlView.setVisibility(View.INVISIBLE);
        }else {
            searchView.setVisibility(View.INVISIBLE);
            controlView.setVisibility(View.VISIBLE);
        }
    }

    private void showConnectBtn(boolean show){
        if(show){
            mConnectBtn.setVisibility(View.VISIBLE);
            roundMenuView.setVisibility(View.INVISIBLE);
            return;
        }
        mConnectBtn.setVisibility(View.INVISIBLE);
        roundMenuView.setVisibility(View.VISIBLE);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean initBluetooth(){
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothAdapter.setName("中央");

        // 注册经典蓝牙扫描的广播
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, intentFilter);
        // We need to enforce that Bluetooth is first enabled, and take the user to settings to enable it if they have not done so.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {//启用蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            //ToastUtils.showToast(this, "蓝牙不可用");
            //finish();
            return false;
        }

        // Check for Bluetooth LE Support.  In production, our manifest entry will keep this from installing on these devices, but this will allow test devices or other sideloads to report whether or not the feature exists.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ToastUtils.showToast(this, "No LE Support.");
            finish();
            return false;
        }

        //Check for advertising support. Not all devices are enabled to advertise Bluetooth LE data.
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            ToastUtils.showToast(this, "No Advertising Support.");
            finish();
            return false;
        }

        return true;

    }

    private void setBtnEnable(boolean value){

        keyBack.setEnabled(value);
        keyPower.setEnabled(value);
        keyHome.setEnabled(value);
        keyMenu.setEnabled(value);
        keyVdown.setEnabled(value);
        keyVup.setEnabled(value);
        //keyBack.setEnabled(value);

    }


    private void initView(){
        searchView = findViewById(R.id.search_view);
        controlView = findViewById(R.id.control_view);
        deviceList = findViewById(R.id.device_list);

        mConnectBtn  = findViewById(R.id.search_or_connect_btn);

        keyBack = findViewById(R.id.key_back);
        keyPower = findViewById(R.id.key_power);
        keyHome = findViewById(R.id.key_home);
        keyMenu = findViewById(R.id.key_menu);
        keyVdown = findViewById(R.id.key_volume_down);
        keyVup = findViewById(R.id.key_volume_up);
        searchBtn = findViewById(R.id.search_btn);

        mProgressBar = findViewById(R.id.scan_progress);

        roundMenuView = findViewById(R.id.roundView);
        initRoundView();


        searchBtn.setOnClickListener(this);
        keyVup.setOnClickListener(this);
        keyHome.setOnClickListener(this);
        keyVdown.setOnClickListener(this);
        keyMenu.setOnClickListener(this);
        keyPower.setOnClickListener(this);
        keyBack.setOnClickListener(this);
        mConnectBtn.setOnClickListener(this);


        mDevicesAdapter = new DevicesAdapter(getApplicationContext());
        deviceList.setAdapter(mDevicesAdapter);

        // 搜索后显示的设备点击事件
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BDevice item = mDevicesAdapter.getItem(position);
                connect(item.mDevice.getAddress(), getConnectState());
                // 更新界面
                mDevicesAdapter.refreshConnState(item.mDevice, getConnectState());
            }
        });

    }

    private void initRoundView(){
        RoundMenuView.RoundMenu roundMenu = new RoundMenuView.RoundMenu();
        roundMenu.selectSolidColor = Color.GRAY;
        roundMenu.strokeColor = Color.GRAY;;
        roundMenu.icon= BitmapFactory.decodeResource(getResources(),R.drawable.right_icon);
        roundMenu.onClickListener=new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message.obtain(mHandler,WHAT_SEND_KEYEVENT,20,0).sendToTarget();
                //ToastUtils.showToast(getApplication(),"点击了1");
            }
        };
        roundMenuView.addRoundMenu(roundMenu);

        roundMenu = new RoundMenuView.RoundMenu();
        roundMenu.selectSolidColor = Color.GRAY;
        roundMenu.strokeColor = Color.GRAY;
        roundMenu.icon=BitmapFactory.decodeResource(getResources(),R.drawable.right_icon);
        roundMenu.onClickListener=new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message.obtain(mHandler,WHAT_SEND_KEYEVENT,21,0).sendToTarget();
                //ToastUtils.showToast(getApplication(),"点击了2");
            }
        };
        roundMenuView.addRoundMenu(roundMenu);

        roundMenu = new RoundMenuView.RoundMenu();
        roundMenu.selectSolidColor = Color.GRAY;
        roundMenu.strokeColor = Color.GRAY;
        roundMenu.icon=BitmapFactory.decodeResource(getResources(),R.drawable.right_icon);
        roundMenu.onClickListener=new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message.obtain(mHandler,WHAT_SEND_KEYEVENT,19,0).sendToTarget();
                //ToastUtils.showToast(getApplicationContext(),"点击了3");
            }
        };
        roundMenuView.addRoundMenu(roundMenu);

        roundMenu = new RoundMenuView.RoundMenu();
        roundMenu.selectSolidColor = Color.GRAY;
        roundMenu.strokeColor = Color.GRAY;
        roundMenu.icon=BitmapFactory.decodeResource(getResources(),R.drawable.right_icon);
        roundMenu.onClickListener=new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message.obtain(mHandler,WHAT_SEND_KEYEVENT,22,0).sendToTarget();
                ToastUtils.showToast(getApplicationContext(),"点击了4");
            }
        };
        roundMenuView.addRoundMenu(roundMenu);

        roundMenuView.setCoreMenu(Color.GRAY,
                Color.GRAY, Color.GRAY
                , 1, 0.43,BitmapFactory.decodeResource(getResources(),R.drawable.icon_ok), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Message.obtain(mHandler,WHAT_SEND_KEYEVENT,23,0).sendToTarget();
                        //sToastUtils.showToast(getApplicationContext(),"点击了中心圆圈");
                    }
                });
    }

    private void startScanBLE() {
        Log.d(TAG, "开始扫描Ble...");
        mProgressBar.setVisibility(View.VISIBLE);
        if(!mInitStatue) return;
       // EventBus.getDefault().post(new CentralActivity.EventObj(CentralActivity.EventObj.Dialog, true));
        mDevicesAdapter.removeAll();
        mHandler.sendEmptyMessageDelayed(WHAT_STOP_SCAN, STOP_SCAN_TIME);// 20秒后停止扫描

        // 扫描我们的自定义的设备服务advertising
        ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Constants.SERVICE_UUID)).build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(/*filters, settings, */mScanCallback);
        //mBluetoothAdapter.getBluetoothLeScanner().startScan(filters,settings,mScanCallback);
    }

    private void startScanBT() {
        mDevicesAdapter.removeAll();
        mHandler.sendEmptyMessageDelayed(WHAT_STOP_SCAN, STOP_SCAN_TIME * 2);
        boolean startDiscovery = mBluetoothAdapter.startDiscovery();
        if (startDiscovery)
            Log.i(TAG, "开始扫描经典蓝牙。。。");
    }

    protected void stopScan() {
        //EventBus.getDefault().post(new CentralActivity.EventObj(CentralActivity.EventObj.Dialog, false));
        mHandler.removeCallbacksAndMessages(null);
        if (mScanCallback != null && mBluetoothAdapter.getBluetoothLeScanner() != null) {
            Log.i(TAG, "停止扫描BLE");
            mProgressBar.setVisibility(View.INVISIBLE);
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
        }

        if (mBluetoothAdapter.isDiscovering()) {
            Log.i(TAG, "停止扫描经典蓝牙");
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    private synchronized void connect(final String address, final boolean isDisconnect) {

        new Thread(new Runnable() { // 连接方法和断开方法要放在线程里面来执行，要不然很容易连不上，尤其是三星手机，note3和s5都已测过，
            public void run() { // 连上率30%左右，放在线程上执行有90%多
                BluetoothDevice bDevice = mBluetoothAdapter.getRemoteDevice(address);
                if (bDevice == null) {
                    Log.i(TAG, "BluetoothDevice is null");
                    return;
                }

                // 为了能进行下次的稳定连接必须的
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    SystemClock.sleep(1000);
                    if (mBluetoothGatt != null)
                        mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
                if (!isDisconnect) {
                    mBluetoothGatt = bDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                    boolean connect = mBluetoothGatt.connect();
                    Log.i(TAG, "开始连接...    address:" + bDevice.toString() + ",connect：" + connect);
                }
            }
        }).start();
    }

    private boolean getConnectState() {
        if (mBluetoothManager != null && mBluetoothGatt != null) {
            final List<BluetoothDevice> connectedDevices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
            return (connectedDevices.size() > 0) ? true : false;
        }
        return false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.find_menu,menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if(item.getItemId() == R.id.action_find){
            //do something
        }
        return super.onMenuItemSelected(featureId, item);
    }

    public void setCharacteristicNotification(UUID serviceUUID, UUID characteristicUUID, boolean enabled) {
        if (mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return;
        }
        try {
            BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);

            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

            BluetoothGattDescriptor descriptor = characteristic
                    .getDescriptor(Constants.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION);
            if (descriptor == null) {
                Log.i(TAG, "descriptor is null");
                return;
            }
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "e:" + e);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG, "onBatchScanResults: " + results.size() + " results");
            for (ScanResult result : results)
                processResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i(TAG, "LE 扫描失败: " + errorCode);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void processResult(ScanResult result) {
            BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(result.getDevice().getAddress());
            Log.i(TAG, "remoteDevice：" + result.getDevice().getName());
            String name = result.getDevice().getName();
            if( name != null && name.equals("CW P1")){
                mDevicesAdapter.add(result.getDevice(), result.getRssi());
            }

        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG,"resultcode == "+resultCode);
        if(requestCode == RESULT_OK){
            mInitStatue = true;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        //请求连接之后的回调， status:返回的状态成功与否， newState:提示新的状态
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            mBluetoothGatt = gatt;
            BluetoothDevice device = gatt.getDevice();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "已连接!!!" + device.getName() + ", " + device.getAddress());
                Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "已断开!!!");
                Message.obtain(mHandler, WHAT_STATE_DISCONNECTED, device).sendToTarget();
            }
        }

        //服务发现之后的回调
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "服务发现成功");
                Message.obtain(mHandler, WHAT_CAN_SEND_DATA).sendToTarget();
            }
        }

        //请求 characteristic读之后的回调
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicRead");
            final int charValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            Log.i(TAG, "charValue:" + charValue);
        }

        //请求characteristic写之后的回调
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        //characteristic属性改变之后的回调
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "onCharacteristicChanged，" + new String(characteristic.getValue()));
        }

        //类似characteristic
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.i(TAG, "onDescriptorRead");
        }

        //类似characteristic
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(TAG, "onDescriptorWrite");
        }

        //可靠写完成之后的回调
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.i(TAG, "onReliableWriteCompleted");
        }

        //读远端Rssi请求之后的回调
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.i(TAG, "onReadRemoteRssi:" + rssi);
            Message.obtain(mHandler, WHAT_REFRESH_RSSI, new BDevice(gatt.getDevice(), rssi)).sendToTarget();
        }

        //请求MTU改变之后的回调
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(TAG, "onMtuChanged");
        }
    };

    //发送数据给连接的蓝牙设备
    public synchronized boolean writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, String value) {
        if (mBluetoothGatt != null) {
            BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
            if (service == null) {
                Log.i(TAG, "service is null");
                return false;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
            characteristic.setValue(value.getBytes());
            return mBluetoothGatt.writeCharacteristic(characteristic);
        }
        return false;
    }



    /**
     * 监听经典蓝牙广播
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.i(TAG, "开始搜索");
                //EventBus.getDefault().post(new CentralActivity.EventObj(CentralActivity.EventObj.Dialog, true));

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "搜索结束");
               // EventBus.getDefault().post(new CentralActivity.EventObj(CentralActivity.EventObj.Dialog, false));

            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);// 信号强度

                Log.i(TAG, "搜索到蓝牙设备：" + device);
                Log.i(TAG, Thread.currentThread().getId() + "********   "
                        + device.getBluetoothClass().getDeviceClass());
                mDevicesAdapter.add(device, rssi);
               Message.obtain(mHandler, WHAT_START_SCAN, new BDevice(device, rssi)).sendToTarget();
               // int majorDeviceClass = device.getBluetoothClass().getMajorDeviceClass();
            }
        }
    };

    @Override
    public void onClick(View v) {
        int id = v.getId();
        int msg = -1;
        switch (id){
            case R.id.search_btn:
                //startScanBLE();
                startScanBT();
                break;
            case R.id.key_back:
                msg = 4;
                break;
            case R.id.key_home:
                msg = 3;
                break;
            case R.id.key_menu:
                msg = 82;
                break;
            case R.id.key_power:
                msg = 26;
                break;
            case R.id.key_volume_down:
                msg = 25;
                break;
            case R.id.key_volume_up:
                msg = 24;
                break;
            case R.id.search_or_connect_btn:
                showSearchView(true);
                startScanBT();
                return;
            default:
                break;
        }
        Log.i(TAG,"msg = "+ msg);
        Message.obtain(mHandler,WHAT_SEND_KEYEVENT,msg,0).sendToTarget();
    }
}
