package dzolla.photograbber2;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dzolla.photograbber2.pojos.Photo;
import dzolla.photograbber2.pojos.RESTresponse;
import dzolla.photograbber2.pojos.Result;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static dzolla.photograbber2.R.id.map;


public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    String mCoordinates;

    Bitmap mBitmap;
    private boolean mIsFirstStart = true;
    private GoogleMap mMap;
    private LatLng mPosition;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    LocationRequest mLocationRequest;
    private final static String TAG = "PhotoGrabber";
    private final static String PLACES_WEB_SERVICE_KEY = "AIzaSyC6EcejXzbUN7ZRtxfaDUc7VcUtU-50IIc";
    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/";
    private static final String BASE_URL_PHOTO = "https://maps.googleapis.com/maps/api/place/photo?";
    List<Bitmap> bitMaps;
    Retrofit retrofit;
    RESTfulApi restfulApi;
    RESTresponse places;
    //    List<String> mPhotoRefs;
    ImageView iv;
    ContentLoadingProgressBar progressBar;
    List<Bitmap> mBitmaps;
    int mCount;
    List<Target> targets;


    int mLimit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: " + " location is " + mLastLocation);
        setContentView(R.layout.activity_maps);

        createTargets();
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        restfulApi = retrofit.create(RESTfulApi.class);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        com.github.clans.fab.FloatingActionButton fabMyPos =
                (com.github.clans.fab.FloatingActionButton) findViewById(R.id.fabMyPos);

        fabMyPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createSettingsRequest();
                addMarkerAndAnimateCamera();
            }
        });
        com.github.clans.fab.FloatingActionButton fabPlus =
                (com.github.clans.fab.FloatingActionButton) findViewById(R.id.fabAdd);

        progressBar = (ContentLoadingProgressBar) findViewById(R.id.image_loading);

        fabPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.show();
                getPhotoRefs();
            }
        });

        // Create GoogleApiCLientApi instance
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(Places.GEO_DATA_API)
                    .addApi(Places.PLACE_DETECTION_API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart: " + " location is " + mLastLocation);
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop: " + " location is " + mLastLocation);
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions(ACCESS_FINE_LOCATION, WRITE_EXTERNAL_STORAGE);
    }

    private void checkPermissions(String... permissions) {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
                Log.d(TAG, "checkPermissions: permissionsNeeded.size()= "+permissionsNeeded.size());
            }
            if (!permissionsNeeded.isEmpty())
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mGoogleApiClient.reconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x1) {
            Log.i(TAG, "onActivityResult: request code=0x1" + " location is " + mLastLocation);
            // Make sure the app is not already connected or attempting to connect
            requestLocationUpdates();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMapToolbarEnabled(false);
    }

    private void createLocationRequest() {
        Log.i(TAG, "createLocationRequest: " + "location is" + mLastLocation);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
//                final LocationSettingsStates states = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.
                        Log.i(TAG, "location settings check Result: SUCCESS");
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "location settings check Result: RESOLUTION_REQUIRED");
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    MapsActivity.this,
                                    0x1);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        createSettingsRequest();
        createLocationRequest();
        requestLocationUpdates();
        getLastLocation();
        addMarkerAndAnimateCamera();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        mPosition = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mCoordinates = mLastLocation.getLatitude() + "," + mLastLocation.getLongitude();
//        Log.i(TAG, "onLocationChanged: " + "location s " + mLastLocation);
        if (mIsFirstStart) {
            addMarkerAndAnimateCamera();
            mIsFirstStart = false;
        }
        if (mMap != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(mPosition)
                    .title("Start")
                    .flat(true)
            );
        }
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            Log.i(TAG, "onActivityResult: REQUESTING LOCATION UPDATES");
        }
    }

    private void addMarkerAndAnimateCamera() {
        if (mLastLocation != null) {
            mMap.clear();

            mMap.addMarker(new MarkerOptions()
                    .position(mPosition)
                    .title("Start")
                    .flat(true)
            );
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mPosition, 15));
        }
    }


    private void getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);

            if (mLastLocation != null)
                mPosition = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            Log.i(TAG, "onConnected: GETTING LAST LOCATION" + "location is" + mLastLocation);
        }
    }

    private void getPhotoRefs() {
        final List<String> photoRefs = new ArrayList<>();
        restfulApi.getPlaces(
                PLACES_WEB_SERVICE_KEY,
                mCoordinates,
                "2000").enqueue(new Callback<RESTresponse>() {
            @Override
            public void onResponse(Call<RESTresponse> call, Response<RESTresponse> response) {

                for (Result result : response.body().getResults()) {
                    if (result.getPhotos() != null) {
                        for (Photo photo : result.getPhotos()) {
//                            Log.i(TAG, "onResponse: " + photo.getPhotoReference());
                            photoRefs.add(photo.getPhotoReference());


                        }
                    }
                }
                Log.i(TAG, "onResponse: photorefs size" + photoRefs.size());
                loadImages(photoRefs);
                Log.i(TAG, "onResponse: loadImages(photoRefs); ");
            }

            @Override
            public void onFailure(Call<RESTresponse> call, Throwable t) {
                Toast.makeText(MapsActivity.this, "An error occurred during networking", Toast.LENGTH_SHORT).show();
                progressBar.hide();
            }
        });
    }

    private void loadImages(List<String> photoRefs) {
        bitMaps = new ArrayList<>();
        mCount = 0;
        Collections.shuffle(photoRefs);
        Log.i(TAG, "loadImages: Entered the loadImages method");
        if (photoRefs != null && photoRefs.size() > 0) {
            Log.i(TAG, "loadImages: (photoRefs != null && photoRefs.size() > 0)");
            if (photoRefs.size() < 4) {
                Log.i(TAG, "loadImages: (photoRefs.size() < 4)");
                mLimit = photoRefs.size();
                Log.i(TAG, "loadImages: mLimit = photoRefs.size() = " + photoRefs.size());
            } else {
                mLimit = 4;
                Log.i(TAG, "loadImages:  mLimit = 4;" + mLimit);
            }
            for (int i = 0; i < mLimit; i++) {
                String URL = BASE_URL_PHOTO + "maxwidth=300&photoreference=" + photoRefs.get(i) + "&key=" + PLACES_WEB_SERVICE_KEY;
                Log.i(TAG, "loadImages: String URL =" + URL);
                Picasso.with(getApplicationContext())
                        .load(URL)
                        .resize(300, 200)
                        .into(targets.get(i));
                mCount++;
            }
        } else {
            Log.i(TAG, "loadImages: PHOTOREFS IS NUL");
            progressBar.hide();
        }
    }

    void createTargets() {
        targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
//           for (int i=0; i<mLimit;i++) {
                    bitMaps.add(bitmap);
//            mBitMapCount++;
//           }
                    Log.i(TAG, "onBitmapLoaded: bitMaps.add(bitmap); " + "bitMaps.size()= " + bitMaps.size());
                    if (bitMaps.size() == mLimit) {
                        Log.i(TAG, "onBitmapLoaded: (mCount == mLimit)" + "mCount= " + mCount + " mLimit= " + mLimit);


                        AlertDialog.Builder ImageDialog = new AlertDialog.Builder(MapsActivity.this);
                        ImageDialog.setTitle("Share image");
                        ImageView showImage = new ImageView(MapsActivity.this);
                        showImage.setImageDrawable(createCollage(bitMaps));
                        ImageDialog.setView(showImage);

                        ImageDialog.setPositiveButton("Share", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                share_bitMap_to_Apps();
                            }
                        });
                        ImageDialog.show();

//                        iv.setImageDrawable(createCollage(bitMaps));
                        progressBar.hide();


                    }
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    Log.i(TAG, "onBitmapFailed: ");
                    progressBar.hide();
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            };
            targets.add(target);
        }
    }


    private Drawable createCollage(List<Bitmap> parts) {
        Bitmap result = Bitmap.createBitmap(parts.get(0).getWidth() * 2, parts.get(0).getHeight() * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        for (int i = 0; i < parts.size(); i++) {
            canvas.drawBitmap(parts.get(i), parts.get(i).getWidth() * (i % 2), parts.get(i).getHeight() * (i / 2), paint);
        }
        mBitmap = result;
        Drawable d = new BitmapDrawable(getResources(), result);
        return d;
    }

    public void share_bitMap_to_Apps() {

        Intent i = new Intent(Intent.ACTION_SEND);

        i.setType("image/*");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
    /*compress(Bitmap.CompressFormat.PNG, 100, stream);
    byte[] bytes = stream.toByteArray();*/


        i.putExtra(Intent.EXTRA_STREAM, getImageUri(this, mBitmap));
        try {
            startActivity(Intent.createChooser(i, "Share image"));
        } catch (android.content.ActivityNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
//        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

}
