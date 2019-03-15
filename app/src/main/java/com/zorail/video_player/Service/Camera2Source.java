package com.zorail.video_player.Service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class Camera2Source {

    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;
    private int mFacing = CAMERA_FACING_FRONT;

    public static final int CAMERA_AF_AUTO = CaptureRequest.CONTROL_AF_MODE_AUTO;
    public static final int CAMERA_AF_EDOF = CaptureRequest.CONTROL_AF_MODE_EDOF;
    public static final int CAMERA_AF_MACRO = CaptureRequest.CONTROL_AF_MODE_MACRO;
    public static final int CAMERA_AF_OFF = CaptureRequest.CONTROL_AF_MODE_OFF;
    private int mFocusMode = CAMERA_AF_AUTO;

    private static final String TAG = "Camera2Source";
    private static final double ratioTolerance = 0.1;
    private static final double maxRatioTolerance = 0.18;
    private Context mContext;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private boolean cameraStarted = false;
    private int mSensorOrientation;

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

    private CameraDevice mCameraDevice;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private int mDisplayOrientation;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;

    private Size mPreviewSize;

    private String mCameraId;

    private Rect sensorArraySize;
    private boolean swappedDimensions = false;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private CameraManager manager = null;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Dedicated thread and associated runnable for calling into the detectot with frames,
     * as the frames become available from the camera.
     */
    private Thread mProcessingThread;
    private FrameProcessingRunnable mFrameProcessor;


    private ImageReader mImageReaderPreview;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private final ImageReader.OnImageAvailableListener monPreviewAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Image mImage = imageReader.acquireLatestImage();
            if(mImage == null) {
                return;
            }
            mFrameProcessor.setNextFrame(convertYUV420888ToNV21(mImage));
            mImage.close();
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            try {
                createCameraPreviewSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraPreviewSession() throws CameraAccessException {
        mImageReaderPreview = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 1);
        mImageReaderPreview.setOnImageAvailableListener(monPreviewAvailableListener, mBackgroundHandler);

        mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mPreviewRequestBuilder.addTarget(mImageReaderPreview.getSurface());
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
        Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        Log.d("TAG", fpsRanges + "");
        mPreviewRequest  = mPreviewRequestBuilder.build();

        mCameraDevice.createCaptureSession(Collections.singletonList(mImageReaderPreview.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                if(mCameraDevice == null)
                    return;
                mCaptureSession = cameraCaptureSession;
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                Log.d("TAG", "Configuration Failed");
            }
        }, mBackgroundHandler);
    }

    public static class Builder {
        private final Detector<?> mDetector;
        private Camera2Source mCameraSource = new Camera2Source();

        /**
         * Creates a camera source builder with the supplied context and detector.  Camera preview
         * images will be streamed to the associated detector upon starting the camera source.
         */
        public Builder(Context context, Detector<?> detector) {
            if (context == null) {
                throw new IllegalArgumentException("No context supplied.");
            }
            if (detector == null) {
                throw new IllegalArgumentException("No detector supplied.");
            }

            mDetector = detector;
            mCameraSource.mContext = context;
        }

        public Builder setFocusMode(int mode) {
            mCameraSource.mFocusMode = mode;
            return this;
        }

        public Builder setFacing(int facing) {
            if ((facing != CAMERA_FACING_BACK) && (facing != CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid camera: " + facing);
            }
            mCameraSource.mFacing = facing;
            return this;
        }

        public Camera2Source build() {
            mCameraSource.mFrameProcessor = mCameraSource.new FrameProcessingRunnable(mDetector);
            return mCameraSource;
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            if(mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the camera and releases the resources of the camera and underlying detector.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void release() {
        mFrameProcessor.release();
        stop();
    }

    /**
     * Closes the camera and stops sending frames to the underlying frame detector.
     * <p/>
     * This camera source may be restarted again by calling {@link //start(AutoFitTextureView, int)}.
     * <p/>
     * Call {@link #release()} instead to completely shut down this camera source and release the
     * resources of the underlying detector.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stop() {
        try {
            mFrameProcessor.setActive(false);
            if(mProcessingThread != null) {
                try {
                    // Wait for the thread to complete to ensure that we can't have multiple threads
                    // executing at the same time (i.e., which would happen if we called start too
                    // quickly after stop).
                    mProcessingThread.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame processing thread interrupted on release.");
                }
                mProcessingThread = null;
            }
            mCameraOpenCloseLock.acquire();
            if(null != mCaptureSession) {
                mCaptureSession.close();
                mCameraDevice = null;
            }
            if(null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if(null != mImageReaderPreview) {
                mImageReaderPreview.close();
                mImageReaderPreview = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Camera2Source start(int displayOrientation) throws IOException, CameraAccessException, InterruptedException {
        mDisplayOrientation = displayOrientation;
        if(ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (cameraStarted) {
                return this;
            }
            cameraStarted = true;
            startBackgroundThread();

            mProcessingThread = new Thread(mFrameProcessor);
            mFrameProcessor.setActive(true);
            mProcessingThread.start();

//            mTextureView = textureView;
//            if (mTextureView.isAvailable()) {
//                setUpCameraOutputs(mTextureView.getWidth(), mTextureView.getHeight());
//            }
            int width = Utils.getScreenWidth(mContext);
            int height = Utils.getScreenHeight(mContext);
            setUpCameraOutputs(width, height);
        }
        return this;
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setUpCameraOutputs(int width, int height) {
        try {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (manager == null)
                manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            mCameraId = manager.getCameraIdList()[mFacing];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }

            sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer maxAFRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

            Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation != null) {
                mSensorOrientation = sensorOrientation;
                switch (mDisplayOrientation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + mDisplayOrientation);

                }
            }

            Point displaySize = new Point(Utils.getScreenWidth(mContext), Utils.getScreenHeight(mContext));
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if(swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;

                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            Size[] outputSizes = Utils.sizeToSize(map.getOutputSizes(SurfaceTexture.class));
            Size largest = getBestAspectPictureSize(map.getOutputSizes(ImageFormat.JPEG));
            mPreviewSize = chooseOptimalSize(outputSizes, rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch(CameraAccessException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        } catch(NullPointerException e) {
            Log.d(TAG, "Camera Error: "+e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Size getBestAspectPictureSize(android.util.Size[] supportedPictureSizes) {
        float targetRatio = Utils.getScreenRatio(mContext);
        Size bestSize = null;
        TreeMap<Double, List<android.util.Size>> diffs = new TreeMap<>();

        //Select supported sizes which ratio is less than ratioTolerance
        for (android.util.Size size : supportedPictureSizes) {
            float ratio = (float) size.getWidth() / size.getHeight();
            double diff = Math.abs(ratio - targetRatio);
            if (diff < ratioTolerance){
                if (diffs.keySet().contains(diff)){
                    //add the value to the list
                    diffs.get(diff).add(size);
                } else {
                    List<android.util.Size> newList = new ArrayList<>();
                    newList.add(size);
                    diffs.put(diff, newList);
                }
            }
        }

        //If no sizes were supported, (strange situation) establish a higher ratioTolerance
        if(diffs.isEmpty()) {
            for (android.util.Size size : supportedPictureSizes) {
                float ratio = (float)size.getWidth() / size.getHeight();
                double diff = Math.abs(ratio - targetRatio);
                if (diff < maxRatioTolerance){
                    if (diffs.keySet().contains(diff)){
                        //add the value to the list
                        diffs.get(diff).add(size);
                    } else {
                        List<android.util.Size> newList = new ArrayList<>();
                        newList.add(size);
                        diffs.put(diff, newList);
                    }
                }
            }
        }

        //Select the highest resolution from the ratio filtered ones.
        for (Map.Entry entry: diffs.entrySet()){
            List<?> entries = (List) entry.getValue();
            for (int i=0; i<entries.size(); i++) {
                android.util.Size s = (android.util.Size) entries.get(i);
                if(bestSize == null) {
                    bestSize = new Size(s.getWidth(), s.getHeight());
                } else if(bestSize.getWidth() < s.getWidth() || bestSize.getHeight() < s.getHeight()) {
                    bestSize = new Size(s.getWidth(), s.getHeight());
                }
            }
        }
        return bestSize;
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }

    }

        /**
         * This runnable controls access to the underlying receiver, calling it to process frames when
         * available from the camera.  This is designed to run detection on frames as fast as possible
         * (i.e., without unnecessary context switching or waiting on the next frame).
         * <p/>
         * While detection is running on a frame, new frames may be received from the camera.  As these
         * frames come in, the most recent frame is held onto as pending.  As soon as detection and its
         * associated processing are done for the previous frame, detection on the mostly recently
         * received frame will immediately start on the same thread.
         */
    private class FrameProcessingRunnable implements Runnable {
        private Detector<?> mDetector;
        private long mStartTimeMillis = SystemClock.elapsedRealtime();

        // This lock guards all of the member variables below.
        private final Object mLock = new Object();
        private boolean mActive = true;

        // These pending variables hold the state associated with the new frame awaiting processing.
        private long mPendingTimeMillis;
        private int mPendingFrameId = 0;
        private byte[] mPendingFrameData;

        FrameProcessingRunnable(Detector<?> detector) {
            mDetector = detector;
        }

        @SuppressLint("Assert")
        void release() {
            assert (mProcessingThread.getState() == Thread.State.TERMINATED);
            mDetector.release();
            mDetector = null;
        }

        void setActive(boolean active) {
            synchronized (mLock) {
                mActive = active;
                mLock.notifyAll();
            }
        }

        void setNextFrame(byte[] data) {
            synchronized (mLock) {
                if (mPendingFrameData != null) {
                    mPendingFrameData = null;
                }

                // Timestamp and frame ID are maintained here, which will give downstream code some
                // idea of the timing of frames received and when frames were dropped along the way.
                mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
                mPendingFrameId++;
                mPendingFrameData = data;

                // Notify the processor thread if it is waiting on the next frame (see below).
                mLock.notifyAll();
            }
        }

        @Override
        public void run() {
            Frame outputFrame;

            while(true) {
                synchronized (mLock) {
                    while(mActive && (mPendingFrameData == null)) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }
                    if (!mActive) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return;
                    }
                    outputFrame = new Frame.Builder()
                            .setImageData(ByteBuffer.wrap(quarterNV21(mPendingFrameData, mPreviewSize.getWidth(), mPreviewSize.getHeight())), mPreviewSize.getWidth()/4, mPreviewSize.getHeight()/4, ImageFormat.NV21)
                            .setId(mPendingFrameId)
                            .setTimestampMillis(mPendingTimeMillis)
                            .setRotation(getDetectorOrientation(mSensorOrientation))
                            .build();

                    // We need to clear mPendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    mPendingFrameData = null;
                }

                try {
                    mDetector.receiveFrame(outputFrame);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                }
            }
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private int getDetectorOrientation(int sensorOrientation) {
        switch (sensorOrientation) {
            case 0:
                return Frame.ROTATION_0;
            case 90:
                return Frame.ROTATION_90;
            case 180:
                return Frame.ROTATION_180;
            case 270:
                return Frame.ROTATION_270;
            case 360:
                return Frame.ROTATION_0;
            default:
                return Frame.ROTATION_90;
        }
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
        return yuv;
    }

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
