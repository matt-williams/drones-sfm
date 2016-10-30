package com.github.williams.matt.dronessfm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceBLEService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiver;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiverDelegate;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING;

public class MainActivity extends AppCompatActivity implements ARDiscoveryServicesDevicesListUpdatedReceiverDelegate, ARDeviceControllerListener, ARDeviceControllerStreamListener {
    private static final String TAG = "MainActivity";

    private ARDiscoveryService mArdiscoveryService;
    private ServiceConnection mArdiscoveryServiceConnection;
    private ARDiscoveryServicesDevicesListUpdatedReceiver mArdiscoveryServicesDevicesListUpdatedReceiver;
    private Handler mHandler = new Handler();

    private String mFolder;
    private Executor mThreadPool = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            10,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    private File mPhotoFile;

//    static {
//        ARSDK.loadSDKLibs();
//    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            Log.e(TAG, "Erasing mPhotoFile");
            mPhotoFile = null;
            try {
                mPhotoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e(TAG, "Caught IOException", ex);
            }
            if (mPhotoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        mPhotoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, 1);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Take a photo to upload, or cancel to finish", Toast.LENGTH_SHORT).show();
                    }
                });
                Log.e(TAG, "Requested photo for " + mPhotoFile);
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "Creating!");
        setContentView(R.layout.activity_main);
        ((EditText)findViewById(R.id.folder)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                mFolder = v.getText().toString();
                v.setEnabled(false);
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                takePhoto();
                return true;
            }
        });
//        initDiscoveryService();
//        registerReceivers();
//        startDiscovery();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Log.e(TAG, "Got OK result!");
                if (mPhotoFile != null) {
                    final File photoFile = mPhotoFile;
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "Running job against " + photoFile);
                            try {
                                HttpClient client = new DefaultHttpClient();
                                HttpPut put = new HttpPut("http://intellidrone.uk.to/api/" + mFolder + "/images/" + photoFile.getName());
                                put.setEntity(new FileEntity(photoFile, "image/jpeg"));
                                HttpResponse putResponse = client.execute(put);
                                Log.e(TAG, "Put response: " + putResponse.getStatusLine());
                                InputStream is = putResponse.getEntity().getContent();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    Log.e(TAG, "Read: " + line);
                                }
                                is.close();
                                photoFile.delete();
                                takePhoto();
                            } catch (IOException e) {
                                Log.e(TAG, "Caught IOException", e);
                            }
                        }
                    });
                } else {
                    takePhoto();
                }
            } else {
                mPhotoFile.delete();
                mPhotoFile = null;
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "Running process job");
                        try {
                            HttpClient client = new DefaultHttpClient();
                            HttpPost post = new HttpPost("http://intellidrone.uk.to/api/" + mFolder + "/process");
                            HttpResponse postResponse = client.execute(post);
                            Log.e(TAG, "Post response: " + postResponse.getStatusLine());
                            InputStream is = postResponse.getEntity().getContent();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                            String line;
                            while ((line = reader.readLine()) != null) {
                                Log.e(TAG, "Read: " + line);
                            }
                            is.close();

                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://intellidrone.uk.to/api/" + mFolder + "/"));
                            startActivity(browserIntent);
                            finish();
                        } catch (IOException e) {
                            Log.e(TAG, "Caught IOException", e);
                        }
                    }
                });
            }
        }
    }
    private void initDiscoveryService()
    {
        // create the service connection
        if (mArdiscoveryServiceConnection == null)
        {
            mArdiscoveryServiceConnection = new ServiceConnection()
            {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service)
                {
                    mArdiscoveryService = ((ARDiscoveryService.LocalBinder) service).getService();

                    startDiscovery();
                }

                @Override
                public void onServiceDisconnected(ComponentName name)
                {
                    mArdiscoveryService = null;
                }
            };
        }

        if (mArdiscoveryService == null)
        {
            // if the discovery service doesn't exists, bind to it
            Intent i = new Intent(getApplicationContext(), ARDiscoveryService.class);
            getApplicationContext().bindService(i, mArdiscoveryServiceConnection, Context.BIND_AUTO_CREATE);
        }
        else
        {
            // if the discovery service already exists, start discovery
            startDiscovery();
        }
    }

    private void startDiscovery()
    {
        if (mArdiscoveryService != null)
        {
            mArdiscoveryService.start();
        }
    }

    // your class should implement ARDiscoveryServicesDevicesListUpdatedReceiverDelegate
    private void registerReceivers()
    {
        mArdiscoveryServicesDevicesListUpdatedReceiver = new ARDiscoveryServicesDevicesListUpdatedReceiver(this);
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastMgr.registerReceiver(mArdiscoveryServicesDevicesListUpdatedReceiver, new IntentFilter(ARDiscoveryService.kARDiscoveryServiceNotificationServicesDevicesListUpdated));
    }

    @Override
    public void onServicesDevicesListUpdated()
    {
        if (mArdiscoveryService != null)
        {
            List<ARDiscoveryDeviceService> deviceList = mArdiscoveryService.getDeviceServicesArray();
            for (ARDiscoveryDeviceService device : deviceList) {
                Log.e(TAG, "Got " + device.toString());
            }
            if (deviceList.size() > 0) {
                ARDiscoveryDeviceService device = deviceList.get(0);
                ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(device);
                Log.e(TAG, discoveryDevice.toString());

                final ARDeviceController deviceController = createDeviceController(discoveryDevice);
                Log.e(TAG, deviceController.toString());

                ARCONTROLLER_ERROR_ENUM error = deviceController.start();
                Log.e(TAG, "deviceController.start() = " + error);

                error = deviceController.getFeatureMiniDrone().sendPilotingTakeOff();
                Log.e(TAG, "deviceController.....sendPilotingTakeOff() = " + error);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureMiniDrone().sendMediaRecordPictureV2();
                        Log.e(TAG, "deviceController.....sendMediaRecordPictureV2() = " + error);

                        error = deviceController.getFeatureMiniDrone().sendPilotingLanding();
                        Log.e(TAG, "deviceController.....sendPilotingLanding() = " + error);

                        error = deviceController.stop();
                        Log.e(TAG, "deviceController.stop() = " + error);
                        finish();
                    }
                }, 5000);

                unregisterReceivers();
            }
            // Do what you want with the device list
        }
    }

    private ARDiscoveryDevice createDiscoveryDevice(ARDiscoveryDeviceService service)
    {
        ARDiscoveryDevice device = null;
        if (service != null)
        {
            try
            {
                device = new ARDiscoveryDevice();

                ARDiscoveryDeviceBLEService bleDeviceService = (ARDiscoveryDeviceBLEService)service.getDevice();

                device.initBLE(ARDiscoveryService.getProductFromProductID(service.getProductID()), getApplicationContext(), bleDeviceService.getBluetoothDevice());
            }
            catch (ARDiscoveryException e)
            {
                e.printStackTrace();
                Log.e(TAG, "Error: " + e.getError());
            }
        }

        return device;
    }

    private ARDeviceController createDeviceController(ARDiscoveryDevice device) {
        ARDeviceController deviceController = null;

        try {
            deviceController = new ARDeviceController(device);
            deviceController.addListener(this);
            deviceController.addStreamListener(this);
        } catch (ARControllerException e) {
            e.printStackTrace();
            Log.e(TAG, e.getError().toString());
        }

        return deviceController;
    }

    private void unregisterReceivers()
    {
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());

        localBroadcastMgr.unregisterReceiver(mArdiscoveryServicesDevicesListUpdatedReceiver);
    }

    private void closeServices() {
        if (mArdiscoveryService != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mArdiscoveryService.stop();

                    getApplicationContext().unbindService(mArdiscoveryServiceConnection);
                    mArdiscoveryService = null;
                }
            }).start();
        }
    }

    @Override
    public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
        Log.e(TAG, "onStateChanged(" + newState + ")");
        switch (newState)
        {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                break;
            case ARCONTROLLER_DEVICE_STATE_STARTING:
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPING:
                break;

            default:
                break;
        }
    }

    @Override
    public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {

    }

    @Override
    public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
        if (elementDictionary != null)
        {
            // if the command received is a battery state changed
            if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED)
            {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);

                if (args != null)
                {
                    Integer batValue = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    Log.e(TAG, "Battery level: " + batValue);
                }
            }
        }
    }

    @Override
    public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, ARControllerCodec codec) {
        return null;
    }

    @Override
    public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, ARFrame frame) {
        return null;
    }

    @Override
    public void onFrameTimeout(ARDeviceController deviceController) {

    }
}