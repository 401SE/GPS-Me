package com.zyxe.gps_me;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * This class gets the current GPS coordinates, speed, distanceTo another location.
 * @author Sam Portillo
 */
public class GPS_Tracking implements LocationListener {
    Context context;
    public GPS_Tracking(Context c){
        context = c;
    }

    /**
     * This method checks if GPS permissions have been granted.
     * If not granted then smsMessage_EditText the user.
     * If granted then return the GPS coordinates.
     *
     * @return Location is an instance of a GPS_Button data.
     * @author Sam Portillo
     */
    public Location getLocation(){

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(context, "Permission Not Granted", Toast.LENGTH_SHORT).show();
            return null;
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isGPSEnabled){
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 6000, 10, this);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return l;
        } else{
            Toast.makeText(context, "Please enable GPS request", Toast.LENGTH_SHORT).show();
        }

        return null;
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
