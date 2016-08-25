package jp.ac.titech.itpro.sdl.blescanner;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Created by kanato on 2016/07/14.
 */
public class DevList extends ArrayList<BluetoothDevice>  {

    public boolean isDevInList(String addr){
        for(BluetoothDevice dev: this){
            if(dev.getAddress().equals(addr))
                return true;
        }
        return false;
    }
    public int getDevInListIndex(String addr){
        int count = 0;
        for(BluetoothDevice dev: this){
            if(dev.getAddress().equals(addr))
                return count;
            count++;
        }
        return -1;
    }

    public BluetoothDevice getFromAddr(String addr) throws NoSuchElementException {
        if(isDevInList(addr))
            return this.get(getDevInListIndex(addr));

        throw new NoSuchElementException();
    }
}
