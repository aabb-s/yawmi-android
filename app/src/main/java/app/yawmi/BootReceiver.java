package app.yawmi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are lost on restart and on app update: set them again from the saved list. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Alarms.apply(c);
    }
}
