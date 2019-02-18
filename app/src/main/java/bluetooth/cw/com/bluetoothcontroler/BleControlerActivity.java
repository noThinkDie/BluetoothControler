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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.inuker.bluetooth.library.connect.listener.BleConnectStatusListener;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.response.BleConnectResponse;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.search.SearchRequest;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.BluetoothUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.inuker.bluetooth.library.Constants.REQUEST_SUCCESS;
import static com.inuker.bluetooth.library.Constants.STATUS_CONNECTED;

public class BleControlerActivity extends Activity implements View.OnClickListener,DeviceListAdapter.DeviceItermClickListener{

    private static final String TAG = "Controler_blue";
    private View searchView,controlView;
    private ListView deviceList;
    private Button searchBtn;

    DeviceListAdapter adapter;
    private List<SearchResult> mDevices;

    private Button mConnectBtn;

    private ImageButton keyPower,keyBack,keyHome,keyVup,keyVdown,keyMenu;

    private ProgressBar mProgressBar;

    private RoundMenuView roundMenuView;

    private DevicesAdapter mDevicesAdapter;

    private BluetoothDevice mPairDevice;



    private static final int WHAT_START_SCAN = 1;
    private static final int WHAT_STOP_SCAN = 2;
    private static final int WHAT_STATE_CONNECTED = 3;
    private static final int WHAT_STATE_DISCONNECTED = 4;
    private static final int WHAT_REFRESH_RSSI = 5;
    private static final int WHAT_CAN_SEND_DATA = 6;
    private static final int WHAT_SEND_KEYEVENT = 7;


    private boolean mConnectStatus;


    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_START_SCAN:
                    mDevicesAdapter.add((BDevice) msg.obj);
                    break;
                case WHAT_STOP_SCAN:
                    //stopScan();
                    break;
                case WHAT_STATE_CONNECTED:
                    showConnectBtn(false);
                    showSearchView(false);
                    setBtnEnable(mConnectStatus);
                    break;

                case WHAT_STATE_DISCONNECTED:
                    showConnectBtn(true);
                    setBtnEnable(mConnectStatus);

                    break;

                case WHAT_REFRESH_RSSI:

                    break;

                case WHAT_CAN_SEND_DATA:
                    showSearchView(false);
                    break;
                case WHAT_SEND_KEYEVENT:
                    Log.i(TAG,"WHAT_SEND_KEYEVENT = "+msg.arg1 + "  arg2 = "+msg.arg2   );
                    ClientManager.getClient().write(mPairDevice.getAddress(), Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID,
                            (msg.arg1+"").getBytes(), mWriteRsp);
                default:
                    break;
            }
        }

    };

    private final BleWriteResponse mWriteRsp = new BleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
               Log.i(TAG,"REQUEST_SUCCESS");
            } else {
                Log.i(TAG,"REQUEST_FAIL");
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controler);
        getActionBar().setTitle("遥控器");
        adapter = new DeviceListAdapter(this);
        mDevices = new ArrayList<SearchResult>();
        initView();
        setBtnEnable(mConnectStatus);
    }


    @Override
    protected void onResume() {
        showConnectBtn(true);
        super.onResume();
    }

    @Override
    protected void onDestroy() {

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
        adapter.setListener(this);
        deviceList.setAdapter(adapter);

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
                ToastUtils.showToast(getApplication(),"点击了1");
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
                ToastUtils.showToast(getApplication(),"点击了2");
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
                ToastUtils.showToast(getApplicationContext(),"点击了3");
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
                //ToastUtils.showToast(getApplicationContext(),"点击了4");
            }
        };
        roundMenuView.addRoundMenu(roundMenu);

        roundMenuView.setCoreMenu(Color.GRAY,
                Color.GRAY, Color.GRAY
                , 1, 0.43,BitmapFactory.decodeResource(getResources(),R.drawable.icon_ok), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Message.obtain(mHandler,WHAT_SEND_KEYEVENT,23,0).sendToTarget();
                        ToastUtils.showToast(getApplicationContext(),"点击了中心圆圈");
                    }
                });
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





    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG,"resultcode == "+resultCode);
        if(requestCode == RESULT_OK){

        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    @Override
    public void onClick(View v) {
        int id = v.getId();
        int msg = -1;
        switch (id){
            case R.id.search_btn:
                searchDevice();
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
                searchDevice();
                return;
            default:
                break;
        }
        Log.i(TAG,"msg = "+ msg);
        Message.obtain(mHandler,WHAT_SEND_KEYEVENT,msg,0).sendToTarget();
    }

    private void searchDevice() {
        SearchRequest request = new SearchRequest.Builder()
                .searchBluetoothLeDevice(5000, 2).build();

        ClientManager.getClient().search(request, mSearchResponse);
        if(mPairDevice != null){
            ClientManager.getClient().unregisterConnectStatusListener(mPairDevice.getAddress(),mConnectStatusListener);
        }
    }

    private final SearchResponse mSearchResponse = new SearchResponse() {
        @Override
        public void onSearchStarted() {
            BluetoothLog.w("MainActivity.onSearchStarted");
            mProgressBar.setVisibility(View.VISIBLE);
            mDevices.clear();
        }

        @Override
        public void onDeviceFounded(SearchResult device) {
//            BluetoothLog.w("MainActivity.onDeviceFounded " + device.device.getAddress());
            if (!mDevices.contains(device)) {
                mDevices.add(device);
                adapter.setDataList(mDevices);
            }

        }

        @Override
        public void onSearchStopped() {
            mProgressBar.setVisibility(View.INVISIBLE);
            BluetoothLog.w("MainActivity.onSearchStopped");
        }

        @Override
        public void onSearchCanceled() {
            BluetoothLog.w("MainActivity.onSearchCanceled");

        }
    };

    @Override
    public void onDeviceItemClick(int postion, SearchResult device) {
        mPairDevice = BluetoothUtils.getRemoteDevice(device.getAddress());
        ClientManager.getClient().registerConnectStatusListener(mPairDevice.getAddress(), mConnectStatusListener);
        connectDeviceIfNeeded();
    }

    private final BleConnectStatusListener mConnectStatusListener = new BleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            BluetoothLog.v(String.format("DeviceDetailActivity onConnectStatusChanged %d in %s",
                    status, Thread.currentThread().getName()));

            mConnectStatus = (status == STATUS_CONNECTED);
            if(mConnectStatus){
                Message.obtain(mHandler,WHAT_STATE_CONNECTED,null).sendToTarget();
            }else {
                Message.obtain(mHandler,WHAT_STATE_DISCONNECTED,null).sendToTarget();
            }
            connectDeviceIfNeeded();
        }
    };


    private void connectDevice() {

        BleConnectOptions options = new BleConnectOptions.Builder()
                .setConnectRetry(3)
                .setConnectTimeout(20000)
                .setServiceDiscoverRetry(3)
                .setServiceDiscoverTimeout(10000)
                .build();

        ClientManager.getClient().connect(mPairDevice.getAddress(), options, new BleConnectResponse() {
            @Override
            public void onResponse(int code, BleGattProfile profile) {
                Log.i(TAG," connect response code = "+ code);

                if (code == REQUEST_SUCCESS) {
                    Log.i(TAG,"connect ok");

                    //mAdapter.setGattProfile(profile);
                }
            }

        });
    }

    private void connectDeviceIfNeeded() {
        if (!mConnectStatus) {
            connectDevice();
        }
    }

}
