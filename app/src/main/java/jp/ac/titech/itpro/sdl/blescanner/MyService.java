package jp.ac.titech.itpro.sdl.blescanner;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

public class MyService extends Service {
    private GattList gattList;
    private static String TAG = "MyService";
    private Timer mTimer;
    private int threshold;
    private Ringtone mRingtone;
    private BluetoothGatt mBlutoothGatt;
    private SharedPreferences mSharedPreferences;
    private Handler mHandler;
    private Uri uri;

    public MyService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        gattList = new GattList();
        mHandler = new Handler();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return 0;
    }

    @Override
    public void onDestroy() {
        gattList.clear();
    }

    //サービスに接続するためのBinder
    public class MyServiceLocalBinder extends Binder {
        //サービスの取得
        MyService getService() {
            return MyService.this;
        }
    }
    //Binderの生成
    private final IBinder mBinder = new MyServiceLocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        //Toast.makeText(this, "MyService#onBind"+ ": " + intent, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "onBind" + ": " + intent);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent){
        //Toast.makeText(this, "MyService#onRebind"+ ": " + intent, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "onRebind" + ": " + intent);
    }

    @Override
    public boolean onUnbind(Intent intent){
        //Toast.makeText(this, "MyService#onUnbind"+ ": " + intent, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "onUnbind" + ": " + intent);

        //onUnbindをreturn trueでoverrideすると次回バインド時にonRebildが呼ばれる
        return true;
    }

    public void addGatt(BluetoothGatt gatt) {
        gattList.add(gatt);
    }

    public GattList getGattList() {
        return this.gattList;
    }

    public void startReadRemoteRssi() {
        Log.d(TAG, "startReadRemoteRssi");
        mTimer = new Timer(false);
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
        }, 3000, 3000);
    }

    public void stopReadRemoteRssi() {
        mTimer.cancel();
    }

    /*private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("BluetoothGattCallback", "onConnectionStateChange: " + status + " -> " + newState);
            super.onConnectionStateChange(gatt, status, newState);
            BluetoothDevice device = gatt.getDevice();
            //final int tmp = devList.getDevInListIndex(device.getAddress());
            if (newState == BluetoothProfile.STATE_CONNECTED) {
            *//*    mHandler.post(new Runnable() {
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
*//*

                // Service検索の成否は mBluetoothGattCallback.onServiceDiscovered で受け取る
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stopReadRemoteRssi();
                //接続が切れたことを通知
                gattList.remove(gatt);
                startReadRemoteRssi();
                *//*mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        devListView.setItemChecked(tmp, false);
                        devListAdapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "disconnected", Toast.LENGTH_SHORT).show();
                    }
                });*//*

                Log.d(TAG,"disconnected");
                //gatt.close();
                //mBluetoothGatt = null;
                // GATT通信が切断された
                //mBluetoothGatt = null;
            }
        }


        @Override
        public void onReadRemoteRssi (BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            mBlutoothGatt = gatt;
            threshold = Integer.parseInt(mSharedPreferences.getString(mBlutoothGatt.getDevice().getAddress() + "." + "rssiListKey", "-80"));
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

                        if(uri != null) {
                            mRingtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
                            mRingtone.play();
                        }
                        //Toast.makeText(MainActivity.this, "RSSI: " + MainActivity.this.rssi, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            *//*str.append("Device: " + gatt.getDevice().getName());
            str.append("\n");
            str.append("  RSSI: " + rssi);
            str.append("\n");*//*
            Log.d(TAG, "onReadRemoteRssi: " + rssi);
        }

    };
*/
}
