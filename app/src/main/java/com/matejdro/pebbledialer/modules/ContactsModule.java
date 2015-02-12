package com.matejdro.pebbledialer.modules;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import com.getpebble.android.kit.util.PebbleDictionary;
import com.matejdro.pebbledialer.PebbleTalkerService;
import com.matejdro.pebbledialer.pebble.PebbleCommunication;
import com.matejdro.pebbledialer.util.ContactUtils;
import com.matejdro.pebbledialer.util.TextUtil;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import timber.log.Timber;

public class ContactsModule extends CommModule
{
    public static int MODULE_CONTACTS = 3;

    private int nextToSend = -1;
    private boolean openWindow = false;

    public ContactsModule(PebbleTalkerService service) {
        super(service);

        names = new ArrayList<String>();
        idSet = new HashSet<Integer>();
        ids = new ArrayList<Integer>();
        filters = new ArrayList<Integer>();
    }

    private List<Integer> filters;
	private List<String> names;
	private List<Integer> ids;
	private HashSet<Integer> idSet;

	private int group = -1;

	public static final String queries[] = {"[^GHIghi4JKLjkl6MNOmno6PQRSŠpqrsš7TUVtuv8wWXYZŽxyzž9]", "[GHIghi4JKLjkl6MNOmno6 ]", "[PQRSŠpqrsš7TUVtuv8wWXYZŽxyzž9 ]"};

	private boolean filterMode;

	public void beginSending(String contactGroup) {
        filters.clear();

        this.group = getGroupId(contactGroup);
        Timber.d("Group " + group);

        filterMode = contactGroup == null || !getService().getGlobalSettings().getBoolean("skipGroupFiltering", false);

        openWindow = true;
        refreshContacts();
		queueSendEntries(0);
	}

	private String buildSelection()
	{
		if (filters.size() == 0)
			return "1";
		String selectionHalf = "";
		for (int i = 0; i < filters.size(); i++)
			selectionHalf = selectionHalf.concat(queries[filters.get(i)]);

		String selection = ContactsContract.Contacts.DISPLAY_NAME + " GLOB \"" + selectionHalf + "*\" OR " + ContactsContract.Contacts.DISPLAY_NAME + " GLOB \"* " + selectionHalf + "*\"";
		return selection;
	}

	private void sendContacts(int offset)
	{
		PebbleDictionary data = new PebbleDictionary();

		data.addUint8(0, (byte) 3);
        data.addUint8(1, (byte) 0);

        data.addUint16(2, (short) offset);
		data.addUint16(3, (short) names.size());

		for (int i = 0; i < 3; i++)
		{
			int listPos = offset + i;
			if (listPos >= names.size())
				break;

			data.addString(i + 4, names.get(i + offset));

		}

        getService().getPebbleCommunication().sendToPebble(data);
	}

	private void refreshContacts()
	{
		ContentResolver resolver = getService().getContentResolver();

		String selection = "( " + buildSelection() + " )";
		String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " ASC";

		Uri uri;
		String idIndex;
		if (group >= 0)
		{
			uri = ContactsContract.Data.CONTENT_URI;
			selection += " AND " + ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + " = " + group;
			idIndex = "contact_id";
		}
		else
		{
			selection += " AND " + ContactsContract.Contacts.HAS_PHONE_NUMBER + " = 1";
			uri = ContactsContract.Contacts.CONTENT_URI;
			sortOrder += " LIMIT " + (filterMode ? "7" : "2000");
			idIndex = ContactsContract.Contacts._ID;
		}


		Cursor cursor = resolver.query(uri, null, selection, null, sortOrder);

		names.clear();
		ids.clear();
		idSet.clear();

		while (cursor.moveToNext())
		{
			String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
			int id = cursor.getInt(cursor.getColumnIndex(idIndex));

			if (idSet.contains(id))
				continue;

			names.add(TextUtil.prepareString(name));

			ids.add(id);
			idSet.add(id);

			if (ids.size() > (filterMode ? 7 : 2000))
				break;
		}

        Timber.d("Loaded " + names.size() + " contacts.");

		cursor.close();
	}

	public void filterContacts(int buttonId)
	{
        if (buttonId == 3)
            filters.clear();
        else
		    filters.add(buttonId);

		refreshContacts();
		queueSendEntries(0);
	}

	private int getGroupId(String group)
	{
        if (group == null)
            return -1;

		ContentResolver resolver = getService().getContentResolver();

		Cursor cursor = resolver.query(ContactsContract.Groups.CONTENT_SUMMARY_URI, new String[] { ContactsContract.Groups._ID, ContactsContract.Groups.SUMMARY_COUNT}, ContactsContract.Groups.TITLE + " = ? AND " + ContactsContract.Groups.SUMMARY_COUNT + " > 0", new String[] { group }, null);

		if (!cursor.moveToNext())
			return -1;

		return cursor.getInt(0);
	}


    @Override
    public boolean sendNextMessage() {
        if (nextToSend != -1)
        {
            sendContacts(nextToSend);

            nextToSend = -1;
            openWindow = false;

            return true;
        }

        return false;

    }

    private void gotMessageRequestContacts(PebbleDictionary message)
    {
        int offset = message.getUnsignedIntegerAsLong(2).intValue();

        if (filterMode && offset > 7)
        {
            filterMode = false;
            refreshContacts();
        }

        if (offset >= names.size())
        {
            Timber.w("Received contact offset out of bounds!");
            return;
        }

        queueSendEntries(offset);
    }

    private void gotMessageContactPicked(PebbleDictionary message)
    {
        int offset = message.getInteger(2).intValue();
        if (offset >= ids.size())
        {
            Timber.w("Received contact offset out of bounds!");
            return;
        }

        int id = ids.get(offset);
        NumberPickerModule.get(getService()).showNumberPicker(id);
    }

    private void gotMessageFilter(PebbleDictionary message)
    {
        int button = message.getUnsignedIntegerAsLong(2).intValue();
        Timber.d("Offset " + button);

        filterContacts(button);
    }

    @Override
    public void gotMessageFromPebble(PebbleDictionary message) {
        int id = message.getUnsignedIntegerAsLong(1).intValue();
        switch (id)
        {
            case 0: //Filter button
                gotMessageFilter(message);
                break;
            case 1: //Request contacts
                gotMessageRequestContacts(message);
                break;
            case 2: //Picked contact
                gotMessageContactPicked(message);
                break;
        }
    }

    public void queueSendEntries(int offset)
    {
        nextToSend = offset;
        PebbleCommunication communication = getService().getPebbleCommunication();
        communication.queueModulePriority(this);
        communication.sendNext();
    }


    public static ContactsModule get(PebbleTalkerService service)
    {
        return (ContactsModule) service.getModule(MODULE_CONTACTS);
    }
}
