package com.matejdro.pebbledialer.modules;

import android.content.SharedPreferences;
import android.telephony.SmsManager;

import com.getpebble.android.kit.util.PebbleDictionary;
import com.matejdro.pebblecommons.messages.MessageTextProvider;
import com.matejdro.pebblecommons.messages.MessageTextProviderListener;
import com.matejdro.pebblecommons.messages.PhoneVoiceProvider;
import com.matejdro.pebblecommons.messages.TimeVoiceProvider;
import com.matejdro.pebblecommons.pebble.CommModule;
import com.matejdro.pebblecommons.pebble.PebbleCommunication;
import com.matejdro.pebblecommons.pebble.PebbleTalkerService;
import com.matejdro.pebblecommons.userprompt.NativePebbleUserPrompter;
import com.matejdro.pebblecommons.util.ListSerialization;
import com.matejdro.pebblecommons.util.TextUtil;
import com.matejdro.pebbledialer.callactions.EndCallAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

/**
 * Created by Matej on 3.12.2014.
 */
public class SMSReplyModule extends CommModule implements MessageTextProviderListener
{
    public static final int MODULE_SMS_REPLIES = 5;

    public static final int MAX_NUMBER_OF_ACTIONS = 20;

    private List<String> actionList;
    int listSize = -1;
    private int nextListItemToSend = -1;
    private boolean tertiaryTextMode = false;

    private int timeVoiceIndex = -1;
    private int phoneVoiceIndex = -1;
    private int writingIndex = -1;

    private String number;

    public SMSReplyModule(PebbleTalkerService service)
    {
        super(service);
    }

    private void sendNextListItems()
    {
        Timber.d("Sending action list items");
        PebbleDictionary data = new PebbleDictionary();

        byte[] bytes = new byte[3];

        data.addUint8(0, (byte) 5);
        data.addUint8(1, (byte) 0);

        int segmentSize = Math.min(listSize - nextListItemToSend, 4);

        bytes[0] = (byte) nextListItemToSend;
        bytes[1] = (byte) listSize;
        bytes[2] = (byte) (tertiaryTextMode ? 1 : 0);

        byte[] textData = new byte[segmentSize * 19];

        for (int i = 0; i < segmentSize; i++)
        {
            String text = TextUtil.prepareString(actionList.get(i + nextListItemToSend), 18);
            System.arraycopy(text.getBytes(), 0, textData, i * 19, text.getBytes().length);

            textData[19 * (i + 1) -1 ] = 0;
        }

        data.addBytes(2, bytes);
        data.addBytes(3, textData);

        getService().getPebbleCommunication().sendToPebble(data);

        nextListItemToSend += 4;
        if (nextListItemToSend >= listSize)
            nextListItemToSend = -1;
    }

    @Override
    public boolean sendNextMessage()
    {
        if (actionList != null && nextListItemToSend >= 0 )
        {
            sendNextListItems();
            return true;
        }

        return false;
    }

    public void startSMSProcess(String number)
    {
        this.number = number;

        tertiaryTextMode = false;

        SharedPreferences settings = getService().getGlobalSettings();

        actionList = new ArrayList<>();
        if (settings.getBoolean("timeVoice", true) && getService().getPebbleCommunication().getConnectedPebblePlatform() == PebbleCommunication.PEBBLE_PLATFORM_BASSALT)
        {
            actionList.add("Time Voice");
            timeVoiceIndex = actionList.size() - 1;
        }
        if (settings.getBoolean("phoneVoice", true))
        {
            actionList.add("Phone Voice");
            phoneVoiceIndex = actionList.size() - 1;
        }
        if (settings.getBoolean("enableSMSWriting", true))
        {
            actionList.add("Write");
            writingIndex = actionList.size() - 1;
        }

        actionList.addAll(ListSerialization.loadList(settings, "smsCannedResponses"));

        listSize = Math.min(actionList.size(), MAX_NUMBER_OF_ACTIONS);
        nextListItemToSend = 0;



        PebbleCommunication communication = getService().getPebbleCommunication();
        communication.queueModulePriority(this);
        communication.sendNext();
    }

    public void showWritingList()
    {
        tertiaryTextMode = true;
        SharedPreferences settings = getService().getGlobalSettings();

        actionList = new ArrayList<>();
        actionList.add("Send");

        actionList.addAll(ListSerialization.loadList(settings, "smsWritingPhrases"));
        actionList.addAll(ListSerialization.loadList(settings, "smsCannedResponses"));

        listSize = Math.min(actionList.size(), MAX_NUMBER_OF_ACTIONS);
        nextListItemToSend = 0;

        PebbleCommunication communication = getService().getPebbleCommunication();
        communication.queueModulePriority(this);
        communication.sendNext();
    }

    public void startTimeVoice()
    {
        Timber.d("Starting time voice");
        MessageTextProvider voiceProvider = new TimeVoiceProvider(getService());
        voiceProvider.startRetrievingText(this);
    }

    public void startPhoneVoice()
    {
        Timber.d("Starting phone voice");

        MessageTextProvider voiceProvider = new PhoneVoiceProvider(new NativePebbleUserPrompter(getService()), getService());
        voiceProvider.startRetrievingText(this);
    }


    @Override
    public void gotText(String text)
    {
        EndCallAction.get(CallModule.get(getService())).executeAction();

        SmsManager.getDefault().sendTextMessage(number, null, text, null, null);
    }

    public void gotMessageActionItemPicked(PebbleDictionary message)
    {
        int index = message.getUnsignedIntegerAsLong(2).intValue();
        Timber.d("SMS list picked %d", index);

        if (index == timeVoiceIndex)
            startTimeVoice();
        else if (index == phoneVoiceIndex)
            startPhoneVoice();
        else if (index == writingIndex)
            showWritingList();
        else
        {
            gotText(actionList.get(index));
        }

    }

    private void gotMessageReplyText(PebbleDictionary data)
    {
        String text = data.getString(2);
        gotText(text);
    }

    @Override
    public void gotMessageFromPebble(PebbleDictionary message)
    {
        int id = message.getUnsignedIntegerAsLong(1).intValue();
        switch (id)
        {
            case 0:
                gotMessageActionItemPicked(message);
                break;
            case 1:
                gotMessageReplyText(message);
                break;
        }
    }

    public static SMSReplyModule get(PebbleTalkerService service)
    {
        return (SMSReplyModule) service.getModule(MODULE_SMS_REPLIES);
    }

}
