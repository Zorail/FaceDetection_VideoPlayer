package com.zorail.video_player.Service;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.android.gms.vision.Frame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraService {

    private Context context;
    private Activity activity;

    private String mCameraId;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private FrameSource frameSource;


    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    public CameraService(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }


    public void openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening");
            }
            Log.d("ThreadId1: ",Thread.currentThread().getId() + "");
            startBackgroundThread();
            Log.d("ThreadId2: ",Thread.currentThread().getId() + "");
            frameSource = new MLKitReceiver(mCameraId, context);
            manager.openCamera(mCameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

    private void startCapturing() {
        try {
            if (mCameraDevice == null) {
                return;
            }
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,@NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }
            };

            mCaptureSession.setRepeatingRequest(captureBuilder.build(),
                        captureCallback,
                        backgroundHandler
            );

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            for(String cameraId: manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                imageReader = ImageReader.newInstance(largest.getWidth() , largest.getHeight() , ImageFormat.YUV_420_888,2);
                imageReader.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);

                mCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void createCameraCaptureSession() {
        try {
            mCameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            startCapturing();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d("TAG", "Configuration Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = imageReader -> {
//            Logic to send the image to Firebase MLKIT goes here
        Log.d("ThreadId3: ",Thread.currentThread().getId() + "");
        Image image = imageReader.acquireLatestImage();
        Log.d("ThreadId4: ",Thread.currentThread().getId() + "");
        if (image != null) {
            Log.d("Height And Width ", " " + image.getHeight() + " " + image.getWidth() + "");
            convertYUV420888ToNV21(image);
            frameSource.onReceiveImage(image, activity);
            image.close();
        } else
            return;
    };

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    private byte[] quarterNV21(byte[] data, int iWidth, int iHeight) {
        // Reduce to quarter size the NV21 frame
        byte[] yuv = new byte[iWidth/4 * iHeight/4 * 3 / 2];
        // halve yuma
        int i = 0;
        for (int y = 0; y < iHeight; y+=4) {
            for (int x = 0; x < iWidth; x+=4) {
                yuv[i] = data[y * iWidth + x];
                i++;
            }
        }
        // halve U and V color components
        /*
        for (int y = 0; y < iHeight / 2; y+=4) {
            for (int x = 0; x < iWidth; x += 8) {
                yuv[i] = data[(iWidth * iHeight) + (y * iWidth) + x];
                i++;
                yuv[i] = data[(iWidth * iHeight) + (y * iWidth) + (x + 1)];
                i++;
            }
        }*/
        return yuv;
    }

    private byte[] convertYUV420888ToNV21(Image imgYUV420) {
        // Converting YUV_420_888 data to NV21.
        byte[] data;
        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();
        data = new byte[buffer0_size + buffer2_size];
        buffer0.get(data, 0, buffer0_size);
        buffer2.get(data, buffer0_size, buffer2_size);
        return data;
    }
}
