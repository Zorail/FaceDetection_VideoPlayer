package com.zorail.video_player.Service;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

import androidx.annotation.RequiresApi;

public class CameraSourcePreview {

    private static final String TAG = "CameraSourcePreview";

    private boolean mStartRequested;

    private Camera2Source mCameraSource;
    private int screenWidth;
    private int screenHeight;
    private int screenRotation;

    public CameraSourcePreview(Context context) {
        screenHeight = Utils.getScreenHeight(context);
        screenWidth = Utils.getScreenWidth(context);
        screenRotation = Utils.getScreenRotation(context);
        mStartRequested = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void start(Camera2Source camera2Source) {
        if(mCameraSource == null) { stop(); }
        mCameraSource = camera2Source;
        if(mCameraSource != null) {
            mStartRequested = true;
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stop() {
        mStartRequested = false;
        if(mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startIfReady() throws IOException {
        if(mStartRequested) {
            try {
                mCameraSource.start(screenRotation);
                mStartRequested = false;
            } catch (SecurityException e) {
                Log.d(TAG, "SECURITY EXCEPTION: "+e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
