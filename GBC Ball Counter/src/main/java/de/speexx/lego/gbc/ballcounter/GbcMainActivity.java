/*
 * Copyright (c) 2016 Sascha Kohlmann. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.speexx.lego.gbc.ballcounter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GbcMainActivity extends AppCompatActivity {

    private static final String TAG = GbcMainActivity.class.getSimpleName();

    /** The delay in milliseconds between two captures of a ball detection image.
     * @see #mImageCaptureTask */
    public static final int IMAGE_CAPTURE_DELAY_IN_MILLIS = 100;

    private static final int CAPTURE_FORMAT = ImageFormat.YUV_420_888;

    /** Count of balls detected by the system. */
    private int mBallCount;

    /** The image format to capture. At this time its {@link ImageFormat.YUV_420_888}. Other formats are not supported. */
    private int mCaptureImageFormat;

    /** Indicates a session for counting balls. Will be set to <tt>true</tt> to start a session. Normally user triggered. */
    private boolean mIsCountSession = false;

    /** Time counter for elapsed time during a count session. */
    private Chronometer mDurationChrono;

    /** The view for the {@link #mBallCount} value. */
    private TextView mBallCountView;

    /** Capture builder. */
    private CaptureRequest.Builder mPreviewBuilder, mCaptureBuilder;

    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mCaptureRequest;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Size mPreviewSize;

    /** Reader for the ball detection image. */
    private ImageReader mCaptureReader; // don't convert to local variable. Otherwise GC will clean up weak reference

    /** The ID of the camera to capture the ball detection images. */
    private String mCameraId;

    /** The camera device to capture the ball detection images. */
    private CameraDevice mCameraDevice;

    /** View for the preview image of the camera. */
    private AutoFitTextureView mPreviewTextureView;

    /** The strategy implementation to detect ball in the ball detection capture image. */
    private final BallDetectionStrategy mBallDetectionStrategy = new FirstScanBackgroundContrastDiffBallDetectionStrategy();
    private final CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(final CameraDevice camera) {
            GbcMainActivity.this.mCameraDevice = camera;
            GbcMainActivity.this.startPreview();
        }

        @Override
        public void onDisconnected(final CameraDevice camera) {
            camera.close();
            GbcMainActivity.this.mCameraDevice = null;
            doToast("Camera disconnected");
        }

        @Override
        public void onError(final CameraDevice camera, final int error) {
            camera.close();
            GbcMainActivity.this.mCameraDevice = null;
            doToast("Close Camera cause of error (" + error + ")");
        }
    };
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
        }
    };

    /** Handles te capture images to detect balls. */
    private final OnImageAvailableListener mImageAvailableListener = new OnImageAvailableListener() {
//        private boolean firstCall = true;
        @Override
        public void onImageAvailable(final ImageReader reader) {
            try (final Image image = reader.acquireLatestImage()) {
                if (image != null) { // Should(!) never happen :)
/*
                    if (this.firstCall) {
                        mBackgroundHandler.post(new ImageSaver(image, GbcMainActivity.this));
                        this.firstCall = false;
                    }
*/
                    if (GbcMainActivity.this.mBallDetectionStrategy.hasImageScanChanged(image)) {
                        increaseBallCount();
                    }
                }
            } catch (final IllegalArgumentException ex) {
                toInfoText("new image - Exception: " + ex.getMessage());
            }
        }
    };

    /** Task to capture new images at a given time. See also {@link #IMAGE_CAPTURE_DELAY_IN_MILLIS}. */
    private final Runnable mImageCaptureTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (GbcMainActivity.this.mCaptureSession != null && GbcMainActivity.this.mCaptureRequest != null && GbcMainActivity.this.mIsCountSession) {
                    GbcMainActivity.this.mCaptureSession.capture(GbcMainActivity.this.mCaptureRequest, null, null);
                }
            } catch (final CameraAccessException ex) {
                ex.printStackTrace();
            } finally {
                GbcMainActivity.this.mBackgroundHandler.postDelayed(GbcMainActivity.this.mImageCaptureTask, IMAGE_CAPTURE_DELAY_IN_MILLIS);
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gbc_main);

        this.mPreviewTextureView = (AutoFitTextureView) findViewById(R.id.camera_view);

        this.mDurationChrono = (Chronometer) findViewById(R.id.duration_view);
        findViewById(R.id.startstop_toggle_button).setOnClickListener(new View.OnClickListener() {
                                                               @Override
                                                               public void onClick(final View v) {
                                                                   GbcMainActivity.this.mIsCountSession ^= true;
                                                                   if (GbcMainActivity.this.mIsCountSession) {
                                                                       GbcMainActivity.this.resetDuration();
                                                                       if (GbcMainActivity.this.mBallDetectionStrategy != null) {
                                                                           GbcMainActivity.this.mBallDetectionStrategy.onStart();
                                                                       }
                                                                       GbcMainActivity.this.mDurationChrono.start();
                                                                   } else {
                                                                       GbcMainActivity.this.mDurationChrono.stop();
                                                                   }
                                                               }
        });
        this.mBallCountView = (TextView) findViewById(R.id.ball_count_value);
        findViewById(R.id.reset_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                GbcMainActivity.this.mBallCount = 0;
                GbcMainActivity.this.updateBallCount();
                GbcMainActivity.this.resetDuration();
            }
        });
    }

    final void startCaptureTask() {
        this.mImageCaptureTask.run();
    }

    void stopCaptureTask() {
        this.mBackgroundHandler.removeCallbacks(this.mImageCaptureTask);
    }

    final void increaseBallCount() {
        if (this.mIsCountSession) {
            this.mBallCount++;
            updateBallCount();
        }
    }

    final void updateBallCount() {
        assert this.mBallCountView != null;
        this.mBallCountView.setText(getString(R.string.ball_value, this.mBallCount));
    }

    final void resetDuration() {
        GbcMainActivity.this.mDurationChrono.setBase(SystemClock.elapsedRealtime());
    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (!this.mPreviewTextureView.isAvailable()) {
            this.mPreviewTextureView.setSurfaceTextureListener(this.mSurfaceTextureListener);
        }
        startCaptureTask();
    }

    @Override
    public void onStop() {
        stopCaptureTask();
        stopBackgroundThread();
        super.onStop();
    }

    final void supportedImageFormat(final StreamConfigurationMap streamConfigs) {
        assert  streamConfigs != null;
        for (final int format : streamConfigs.getOutputFormats()) {
            if (format == CAPTURE_FORMAT) {
                this.mCaptureImageFormat = format;
                return;
            }
        }
        Log.e(TAG, "Image Formats: " + Arrays.toString(streamConfigs.getOutputFormats()));
        final String errorMsg = getString(R.string.image_format_not_supported, "YUV_420_888", CAPTURE_FORMAT);
        doToast(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    final void startPreview() {
        if (this.mCameraDevice == null || !this.mPreviewTextureView.isAvailable() || this.mPreviewSize == null) {
            return;
        }
        try {
            final SurfaceTexture texture = this.mPreviewTextureView.getSurfaceTexture();

            assert texture != null;
            final List<Surface> surfaces = new ArrayList<>();
            texture.setDefaultBufferSize(this.mPreviewSize.getWidth(), this.mPreviewSize.getHeight());

            this.mPreviewBuilder = this.mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            this.mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            this.mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO);

            final Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            this.mPreviewBuilder.addTarget(previewSurface);

            final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            final CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(this.mCameraId);
            final StreamConfigurationMap streamConfigs = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            supportedImageFormat(streamConfigs);
            final Size[] captureSize = streamConfigs.getOutputSizes(this.mCaptureImageFormat);
            final Size minimumCaptureSize = minimumCaptureSize(captureSize);

            this.mCaptureBuilder = this.mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            this.mCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            this.mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO);

            this.mCaptureReader = ImageReader.newInstance(minimumCaptureSize.getWidth(), minimumCaptureSize.getHeight(), this.mCaptureImageFormat, 1);
            this.mCaptureReader.setOnImageAvailableListener(this.mImageAvailableListener, null);

            final Surface captureSurface = mCaptureReader.getSurface();
            surfaces.add(captureSurface);
            this.mCaptureBuilder.addTarget(captureSurface);

            this.mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    GbcMainActivity.this.mCaptureSession = cameraCaptureSession;
                    try {

                        final CaptureRequest previewRequest = GbcMainActivity.this.mPreviewBuilder.build();
                        GbcMainActivity.this.mCaptureRequest = GbcMainActivity.this.mCaptureBuilder.build();
                        GbcMainActivity.this.mCaptureSession.setRepeatingBurst(Arrays.asList(previewRequest),
                                null,
                                GbcMainActivity.this.mBackgroundHandler);

                    } catch (final CameraAccessException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    doToast("Failed CameraCaptureSession configuration");
                }
            }, GbcMainActivity.this.mBackgroundHandler);
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /** Starts a background thread and its {@link Handler}. */
    final void startBackgroundThread() {
        this.mBackgroundThread = new HandlerThread("GbcMainBackground");
        this.mBackgroundThread.start();
        this.mBackgroundHandler = new Handler(this.mBackgroundThread.getLooper());
    }

    /** Stops the background thread and its {@link Handler}. */
    final void stopBackgroundThread() {
        this.mBackgroundThread.quitSafely();
        try {
            this.mBackgroundThread.join();
            this.mBackgroundThread = null;
            this.mBackgroundHandler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

    final void setupCamera(final int width, final int height) {
        final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            final String[] cameraIdList = cameraManager.getCameraIdList();
            for (final String cId : cameraIdList) {
                final CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    final StreamConfigurationMap streamConfigs = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    this.mCameraId = cId;

                    final Size aspectRatio = chooseVideoSize(streamConfigs.getOutputSizes(MediaRecorder.class));
                    this.mPreviewSize = chooseOptimalSize(streamConfigs.getOutputSizes(SurfaceTexture.class), width, height, aspectRatio);
                    final int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        this.mPreviewTextureView.setAspectRatio(this.mPreviewSize.getWidth(), this.mPreviewSize.getHeight());
                    } else {
                        this.mPreviewTextureView.setAspectRatio(this.mPreviewSize.getHeight(), this.mPreviewSize.getWidth());
                    }

                    return;
                }
            }
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }

    final Size chooseOptimalSize(final Size[] choices, final int width, final int height, final Size aspectRatio) {
        final List<Size> bigEnough = new ArrayList<>();
        final int w = aspectRatio.getWidth();
        final int h = aspectRatio.getHeight();
        for (final Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    final Size chooseVideoSize(final Size[] choices) {
        for (final Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    final Size minimumCaptureSize(final Size[] sizes) {
        int width = Integer.MAX_VALUE;
        Size retval = null;
        for (final Size size : sizes) {
            if (size.getWidth() < width) {
                width = size.getWidth();
                retval = size;
            }
        }
        return retval;
    }

    final void openCamera() {
        final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(this.mCameraId, this.mCameraDeviceStateCallback, null);
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }

    final void toInfoText(final String text) {
        if (text == null) {
            return;
        }
        final TextView infoText = (TextView) this.findViewById(R.id.info_text);
        infoText.setText(text);
    }

    final void doToast(final String message) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    final static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    
/*
    private static class ImageSaver implements Runnable {

        private final Image mImage;
        private final Activity mActivity;

        public ImageSaver(final Image image, final Activity activity) {
            this.mImage = image;
            this.mActivity = activity;
        }

        @Override
        public void run() {
            if (mImage.getFormat() != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format (" + mImage.getFormat() +"). " + ImageFormat.YUV_420_888 + " required.");
                return;
            }
            final ByteBuffer yBuffer = mImage.getPlanes()[0].getBuffer();
            yBuffer.rewind();
            final byte[] yBytes = new byte[yBuffer.remaining()];
            yBuffer.get(yBytes);
            yBuffer.rewind();

            final ByteBuffer uBuffer = mImage.getPlanes()[1].getBuffer();
            uBuffer.rewind();
            final byte[] uBytes = new byte[uBuffer.remaining()];
            uBuffer.get(uBytes);
            uBuffer.rewind();

            final ByteBuffer vBuffer = mImage.getPlanes()[2].getBuffer();
            vBuffer.rewind();
            final byte[] vBytes = new byte[vBuffer.remaining()];
            vBuffer.get(vBytes);
            vBuffer.rewind();

            final long current = System.currentTimeMillis();
            final File yFile = new File(this.mActivity.getExternalFilesDir(null), "y" + current + ".yuv");
            final File uFile = new File(this.mActivity.getExternalFilesDir(null), "u" + current + ".yuv");
            final File vFile = new File(this.mActivity.getExternalFilesDir(null), "v" + current + ".yuv");
            try (final FileOutputStream yOutput = new FileOutputStream(yFile);
                 final FileOutputStream uOutput = new FileOutputStream(uFile);
                 final FileOutputStream vOutput = new FileOutputStream(vFile)) {
                yOutput.write(yBytes);
                uOutput.write(uBytes);
                vOutput.write(vBytes);
            } catch (final IOException e) {
                Log.e(TAG, "Unable to save file.", e);
            }
        }
    }
*/
}
