package com.zorail.video_player.ui.video;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.zorail.video_player.Service.Camera2Source;
import com.zorail.video_player.Service.CameraSourcePreview;
import com.zorail.video_player.device.player.MediaPlayerImpl;


import java.lang.ref.WeakReference;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)

public class VideoViewPresenter implements VideoViewContract.Presenter {

    private WeakReference view;
    VideoViewContract.View viewContract;
    private MediaPlayerImpl mediaPlayer;
    private FaceDetector previewFaceDetector;
    private Camera2Source mCameraSource;
    private CameraSourcePreview mPreview;
    Context context;

    VideoViewPresenter(VideoViewContract.View videoViewView, Context context) {
        this.viewContract=videoViewView;
        this.context = context;
        view = new WeakReference(videoViewView);
        mediaPlayer = new MediaPlayerImpl();
        mPreview = new CameraSourcePreview(context);
    }
    @Override
    public void deactivate() {
        if(mPreview != null) {
            mPreview = null;
        }
    }

    @Override
    public MediaPlayerImpl getPlayer() {
        return mediaPlayer;
    }


    public void camera_use(Context context, Activity activity) {
        createCameraSourceFront();
    }

    @Override
    public void closeCamera() {
        stopCameraSource();
    }

    @Override
    public void play(String url) {
        if(url!=null) {
            mediaPlayer.play(url);
        }
    }

    @Override
    public void releasePlayer() {
        mediaPlayer.releasePlayer();
        stopCameraSource();
    }

    @Override
    public void setMediaSessionState(Boolean isActive) {
        mediaPlayer.setMediaSessionState(isActive);
    }

    private void createCameraSourceFront() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new FaceTrackerFactory()).build());
        } else {
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
        }
        mCameraSource = new Camera2Source.Builder(context, previewFaceDetector)
                .setFocusMode(Camera2Source.CAMERA_AF_AUTO)
                .setFacing(Camera2Source.CAMERA_FACING_FRONT)
                .build();

        startCameraSource();

    }

    private void startCameraSource() {
        if(mCameraSource != null) {
            mPreview.start(mCameraSource);
        }
    }

    private void stopCameraSource() {
        if(mPreview != null) {
            mPreview.stop();
        }
    }

    private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {

        @Override
        public Tracker<Face> create(Face face) {
            decideAndChangeStateOfPlayer(face);
            return null;
        }
    }

    private void decideAndChangeStateOfPlayer(Face face) {
        float left = face.getIsLeftEyeOpenProbability();
        float right = face.getIsRightEyeOpenProbability();
        float eulerZ = face.getEulerZ();
        float eulerY = face.getEulerY();
        Log.d("TAG", " " + eulerY + " " + eulerZ);
        if((left+right) < 0.5) {
            if(mediaPlayer.getPlayerState()) {
                mediaPlayer.pausePlayer();
            }
        } else {
            if(!mediaPlayer.getPlayerState()) {
                mediaPlayer.playPlayer();
            }
        }
    }
}
