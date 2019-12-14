//  Reference:
//      https://www.tutorialspoint.com/sending-and-receiving-data-with-sockets-in-android
//      Sam Portillo made the following modifications:
//      11/20/2019  Thread 2 does not loop.
//      11/20/2019  Thread 3 creates a Thread 2.
//      11/26/2019  Toggle Connect / Disconnect Button
//      11/28/2019  Thread 1 loops to poll GPS & send_Button coordinates to server.

//      Working with threads in IntelliJ → less ▼ debugging support.
//      See String gpsMe for an example.
//      C:\z\github\Android\GPSMe



package com.zyxe.gps_me;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


/**
 *  When a user starts this application, this is the entry point of execution.
 *  Shows a receiving text smsMessage_EditText.
 *  Shows the receiving text number.
 *  Gets GPS coordinates.
 *  Sends GPS coordinates to a SMS text number.
 *  Creates a socket connection with a Raspberry Pi to receive status
 *  and control garage_Button door.
 *
 *      VIP mode: Instance 1
 *           Pre-conditions:
 *              Android Cell phone with WiFi disabled.
 *                   WiFi may disconnect before VIP command to close garage door.
 *              Start a socket connection to Raspberry Pi on site ( < 30 meters).
 *              Leave premises of a distance greater than 30 meters.
 *              Sensor state = Garage door is open.
 *
 *           Input:
 *               Nothing
 *           Response:
 *                Trigger Garage Door.
 *
 *           Post-Condition:
 *               Sensor state = Garage door is closed.
 *               Android messageLog_TextView shows confirmation of action & result.
 *
 *      VIP mode: Instance 2
 *      Reverse of 1 → detects user coming home & automatically opens the
 *      garage door.
 *
 *
 * @author Sam Portillo
 */
public class GPSMe extends AppCompatActivity
{
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 0;
    private static final int SEND_SMS_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "SAM";

    public double latitude;
    public double longitude;
    public double speed_mps;
    public double speed_mph;
    public double speed_mph_max = 0;
    public double distance_max = 0;

    public EditText smsPhone_EditText;
    public EditText smsMessage_EditText;
    public Button GPS_Button;
    public Button send_Button;
    public Button connect_Button;
    public Button status_Button;

    public TextView messageLog_TextView;     //  See status_Garage
    public Button garage_Button;
    String SERVER_IP;
    int SERVER_PORT;
    Socket socket;
    Thread Thread1 = null;

    PrintWriter output;
    InputStreamReader in;
    BufferedReader br;
    boolean loop = false;
    boolean VIP_OnSite = false;
    String gpsMe = "";              //  Set gpsMe to "";
                                    //  otherwise,
                                    //  passing in Thread → error.


//    double lat_A = 37.81580846;         //  PC Room
//    double lon_A = -121.90025908;       //  PC Room
    double lat_A = 37.815767;         //  Garage
    double lon_A = -121.900619;       //  Garage
    int homeRoam = 40;                  //  PC Room is 40.
    double gps_Starting_Calibration_Distance;
    double distanceTo;
    int pollInterval = 500;            // ms

// 24

    /**
     * This method creates the user interface from the activity_main resource.
      * @param savedInstanceState Receives a reference to the Bundle object.
     * @author Sam Portillo
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smsPhone_EditText = findViewById(R.id.smsPhone_EditText);
        smsMessage_EditText = findViewById(R.id.smsMessage_EditText);
        GPS_Button = findViewById(R.id.GPS_Button);
        send_Button = findViewById(R.id.send_Button);
        connect_Button = findViewById(R.id.connect_Button);
        status_Button = findViewById(R.id.status_Button);

        messageLog_TextView = findViewById(R.id.messageLog_TextView);
        garage_Button = findViewById(R.id.garage_Button);
        connect_Button.setText("Connect");
        messageLog_TextView.setMovementMethod(new ScrollingMovementMethod());

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String phone_string = extras.getString("MessageNumber");
            String message_string = extras.getString("Message");
            smsPhone_EditText.setText(phone_string);
            smsMessage_EditText.setText(message_string);

            if (smsMessage_EditText.getText().toString().toLowerCase().equals("gps")) {
                getGPS();
                sendGPS();
            }
        }


        //Toast.makeText(this, "onCreate 1", Toast.LENGTH_SHORT).show();
        //check if the permission is not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "onCreate 2", Toast.LENGTH_SHORT).show();
            //if the permission is not been granted then check if the user has denied the permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECEIVE_SMS)) {
                //Do nothing as user has denied
                Toast.makeText(this, "onCreate 3 - Denied", Toast.LENGTH_SHORT).show();
            } else {
                //a pop up will appear asking for required permission i.e Allow or Deny
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            }
        }

        //Toast.makeText(this, "onCreate 4 - Permission Granted", Toast.LENGTH_SHORT).show();


        // Checking Sending Permissions
        // May only work second time
        send_Button.setEnabled(false);
        if (checkPermission(Manifest.permission.SEND_SMS)) {
            send_Button.setEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQUEST_CODE);
        }


        // GPS
        ActivityCompat.requestPermissions(GPSMe.this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION}, 123);


        GPS_Button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                double d = checkDistanceTo();
                String s = String.format( "OnSite: %b, MaxDistance: %f, Distance: %f, MaxSpeed: %f \n", VIP_OnSite, distance_max, distanceTo, speed_mph_max );
                messageLog_TextView.setText( s );
                getGPS();
            }
        });



        // 39
        //      New     IOT
        connect_Button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                VIP_OnSite = false;
                if (connect_Button.getText().equals("Connect"))
                {
                    //messageLog_TextView.setText("");
                    //SERVER_IP = etIP.getText().toString().trim();
                    //                    //SERVER_PORT = Integer.parseInt(etPort.getText().toString().trim());
//                    SERVER_IP = "10.0.0.227";
//                    SERVER_PORT = 7000;
                    speed_mph_max   = 0;
                    distance_max    = 0;
                    messageLog_TextView.setText("");
                    //SERVER_IP = "10.0.0.124";
                    SERVER_IP = "73.223.16.32";
                    SERVER_PORT = 8070;
                    gps_Starting_Calibration_Distance = checkDistanceTo();

                    if (gps_Starting_Calibration_Distance < homeRoam )
                    {
                        VIP_OnSite = true;
                        Toast.makeText(getApplicationContext(), "Welcome VIP !\nHome Roam\n" + gps_Starting_Calibration_Distance, Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Remote VIP\n" + gps_Starting_Calibration_Distance, Toast.LENGTH_LONG).show();
                    }
                    connect_Button.setText("Disconnect");
                    loop = true;
                    Thread1 = new Thread(new Thread1());
                    Thread1.start();
                } else {
                    loop = false;
                    new Thread(new Thread3("exit")).start();
                }

            }
        });


        status_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                new Thread(new Thread3("Status")).start();
            }
        });


        garage_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Thread3("Garage")).start();
            }
        });


    }//onCreate

    //  60


    /**
     * This method has an instance of the GPS_Tracking
     * class, which returns the GPS coordinates & speed.
     * @author Sam Portillo
     */
    private void getGPS()
    {
        GPS_Tracking g = new GPS_Tracking(getApplicationContext());
        Location l = g.getLocation();
        if (l == null)
            return;

        latitude = l.getLatitude();
        longitude = l.getLongitude();
        speed_mps = l.getSpeed();
        speed_mph = speed_mps * 2.2369362920544;
//        latitude.setText(Double.toString(lat));
//        longitude.setText(String.valueOf(lon));
//        //speed_mps.setText(Double.toString(speed_GPS));
//        speed_mph.setText(Double.toString(speed_GPS * 2.2369362920544));

        String s = String.format("%s %f\n%s %f\n%s %f\n%s %f\n", "Latitude: ", latitude,
                "Longitude", longitude, "Speed mps: ", speed_mps, "Speed mph: ", speed_mph );

        messageLog_TextView.append( s );
    }

// 71
    /**
     * This method checks the distance from one GPS coordinate to another.
     * @return The distance between two GPS coordinates.
     * @author Sam Portillo
     */
    public double checkDistanceTo()
    {
        final double[] distance = new double[1];        //  Crazy circumvention

        runOnUiThread( new Runnable()
        {
            @Override
            public void run()
            {
                GPS_Tracking g = new GPS_Tracking(getApplicationContext());
                Location l = g.getLocation();
                if (l == null)
                    return;

                Location locationA = new Location("");
                Location locationB = new Location("");

                locationA.setLatitude(lat_A);
                locationA.setLongitude(lon_A);

                latitude = l.getLatitude();
                longitude = l.getLongitude();
                speed_mps = l.getSpeed();
                speed_mph = speed_mps * 2.2369362920544;

                if ( speed_mph > speed_mph_max)
                    speed_mph_max = speed_mph;


//                String s = String.format("%s %f\n%s %f\n%s %f\n%s %f\n", "Latitude: ", latitude,
//                        "Longitude", longitude, "Speed mps: ", speed_mps, "Speed mph: ", speed_mph );
//                messageLog_TextView.append( s );

                locationB.setLatitude(latitude);
                locationB.setLongitude(longitude);

                //distance[0] = locationA.distanceTo(locationB);
                distanceTo = locationA.distanceTo(locationB);


                if ( distanceTo > distance_max )
                    distance_max = distanceTo;


                //gpsMe = String.format("%s: %f,  %s: %f", "Feet: ", distance[0] * 3.28084, "Meters: ", distance[0]);
                gpsMe = String.format("%s: %f,  %s: %f", "Feet: ", distanceTo * 3.28084, "Speed: ",speed_mph );
            }
        });

        //return distance[0];
        return distanceTo;
    }

// 96
    /**
     * This interface method is the contract for receiving the results for
     * permission requests.
     * @param requestCode int: The request code for the permission requesting.
     * @param permissions The requested permissions.
     * @param grantResults The grant results for the corresponding permissions
     *                    which is either PERMISSION_GRANTED or PERMISSION_DENIED.
     *                    Never null.
     * @author Sam Portillo
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        //Toast.makeText(this, "onRequestPermissionsResult 1", Toast.LENGTH_SHORT).show();

        //will check the requestCode
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECEIVE_SMS: {
                //check whether the length of grantResults is greater than 0 and is equal to PERMISSION_GRANTED
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Now broadcastreceiver will work in background
                    Toast.makeText(this, "Thank you for permitting!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Well I can't do anything until you permit me", Toast.LENGTH_LONG).show();
                }
            }

            case SEND_SMS_PERMISSION_REQUEST_CODE: {
                Toast.makeText(this, "onRequestPermissionsResult - SEND", Toast.LENGTH_LONG).show();
            }

        }
    }


    // 104

    public void onSend(View v) {
        sendGPS();
    }

    // 106
    /**
     * This method sends the currently listed GPS coordinates
     * to the listed number.  As a google maps link.
     * @author Sam Portillo
     */
    private void sendGPS() {
        String phoneNumber = smsPhone_EditText.getText().toString();
        //String smsMessage = smsMessage_EditText.getText().toString();
        String smsMessage = "http://www.google.com/maps/place/" +
                Double.toString(latitude) + "," +
                Double.toString( longitude );

        if (phoneNumber == null || phoneNumber.length() == 0 || smsMessage == null || smsMessage.length() == 0) {
            Toast.makeText(this, "Error 1", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkPermission(Manifest.permission.SEND_SMS)) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, smsMessage, null, null);
            Toast.makeText(this, "Message Sent !", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }

    }
// 120
    /**
     * This method checks the permission granted status & returns the result.
     * @param permission String validation key is in the form of a string.
     * @return boolean: Returns the result of permission granted status.
     * @author Sam Portillo
     */
    public boolean checkPermission(String permission) {
        int check = ContextCompat.checkSelfPermission(this, permission);
        Log.i(TAG, "check " + check);
        return (check == PackageManager.PERMISSION_GRANTED);
    }

// 123
    //  **********************************************************


    /**
     * Creates socket connection with server on a new thread.
     * Instantiates the BufferedReader & PrintWriter instances.
     * Creates a new thread2 to receive incoming messages.
     * Continues to loop to feed GPS coordinates to server.
     * If the Disconnect button is pressed then it will stop looping.
     * @author Sam Portillo
     *
     */
    class Thread1 implements Runnable
    {
        public void run() {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                output = new PrintWriter(socket.getOutputStream());
                in = new InputStreamReader(socket.getInputStream());
                br = new BufferedReader(in);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        messageLog_TextView.setText("Connected.\n");
                    }
                });
                new Thread(new Thread2()).start();


                //      Continue to loop to monitor & check distance
                do {

                    double calibrate = checkDistanceTo() - gps_Starting_Calibration_Distance;
                    String s = String.format( "%s %b, %s,  %s %f", "OnSite: ", VIP_OnSite, gpsMe, "Calibrate: ", calibrate);
                    Log.i(TAG, s);

                    if ( VIP_OnSite && speed_mph > 5)
                        {
                            VIP_OnSite = false;
                            distance_max    = 0;
                            speed_mph_max   = 0;
                            new Thread(new Thread3( "Garage_Close" )).start();
                        }
                    else
                    if ( !VIP_OnSite && checkDistanceTo() < homeRoam && distance_max > 40 && speed_mph_max > 1)
                    {
                        VIP_OnSite = true;
                        distance_max    = 0;
                        speed_mph_max   = 0;
                        pollInterval = 60000;           // 1 minute       Otherwise, Garage_Open, wait 500 ms, Garage_Close
                        new Thread(new Thread3( "Garage_Open" )).start();
                    }
                        //new Thread(new Thread3( gpsMe )).start();
                        //new Thread(new Thread3( s )).start();
//                        new Thread(new Thread3( Double.toString( calibrate ) )).start();

                    try {
                        Thread.sleep( pollInterval );
                        pollInterval = 1000;
                    } catch (Exception e) {
                        //
                    }


                } while (loop);


            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    //  147
    /**
     * Receive Messages from Server.
     * This class replaces received ▼ with carriage returns.
     * Updates the TextView smsMessage_EditText log with the incoming smsMessage_EditText.
     * @author Sam Portillo
     */
    class Thread2 implements Runnable {
        @Override
        public void run() {
            try {
                final String message = br.readLine();
                final String newMessage = message.replaceAll("▼", "\n");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messageLog_TextView.append("server: " + newMessage + "\n");
                    }
                });
            } catch (IOException e) {
                //e.printStackTrace();
                Log.i(TAG, "IO Exception");
            }
        }
    }

//  157
    /**
     * Send messages to Server & create a new thread if not exit.
     * @param 'String' smsMessage_EditText is the smsMessage_EditText to be sent to the server.
     * @author Sam Portillo
     */
    class Thread3 implements Runnable {
        private String message;

        Thread3(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            output.println(message);
            output.flush();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageLog_TextView.append("> " + message + "\n");
                    if (message.equals("exit")) {
                        messageLog_TextView.append("Socket Connection Closed: " + socket + "\n");
                        connect_Button.setText("Connect");
                    }
                    //  ****************    etMessage.setText("");
                //                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });

            if (message.equals("exit")) {


                try {
                    socket.close();
                    in.close();
                    br.close();
                    output.close();

                } catch (IOException e) {
                    //e.printStackTrace();
                    messageLog_TextView.append("Socket IO Exception:\n");
                }
            } else
                new Thread(new Thread2()).start();
        }
    }
}

//  181





