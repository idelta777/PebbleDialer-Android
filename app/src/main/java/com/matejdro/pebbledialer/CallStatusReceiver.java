package com.matejdro.pebbledialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

/**
 * Created by Matej on 31.12.2014.
 */
public class CallStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("callStatusReceived");

        Intent startIntent = new Intent(context, CallService.class);
        startIntent.setAction(CallService.INTENT_CALL_STATUS);
        startIntent.putExtra("callIntent", intent);

        context.startService(startIntent);

    }
}
