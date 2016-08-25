package jp.ac.titech.itpro.sdl.blescanner;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationBuilderWithBuilderAccessor;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.RunnableFuture;

/**
 * Activity for scanning and displaying available BLE devices.
 */
public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private MyService mBoundService;
    private boolean mIsBound;

    private ProgressBar scanProgress;

    private final static String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mScanning = false;
    private Handler mHandler;
    private BluetoothGatt mBlutoothGatt;

    private final static int REQUEST_ENABLE_BT = 1111;
    private final static int REQCODE_PERMISSIONS = 2222;
    private final static String KEY_DEVLIST = "MainActivity.devList";
    private final static String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private BluetoothManager mBluetoothManager;
    private List<BluetoothGattService> serviceList;
    static private ListView devListView;
    private ArrayAdapter<BluetoothDevice> devListAdapter;
    static private ArrayList<BluetoothDevice> devList = null;

    static private GattList gattList = new GattList();
    private SharedPreferences mSharedPreferences;
    private int rssi;
    private int threshold = -80;
    private Uri uri;
    private Ringtone mRingtone;
    private boolean vibrate;

    StringBuilder str;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private static final int NOTIFY_ID = 0;
    private NotificationManager manager;



    public static final UUID ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    public static final UUID ALERT_WRITE_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    public static final UUID LINK_LOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");
    public static final UUID LINK_LOSS_WRITE_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    public static final UUID TXPOWER_SERVICE_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TXPOWER_READ_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");

    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        str = new StringBuilder();
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        //gattList = new GattList();
        scanProgress = (ProgressBar)findViewById(R.id.scan_progress);

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

       /* if (savedInstanceState != null)
            devList = savedInstanceState.getParcelableArrayList(KEY_DEVLIST);

        if (devList == null)
       */     devList = new DevList();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        /*String str = mSharedPreferences.getString("rssiListKey", "-80");
        uri = Uri.parse(mSharedPreferences.getString("notificationsRingtoneKey", ""));
        Log.d(TAG, "Rssi_list: " + str);
        threshold = Integer.parseInt(str);
        */
        devListAdapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_multiple_choice, devList) {
            @Override
            public View getView(int pos, View view, ViewGroup parent) {
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    view = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
                }
                BluetoothDevice device = getItem(pos);
                TextView nameView = (TextView) view.findViewById(android.R.id.text1);
                //TextView addrView = (TextView) view.findViewById(android.R.id.text2);
                nameView.setText(getString(R.string.format_dev_name, device.getName(),
                        device.getBondState() == BluetoothDevice.BOND_BONDED ? "*" : " "));
                //addrView.setText(device.getAddress());
                return view;
            }
        };


        devListView = (ListView) findViewById(R.id.dev_list);
        devListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        assert devListView != null;
        devListView.setAdapter(devListAdapter);
        devListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                stopScan();
                final BluetoothDevice device = devList.get(pos);
                if(mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT).contains(device)) {
                    gattList.disconnect(device);
                }else {
                    gattList.add(device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback));
                }
            }
        });

        for (BluetoothDevice device : mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
            ParcelUuid[] uuids = device.getUuids();
            String uuid = "";
            if (uuids != null) {
                for (ParcelUuid puuid : uuids) {
                    uuid += puuid.toString() + " ";
                }
            }
            String msg = "name=" + device.getName() + ", bondStatus="
                    + device.getBondState() + ", address="
                    + device.getAddress() + ", type" + device.getType()
                    + ", uuids=" + uuid;
            Log.d("BLEActivity", "BondedDevice" + msg);
            devListAdapter.add(device);
            devListView.smoothScrollToPosition(devListAdapter.getCount());
            devListView.setItemChecked(devList.indexOf(device), true);
            devListAdapter.notifyDataSetChanged();
        }

        //initialize Bluetooth Adapter
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

       /* Timer mTimer = new Timer(false);
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    for(BluetoothGatt gatt: gattList) {
                        gatt.readRemoteRssi();
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {

                }
            }
        }, 3000, 3000);*/

    }

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("BluetoothGattCallback", "onConnectionStateChange: " + status + " -> " + newState);
            super.onConnectionStateChange(gatt, status, newState);
            BluetoothDevice device = gatt.getDevice();
            final int tmp = getDevInListIndex(device.getAddress());
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        devListView.setItemChecked(tmp, true);
                        devListAdapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "connected", Toast.LENGTH_SHORT).show();
                    }
                });

                // GATT接続成功
                // Serviceを検索する
                gatt.discoverServices();


                // Service検索の成否は mBluetoothGattCallback.onServiceDiscovered で受け取る
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        devListView.setItemChecked(tmp, false);
                        devListAdapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "disconnected", Toast.LENGTH_SHORT).show();
                    }
                });
                doUnbindService();
                doBindService();
                Log.d(TAG,"disconnected");
                //gatt.close();
                //mBluetoothGatt = null;
                // GATT通信が切断された
                //mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            mBlutoothGatt = gatt;
            //alt = new AlertDialog.Builder(MainActivity.this);
            StringBuilder servicesStr = new StringBuilder();
            StringBuilder characteristicsStr = new StringBuilder();
            Log.d("BluetoothGattCallback", "onServicesDiscovered: " + status);
            super.onServicesDiscovered(gatt, status);
            serviceList = gatt.getServices();
            /*alt.setTitle(gatt.getDevice().getName().toString());
                    str.append(gatt.getDevice().getAddress().toString() + "\n\n");
*/
            for (BluetoothGattService s : serviceList) {
                servicesStr.append("Service: " + s.getUuid() + "\n");
                characteristicsStr.append("Service: " + s.getUuid() + "\n");
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    characteristicsStr.append("  Characteristic: " + c.getUuid() + "\n");
                }
                characteristicsStr.append("\n");
            }

            //SettingActivityに移動
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            //intent.putExtra("devName", device.getName());
            //intent.putExtra("devAddr", device.getAddress());
            intent.putExtra("device", gatt.getDevice());
            intent.putExtra("services", (CharSequence) servicesStr);
            intent.putExtra("characteristics", (CharSequence) characteristicsStr);
                /*BluetoothGatt[] g = new BluetoothGatt[1];
                g[0] = gatt;
                intent.putExtra("gatt", g);*/
            if(mIsBound) {
                doUnbindService();
            }
            doBindService();
            MainActivity.this.startActivity(intent);

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (TXPOWER_READ_UUID.equals(characteristic.getUuid())) {
                str.append("Device: " + gatt.getDevice().getName());
                str.append("\n");
                str.append("  TxPower: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0) + " dBm");
                str.append("\n");
                Log.d(TAG, "onCharacteristicRead Tx Power: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0));
            }
        }

        @Override
        public void onReadRemoteRssi (BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            MainActivity.this.rssi = rssi;
            mBlutoothGatt = gatt;
            threshold = Integer.parseInt(mSharedPreferences.getString(mBlutoothGatt.getDevice().getAddress() + "." + "rssiListKey", "-80"));
            vibrate = mSharedPreferences.getBoolean(mBlutoothGatt.getDevice().getAddress() + "." + "notificationsVibrateKey", true);
            if(status != BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "onReadRemoteRssi: " + "FAILURE");
                return;
            }
            if(rssi < threshold) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        uri = Uri.parse(mSharedPreferences.getString(mBlutoothGatt.getDevice().getAddress() + "." + "notificationsRingtoneKey", ""));
                        Notification.Builder notifB = new Notification.Builder(MainActivity.this)
                                .setContentTitle(mBlutoothGatt.getDevice().getName() + " " + mBlutoothGatt.getDevice().getAddress())
                                .setContentText("デバイスの距離との距離が離れました。")
                                .setWhen(System.currentTimeMillis())
                                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                                .setContentIntent(PendingIntent.getActivity(MainActivity.this, 0, new Intent(MainActivity.this, jp.ac.titech.itpro.sdl.blescanner.MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT));


                        if(uri != null) {
                            notifB.setSound(uri);
                            /*mRingtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
                            mRingtone.play();*/
                        }

                        if(vibrate){
                            notifB.setVibrate(new long[]{500, 500, 500, 1000});
                        }

                        manager.notify(NOTIFY_ID, notifB.build());
                        //Toast.makeText(MainActivity.this, "RSSI: " + MainActivity.this.rssi, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            str.append("Device: " + gatt.getDevice().getName());
            str.append("\n");
            str.append("  RSSI: " + rssi);
            str.append("\n");
            Log.d(TAG, "onReadRemoteRssi: " + rssi);
        }

    };

    /** BLE機器をスキャンした際のコールバック */
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            // スキャンできた端末の情報をログ出力
            ParcelUuid[] uuids = device.getUuids();
            String uuid = "";
            if (uuids != null) {
                for (ParcelUuid puuid : uuids) {
                    uuid += puuid.toString() + " ";
                }
            }
            String msg = "name=" + device.getName() + ", bondStatus="
                    + device.getBondState() + ", address="
                    + device.getAddress() + ", type" + device.getType()
                    + ", uuids=" + uuid;
            Log.d("BLEActivity", msg);

            //アドレスが重複しているデバイスは追加しない
            if(!devList.contains(device)){
                devListAdapter.add(device);
                devListView.setItemChecked(devList.indexOf(device), false);
                devListAdapter.notifyDataSetChanged();
                devListView.smoothScrollToPosition(devListAdapter.getCount());
            }
        }
    };

    /**
     * スキャン開始ボタンタップ時のコールバックメソッド
     */
    public void startScan() {
        Log.d(TAG, "startScan");
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQCODE_PERMISSIONS);
                return;
            }
        }

        devListAdapter.clear();
        mScanning = true;
        scanProgress.setIndeterminate(true);
        int count = 0;
        for (BluetoothDevice device : mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
            ParcelUuid[] uuids = device.getUuids();
            String uuid = "";
            if (uuids != null) {
                for (ParcelUuid puuid : uuids) {
                    uuid += puuid.toString() + " ";
                }
            }
            String msg = "name=" + device.getName() + ", bondStatus="
                    + device.getBondState() + ", address="
                    + device.getAddress() + ", type" + device.getType()
                    + ", uuids=" + uuid;
            Log.d("BLEActivity", "BondedDevice" + msg);
            devListAdapter.add(device);
            devListView.smoothScrollToPosition(devListAdapter.getCount());
            devListView.setItemChecked(devList.indexOf(device), true);
            devListAdapter.notifyDataSetChanged();
        }
        ArrayList<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
        ScanFilter scanFilter = new ScanFilter.Builder().build();
        scanFilters.add(scanFilter);
        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build();
        mBluetoothLeScanner.startScan(scanFilters, scanSettings, mLeScanCallback);
        invalidateOptionsMenu();
    }

    /**
     * スキャン停止ボタンタップ時のコールバックメソッド
     */
    public void stopScan() {
        // BLE機器のスキャンを停止します
        mScanning = false;
        scanProgress.setIndeterminate(false);
        mBluetoothLeScanner.stopScan(mLeScanCallback);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        if (mBluetoothAdapter != null && mScanning) {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        }
        else {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.menu_scan:
                startScan();
                return true;
            case R.id.menu_stop:
                stopScan();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mScanning) {
            stopScan();
        }
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        doUnbindService();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private BluetoothGattCharacteristic characteristic(BluetoothGatt gatt, UUID sid, UUID cid) {
        /*if (!mBluetoothGatt.isConnected()) {
            return null;
        }*/
        BluetoothGattService s = gatt.getService(sid);
        if (s == null) {
            Log.w(TAG, "Service NOT found :" + sid.toString());
            return null;
        }
        BluetoothGattCharacteristic c = s.getCharacteristic(cid);
        if (c == null) {
            Log.w(TAG, "Characteristic NOT found :" + cid.toString());
            return null;
        }
        return c;
    }

    public void onClickAlertButton(View view) {
        byte level = 2;
        for(BluetoothGatt gatt: gattList) {
            BluetoothGattCharacteristic c = characteristic(gatt, ALERT_SERVICE_UUID, ALERT_WRITE_UUID);
            if(c != null)
                c.setValue(new byte[] { level });
            if(gatt != null)
                gatt.writeCharacteristic(c);
        }

        Log.w(TAG, "Alert:" + level);
    }
    public void onClickLinkLossButton(View view) {
        byte level = 2;

        for(BluetoothGatt gatt: gattList) {
            BluetoothGattCharacteristic c = characteristic(gatt, LINK_LOSS_SERVICE_UUID, LINK_LOSS_WRITE_UUID);
            if(c != null)
                c.setValue(new byte[] { level });
            if(gatt != null)
                gatt.writeCharacteristic(c);
        }

        Log.w(TAG, "Link Loss:" + level);
    }

    public void onClickTxPowerButton(View view) {
        str = new StringBuilder();
        for(BluetoothGatt gatt: gattList) {
            BluetoothGattCharacteristic c = characteristic(gatt, TXPOWER_SERVICE_UUID, TXPOWER_READ_UUID);
            gatt.readCharacteristic(c);
        }
        try {
            Thread.sleep(500);
        }catch (InterruptedException e){
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("TxPower")
                .setMessage(str)
                .setPositiveButton("Ok", null)
                .show();
    }

    public void onClickReadRssiButton(View vies) {
        str = new StringBuilder();
        for(BluetoothGatt gatt: gattList)
            gatt.readRemoteRssi();
        try {
            Thread.sleep(500);
        }catch (InterruptedException e){
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("RSSI")
                .setMessage(str)
                .setPositiveButton("Ok", null)
                .show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        Log.d(TAG, "onSharedPreferenceChanged: " + key);
        String[] str = key.split("\\.", 2);
        String addr = str[0];

        switch (str[1]) {
            case "stateSwitchKey":
                Log.d(TAG, "onSharedPreferenceChanged: " + "stateSwitchKey");
                Boolean state = sharedPreferences.getBoolean(key, false);
                if(state) {
                    gattList.add(getFromAddr(addr).connectGatt(MainActivity.this, false ,mBluetoothGattCallback));
                } else {
                    BluetoothGatt gatt = gattList.getFromAddr(addr);
                    gattList.remove(gatt);
                    gatt.disconnect();
                }
                break;

            /*case "notificationsRingtoneKey":
                uri = Uri.parse(sharedPreferences.getString(key, ""));
                Log.d(TAG, "onSharedPreferenceChanged: " + key + ": " + uri);
                break;

            case "rssiListKey":
                threshold = Integer.parseInt(sharedPreferences.getString(key, "-80"));
                Log.d(TAG, "onSharedPreferenceChanged: " + key + ": " + threshold);
                break;*/
        }

    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {

            // サービスとの接続確立時に呼び出される
            /*Toast.makeText(MainActivity.this, "Activity:onServiceConnected",
                    Toast.LENGTH_SHORT).show();
*/
            // サービスにはIBinder経由で#getService()してダイレクトにアクセス可能
            mBoundService = ((MyService.MyServiceLocalBinder)service).getService();

            //必要であればmBoundServiceを使ってバインドしたサービスへの制御を行う
            mBoundService.getGattList().clear();
            mBoundService.getGattList().addAll(gattList);
            mBoundService.startReadRemoteRssi();
        }

        public void onServiceDisconnected(ComponentName className) {
            // サービスとの切断(異常系処理)
            // プロセスのクラッシュなど意図しないサービスの切断が発生した場合に呼ばれる。
            mBoundService = null;
  /*          Toast.makeText(MainActivity.this, "Activity:onServiceDisconnected",
                    Toast.LENGTH_SHORT).show();
  */      }
    };

    void doBindService() {
        //サービスとの接続を確立する。明示的にServiceを指定
        //(特定のサービスを指定する必要がある。他のアプリケーションから知ることができない = ローカルサービス)
        bindService(new Intent(MainActivity.this,
                MyService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // コネクションの解除
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public boolean isDevInList(String addr){
        for(BluetoothDevice dev: devList){
            if(dev.getAddress().equals(addr))
                return true;
        }
        return false;
    }
    public int getDevInListIndex(String addr){
        int count = 0;
        for(BluetoothDevice dev: devList){
            if(dev.getAddress().equals(addr))
                return count;
            count++;
        }
        return -1;
    }

    public BluetoothDevice getFromAddr(String addr) throws NoSuchElementException {
        if(isDevInList(addr))
            return devList.get(getDevInListIndex(addr));

        throw new NoSuchElementException();
    }
}