package jp.ac.titech.itpro.sdl.blescanner;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Created by kanato on 2016/07/13.
 */
public class GattList extends ArrayList<BluetoothGatt> {

    public boolean isConnected(BluetoothDevice device) {
        for(BluetoothGatt gatt : this) {
            if(gatt.getDevice().equals(device))
                return true;
        }
        return false;
    }

    public BluetoothGatt getFromDevice(BluetoothDevice device) throws NoSuchElementException{
        for(BluetoothGatt gatt : this) {
            if(gatt.getDevice().equals(device))
                return gatt;
        }
        throw new NoSuchElementException();
    }

    public BluetoothGatt getFromAddr(String addr) throws NoSuchElementException{
        for(BluetoothGatt gatt : this) {
            if(gatt.getDevice().getAddress().equals(addr))
                return gatt;
        }
        throw new NoSuchElementException();
    }

    public void disconnect(BluetoothDevice device) throws NoSuchElementException{

        if(!isConnected(device))
            throw new NoSuchElementException();

        BluetoothGatt gatt = getFromDevice(device);
        this.remove(gatt);
        gatt.disconnect();
    }
}
