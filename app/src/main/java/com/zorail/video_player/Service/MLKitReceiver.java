package com.zorail.video_player.Service;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.util.List;

import androidx.annotation.RequiresApi;

import static android.content.Context.CAMERA_SERVICE;

public class MLKitReceiver implements FrameSource {

    String cameraId;
    Context context;

    MLKitReceiver(String cameraId, Context context) {
        this.cameraId = cameraId;
        this.context = context;
        FirebaseApp.initializeApp(context);
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId, Activity activity, Context context)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceiveImage(Image image, Activity activity) {
        try {
            int rotation = getRotationCompensation(cameraId, activity, context);
            runFaceDetector(image, rotation);
        } catch (CameraAccessException e) {

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void runFaceDetector(Image image, int rotation) {
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .enableTracking()
                .build();

        FirebaseVisionImage visionImage = FirebaseVisionImage.fromMediaImage(image, rotation);
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        detector.detectInImage(visionImage)
                .addOnSuccessListener(this::getDataFromFace)
                .addOnFailureListener(e -> {

                });
    }

    private void getDataFromFace(List<FirebaseVisionFace> faces) {
        float rightEyeOpenProbability = 0;
        float leftEyeOpenProbability = 0;

        for(FirebaseVisionFace face: faces) {
            if(face.getLeftEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                leftEyeOpenProbability = face.getLeftEyeOpenProbability();
            }

            if(face.getRightEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                rightEyeOpenProbability = face.getRightEyeOpenProbability();
            }
            Log.d("TAG", leftEyeOpenProbability + " " + rightEyeOpenProbability);

//            if(leftEyeOpenProbability > 0.5) {
//
//            }
//
//            if(rightEyeOpenProbability > 0.5) {
//
//            }
        }
    }
}
