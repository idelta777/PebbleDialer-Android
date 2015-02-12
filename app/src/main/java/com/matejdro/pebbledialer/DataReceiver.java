package com.matejdro.pebbledialer;

import static com.getpebble.android.kit.Constants.APP_UUID;
import static com.getpebble.android.kit.Constants.MSG_DATA;
import static com.getpebble.android.kit.Constants.TRANSACTION_ID;

import java.util.UUID;

import org.json.JSONException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import timber.log.Timber;


public class DataReceiver extends BroadcastReceiver {

	public final static UUID dialerUUID = UUID.fromString("158A074D-85CE-43D2-AB7D-14416DDC1058");

    public void receiveData(final Context context, final int transactionId, final String jsonPacket)
    {
        PebbleKit.sendAckToPebble(context, transactionId);

        Intent intent = new Intent(context, PebbleTalkerService.class);
        intent.setAction(PebbleTalkerService.INTENT_PEBBLE_PACKET);
        intent.putExtra("packet", jsonPacket);
        context.startService(intent);
    }

    public void receivedAck(Context context, int transactionId)
    {
        Intent intent = new Intent(context, PebbleTalkerService.class);
        intent.setAction(PebbleTalkerService.INTENT_PEBBLE_ACK);
        intent.putExtra("transactionId", transactionId);
        context.startService(intent);
    }

    public void receivedNack(Context context, int transactionId)
    {
        Intent intent = new Intent(context, PebbleTalkerService.class);
        intent.setAction(PebbleTalkerService.INTENT_PEBBLE_NACK);
        intent.putExtra("transactionId", transactionId);
        context.startService(intent);
    }

    public void onReceive(final Context context, final Intent intent) {
        final int transactionId = intent.getIntExtra(TRANSACTION_ID, -1);

        if ("com.getpebble.action.app.RECEIVE_ACK".equals(intent.getAction()))
        {
            receivedAck(context, transactionId);
            return;
        }
        if ("com.getpebble.action.app.RECEIVE_NACK".equals(intent.getAction()))
        {
            receivedNack(context, transactionId);
            return;
        }

        final UUID receivedUuid = (UUID) intent.getSerializableExtra(APP_UUID);

        // Pebble-enabled apps are expected to be good citizens and only inspect broadcasts containing their UUID
        if (!dialerUUID.equals(receivedUuid)) {
            return;
        }

        final String jsonData = intent.getStringExtra(MSG_DATA);
        if (jsonData == null || jsonData.isEmpty()) {
            return;
        }

        receiveData(context, transactionId, jsonData);

        try {
            final PebbleDictionary data = PebbleDictionary.fromJson(jsonData);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    }
}
