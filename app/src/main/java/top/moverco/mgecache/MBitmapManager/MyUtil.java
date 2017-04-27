package top.moverco.mgecache.MBitmapManager;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by liuzongxiang on 27/04/2017.
 */

final public class MyUtil {
    public static final String TAG = "MYUTIL";
    public static void close(Closeable closeable){
        if (closeable!=null){
            try {
                closeable.close();
            } catch (IOException e) {
                Log.d(TAG,"closeable is not close:"+ closeable.toString());
            }
        }
    }
}
