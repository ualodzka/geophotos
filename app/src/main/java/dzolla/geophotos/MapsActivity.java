package dzolla.geophotos;

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

import dzolla.geophotos.pojos.Photo;
import dzolla.geophotos.pojos.RESTresponse;
import dzolla.geophotos.pojos.Result;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static dzolla.geophotos.R.id.map;


public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    Bitmap bitmap;
    String coordinates;
    boolean isFirstStart = true;
    GoogleMap mMap;
    LatLng position;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;
    final static String TAG = "PhotoGrabber";
    final static String PLACES_WEB_SERVICE_KEY = "AIzaSyC6EcejXzbUN7ZRtxfaDUc7VcUtU-50IIc";
    static final String BASE_URL = "https://maps.googleapis.com/maps/api/";
    static final String BASE_URL_PHOTO = "https://maps.googleapis.com/maps/api/place/photo?";
    List<Bitmap> bitMaps;
    Retrofit retrofit;
    RESTfulApi restfulApi;
    RESTresponse places;
    ImageView iv;
    ContentLoadingProgressBar progressBar;
    List<Bitmap> bitmaps;
    List<Target> targets;
    int mCount, photoLimit;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        createTargets();
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        restfulApi = retrofit.create(RESTfulApi.class);

        setUpViews();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        // Create GoogleApiCLientApi instance
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
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
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
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
                Log.d(TAG, "checkPermissions: permissionsNeeded.size()= " + permissionsNeeded.size());
            }
            if (!permissionsNeeded.isEmpty())
                ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        googleApiClient.reconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x1) {
            requestLocationUpdates();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMapToolbarEnabled(false);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(googleApiClient,
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
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
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

    private void setUpViews() {
        progressBar = (ContentLoadingProgressBar) findViewById(R.id.image_loading);
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
        fabPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.show();
                getPhotoRefs();
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
        lastLocation = location;
        position = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        coordinates = lastLocation.getLatitude() + "," + lastLocation.getLongitude();
        if (isFirstStart) {
            addMarkerAndAnimateCamera();
            isFirstStart = false;
        }
        if (mMap != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title("Start")
                    .flat(true)
            );
        }
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        }
    }

    private void addMarkerAndAnimateCamera() {
        if (lastLocation != null) {
            mMap.clear();

            mMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title("Start")
                    .flat(true)
            );
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
        }
    }


    private void getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    googleApiClient);

            if (lastLocation != null)
                position = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        }
    }

    private void getPhotoRefs() {
        final List<String> photoRefs = new ArrayList<>();
        restfulApi.getPlaces(
                PLACES_WEB_SERVICE_KEY,
                coordinates,
                "2000").enqueue(new Callback<RESTresponse>() {
            @Override
            public void onResponse(Call<RESTresponse> call, Response<RESTresponse> response) {
                for (Result result : response.body().getResults()) {
                    if (result.getPhotos() != null) {
                        for (Photo photo : result.getPhotos()) {
                            photoRefs.add(photo.getPhotoReference());
                        }
                    }
                }
                loadImages(photoRefs);
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
        if (photoRefs != null && photoRefs.size() > 0) {
            if (photoRefs.size() < 4) {
                photoLimit = photoRefs.size();
            } else {
                photoLimit = 4;
            }
            for (int i = 0; i < photoLimit; i++) {
                String URL = BASE_URL_PHOTO + "maxwidth=300&photoreference=" + photoRefs.get(i) + "&key=" + PLACES_WEB_SERVICE_KEY;
                Picasso.with(getApplicationContext())
                        .load(URL)
                        .resize(300, 200)
                        .into(targets.get(i));
                mCount++;
            }
        } else {
            progressBar.hide();
        }
    }

    void createTargets() {
        targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    bitMaps.add(bitmap);
                    if (bitMaps.size() == photoLimit) {
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
                        progressBar.hide();
                    }
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
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
        bitmap = result;
        Drawable d = new BitmapDrawable(getResources(), result);
        return d;
    }

    public void share_bitMap_to_Apps() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/*");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        i.putExtra(Intent.EXTRA_STREAM, getImageUri(this, bitmap));
        try {
            startActivity(Intent.createChooser(i, "Share image"));
        } catch (android.content.ActivityNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }
}
