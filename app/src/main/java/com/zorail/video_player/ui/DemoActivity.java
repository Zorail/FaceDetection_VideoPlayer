package com.zorail.video_player.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import com.zorail.video_player.Service.CameraService;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DemoActivity extends AppCompatActivity {

    CameraService cameraService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        cameraService = new CameraService(getApplicationContext());
        getPermissions();
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        cameraService.openCamera();
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        cameraService.closeCamera();
//    }


    @Override
    protected void onStop() {
        super.onStop();
        cameraService.closeCamera();
    }

    private void getPermissions(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                //Requesting permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                cameraService.openCamera();
            }
        }
    }

    @Override //Override from ActivityCompat.OnRequestPermissionsResultCallback Interface
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted
                    cameraService.openCamera();
                }
                return;
            }
        }
    }
}
