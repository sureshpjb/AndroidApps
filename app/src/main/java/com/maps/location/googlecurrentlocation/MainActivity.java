package com.maps.location.googlecurrentlocation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback {

    private static final String TAG = "MainActivity";

    private static final String LATITUDE_STR = "{lat}";
    private static final String LONGITUDE_STR = "{lng}";
    private static final String STATUS_STR = "{status}";
    private static final String LOCATION_STR = "{location}";
    private static final String PREFS = "MainPrefs";
    private static final String PREF_PRIMARY_CONTACT = "primaryContactNo";

    private static final String GOOGLE_MAPS_URL = "http://maps.google.com/maps?q=" + LATITUDE_STR + "," + LONGITUDE_STR;
    private static final String SMS = "Status: " + STATUS_STR + "\nLocation: " + LOCATION_STR;

    private RadioGroup radioGroup;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private String mLastUpdateTime;
    private GoogleMap googleMap;
    private SmsManager smsManager;
    private double lastUpdatedLatitude;
    private double lastUpdatedLongitude;

    SharedPreferences sharedpreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smsManager = SmsManager.getDefault();
        radioGroup = (RadioGroup) findViewById(R.id.statusRadioGroup);

        sharedpreferences = getSharedPreferences(PREFS,  Context.MODE_PRIVATE);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addPhNumMenuItem:
                this.launchPhoneNumberDialog(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void launchPhoneNumberDialog(final Context context) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        final EditText edittext = new EditText(this);
        alert.setTitle("Edit Primary Contact");
        String phNum = sharedpreferences.getString(PREF_PRIMARY_CONTACT, "");
        edittext.setText(phNum == null || "".equals(phNum) ? "" : phNum);

        alert.setView(edittext);

        alert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //What ever you want to do with the value
                String phNum = edittext.getText().toString();

                //save the string to storage
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(PREF_PRIMARY_CONTACT, phNum);

                editor.commit();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //Cancel clicked
            }
        });

        alert.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(3000);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        lastUpdatedLatitude = location.getLatitude();
        lastUpdatedLongitude = location.getLongitude();

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        CameraPosition googlePlex = CameraPosition.builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                .zoom(16)
                .bearing(0)
                .tilt(45)
                .build();

        if(googleMap != null) {
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(googlePlex));
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
    }

    public void sendLocationUpdate(View view) {
        if(lastUpdatedLatitude == 0 || lastUpdatedLongitude == 0) {
            return;
        }

        int selectedId = radioGroup.getCheckedRadioButtonId();

        RadioButton selectedRadioBtn = (RadioButton) findViewById(selectedId);

        sharedpreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String phNum = null;

        if (sharedpreferences.contains(PREF_PRIMARY_CONTACT)) {
            phNum = sharedpreferences.getString(PREF_PRIMARY_CONTACT, "");
        }

        if(phNum == null || "".equals(phNum)) {
            Toast.makeText(this, "Please set a Primary contact to send message", Toast.LENGTH_SHORT).show();
        } else {
            sendLocation(phNum, selectedRadioBtn.getText().toString(), lastUpdatedLatitude, lastUpdatedLongitude);
        }
    }

    private void sendLocation(String phNo, String status, double latitude, double longitude) {
        String googleMapsURL = getGoogleMapsURL(latitude, longitude);

        if(phNo == null || "".equals(phNo) || googleMapsURL == null) {
            return;
        }

        String message = SMS.replace(STATUS_STR, status).replace(LOCATION_STR, googleMapsURL);

        smsManager.sendTextMessage(phNo, null, message, null, null);

        Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
    }

    private String getGoogleMapsURL(double latitude, double longitude) {
        return GOOGLE_MAPS_URL.replace(LATITUDE_STR, String.valueOf(latitude)).replace(LONGITUDE_STR, String.valueOf(longitude));
    }
}
