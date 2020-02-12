package com.zyxe.gps_me;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;


/**
 * This class extends BroadcastReceiver which has a service that starts the
 * onReceive method when ever there is an incoming text smsMessage_EditText.
 * This start service, starts execution even when the application Activity is shut
 * down.
 * @author Sam Portillo
 */
public class SMS_Receiver extends BroadcastReceiver
{
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG = "SmsBroadcastReceiver";
    String msg, phoneNo = "";

    /**
     * When this application is in a Activity Shutdown state,
     * BroadcastReceiver enables this method to be the
     * executing entry point.
     * It retrieves all incoming text messages.
     * Creates an intent that starts GPS_Me.
     * @param context Context: Interface to global information about an application environment.
     * @param intent Intent: is a simple smsMessage_EditText object that is used to communicate between
     *                  android components such as activities, content providers,
     *                  broadcast receivers and services. Intents are also used to transfer
     *                  data between activities.
     * @author Sam Portillo
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        //Toast.makeText(context, "onReceive 1", Toast.LENGTH_LONG).show();

        //retrieves the general action to be performed and display on log
        Log.i(TAG, "Intent Received: " + intent.getAction());
        if (intent.getAction() == SMS_RECEIVED)
        {
            // Toast.makeText(context, "onReceive 2", Toast.LENGTH_LONG).show();
            //retrieves a map of extended data from the intent
            Bundle dataBundle = intent.getExtras();
            if (dataBundle != null)
            {
                //creating PDU(Protocol Data Unit) object which is a protocol for transferring smsMessage_EditText
                Object[] mypdu = (Object[]) dataBundle.get("pdus");
                final SmsMessage[] message = new SmsMessage[mypdu.length];

                for (int i = 0; i < mypdu.length; i++)
                {
                    //for build versions >= API Level 23
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    {
                        String format = dataBundle.getString("format");
                        //From PDU we get all object and SmsMessage Object using following line of code
                        message[i] = SmsMessage.createFromPdu((byte[])mypdu[i], format);
                    }
                    else
                    {
                        //<API level 23
                        message[i] = SmsMessage.createFromPdu((byte[])mypdu[i]);
                    }
                    msg = message[i].getMessageBody();
                    phoneNo = message[i].getOriginatingAddress();
                }

                //Toast.makeText(context, "Message: " + msg + "\nNumber: " + phoneNo, Toast.LENGTH_LONG).show();

                Intent smsIntent = new Intent(context, GPSMe.class);
                smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                smsIntent.putExtra("MessageNumber", phoneNo);
                smsIntent.putExtra("Message", msg);
                context.startActivity(smsIntent);
            }
        }
    }
}