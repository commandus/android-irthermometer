package com.commandus.irthermometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receive android.intent.action.BOOT_COMPLETED broadcast and start up
 */
public class ReceiverBoot extends BroadcastReceiver {
    private static final String TAG = ReceiverBoot.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Settings settings = Settings.getSettings(context);
        boolean autoStart = settings.isAutoStart();
        Log.d(TAG, "device rebooted, auto-start: " + Boolean.toString(autoStart));
        if (autoStart) {
            Intent intentA = new Intent(context, MainActivity.class);
            intentA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intentA);
            Log.d(TAG, "Main activity started");
        }
    }
}
