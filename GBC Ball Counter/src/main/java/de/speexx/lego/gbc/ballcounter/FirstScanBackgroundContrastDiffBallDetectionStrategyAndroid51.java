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

import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.PREFS_KEY_CAPTURE_DELAY;
import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.PREFS_KEY_BRIGHTNESS_DIFFERENCE;
import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.PREFS_KEY_MINIMUM_DIFFERENCE_PERCENT;
import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.BRIGHTNESS_DIFFERENCE;
import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.MIN_DIFF_PERCENT;
import static de.speexx.lego.gbc.ballcounter.FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50.CAPTURE_DELAY_IN_MILLIS;


final class FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid51 implements BallDetectionStrategy {

    private static  final String TAG = FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid51.class.getSimpleName();

    private static final int Y_PLANE = 0;
    private static final int U_PLANE = 1;
    private static final int V_PLANE = 2;

    private static final String PREFS_KEY_WHITE_BALL_SUPPORT = "white_ball_support";
    private static final String PREFS_KEY_CHROMINANCE_MINIMUM_DIFFERENCE = "chrominance_minimum_difference";
    private static final int CHROMINANCE_MINIMUM_DIFFERENCE_DEFAULT = 20;
    private static final boolean WHITE_BALL_SUPPORT_DEFAULT = false;

    private int[] firstScanY = null;
    private int[] firstScanU = null;
    private int[] firstScanV = null;
    private byte[] scanY = null;
    private byte[] scanU = null;
    private byte[] scanV = null;

    private int[] chrominanceLookupTable = null;

    private int onePercent = 0;
    private boolean firstCall = true;
    private int scanIgnoreCountdown = 0;

    private int captureDelay = CAPTURE_DELAY_IN_MILLIS;
    private int luminanceMinimumDifference = BRIGHTNESS_DIFFERENCE;
    private int chrominanceMinimumDifference = CHROMINANCE_MINIMUM_DIFFERENCE_DEFAULT;
    private int minDiffPercent = MIN_DIFF_PERCENT;
    private boolean whiteBallSupported = false;

    private int imageCaptureDelay = GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS;
    private Semaphore scanChangedLock = new Semaphore(1);


    public boolean hasImageScanChanged(final Image image) {
        if (image == null) {
            Log.w(TAG, "Image is null.");
            return false;
        }

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            Log.w(TAG, "Image format (" + image.getFormat() + " not supported.");
            return false;
        }

        try {
            if (this.scanChangedLock.tryAcquire(this.captureDelay, TimeUnit.MILLISECONDS)) {
                try {
                    return checkForImageChange(image);
                } finally {
                    this.scanChangedLock.release();
                }
            } else {
                Log.w(TAG, "Lock acquire failed. Ignoring image from " + image.getTimestamp());
            }
        } catch (final IllegalArgumentException | InterruptedException e) {
            Log.w(TAG, "Unable to acquire scan lock.", e);
        }
        return false;
    }

    boolean checkForImageChange(final Image image) {
        if (this.firstCall) {
            firstCall(image);
            return false;
        }

        if (!isScanRequired()) {
            return false;
        }

        copyImageBufferToArray(image, this.scanY, this.scanU, this.scanV);
        if (compareImageArrays()) {
            calculateAndSetScanIgnoreCount();
            return true;
        }
        return false;
    }

    final void calculateAndSetScanIgnoreCount() {
        this.scanIgnoreCountdown = this.captureDelay / this.imageCaptureDelay;
    }

    final boolean compareImageArrays() {
        final int length = this.firstScanY.length;
        int diffCount = 0;
        final int cf = this.chrominanceMinimumDifference;
        for (int i = 0; i < length; i++) {
            final int chrominanceIndex = this.chrominanceLookupTable[i];

            final int uDiff = this.firstScanU[chrominanceIndex] - ((int) this.scanU[chrominanceIndex] & 0xff);
            final int absUDiff = uDiff < 0 ? -uDiff : uDiff;
            if (absUDiff > cf) {
                diffCount++;
            } else {
                final int vDiff = this.firstScanV[chrominanceIndex] - ((int) this.scanV[chrominanceIndex] & 0xff);
                final int absVDiff = vDiff < 0 ? -vDiff : vDiff;
                if (absVDiff > cf) {
                    diffCount++;
                } else if (this.whiteBallSupported) {
                    final int luminanceDiff = this.firstScanY[i] - ((int) this.scanY[i] &0xff);
                    final int absLuminanceDiff = luminanceDiff < 0 ? -luminanceDiff : luminanceDiff;
                    if (absLuminanceDiff > this.luminanceMinimumDifference) {
                        diffCount++;
                    }
                }
            }
        }

        final int diffPercent = diffCount / this.onePercent; // divide by 0 possible. Indicates other problems
        return diffPercent >= minDiffPercent;
    }

    final boolean isScanRequired() {
        if (this.scanIgnoreCountdown == 0) {
            return true;
        }
        this.scanIgnoreCountdown--;
        return false;
    }

    final void firstCall(final Image image) {
        assert image != null;

        final Image.Plane[] planes = image.getPlanes();
        this.firstScanY = new int[planes[Y_PLANE].getBuffer().capacity()];
        this.firstScanU = new int[planes[U_PLANE].getBuffer().capacity()];
        this.firstScanV = new int[planes[V_PLANE].getBuffer().capacity()];
        final byte[] byteY = new byte[this.firstScanY.length];
        final byte[] byteU = new byte[this.firstScanU.length];
        final byte[] byteV = new byte[this.firstScanU.length];
        this.scanY = new byte[this.firstScanY.length];
        this.scanU = new byte[this.firstScanU.length];
        this.scanV = new byte[this.firstScanV.length];

        this.onePercent = this.scanY.length / 100;
        this.chrominanceLookupTable = calculateChrominanceLookupTable(image);

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Image Y.length=" + this.firstScanY.length
                       + "; U.length=" + this.firstScanU.length
                       + "; V.length=" + this.firstScanV.length
                       + "; one percent=" + this.onePercent);
        }

        copyImageBufferToArray(image, byteY, byteU, byteV);
        toIntArray(byteY, byteU, byteV);

        this.firstCall = false;
    }

    private void toIntArray(byte[] byteY, byte[] byteU, byte[] byteV) {
        for (int i = 0; i < byteY.length; i++) {
            this.firstScanY[i] = ((int) byteY[i] &0xff);
        }
        for (int i = 0; i < byteU.length; i++) {
            this.firstScanU[i] = ((int) byteU[i] &0xff);
            this.firstScanV[i] = ((int) byteV[i] &0xff);
        }
    }

    /**
     * We are working with fixed image size. Because of this it makes sense to pre calculate the index lookup offsets
     * for the U and V planes.
     * <p>Tis means when iterating through an Y plane data array, fetching the corresponding U and V data is only
     * a lookup in the U and V plane array with the value of the Y index in the table returning by this method.</p>
     * <p>Example:</p>
     * <pre>
     *     final int[] lookupTable = calculateUvLookupTable(image);
     *
     *     final Image.Plane[] planes = image.getPlanes();
     *     final byte[] yPlaneData = new byte[planes[0].getBuffer().capacity()];
     *     final byte[] uPlaneData = new byte[planes[1].getBuffer().capacity()];
     *     final byte[] uPlaneData = new byte[uPlaneData.length];
     *
     *     for (int i = 0; i < yPlaneData.length; i++) {
     *         final byte y = yPlaneData[i];
     *         final byte u = uPlaneData[lookupTable[i]];
     *         final byte v = vPlaneData[lookupTable[i]];
     *     }
     * </pre>
     * <p>No arithmetic operations are required.</p>
     * <p>This works only with YUV 4:2:0 coded images.</p>
     * @param image the image data
     * @return lookup array for U and V values
     * @see ImageFormat#YUV_420_888
     */
    final int[] calculateChrominanceLookupTable(final Image image) {
        final int width = image.getWidth();
        final int height = image.getHeight();

        final Image.Plane[] planes = image.getPlanes();
        final int yPixelStride = planes[Y_PLANE].getPixelStride();
        final int yRowStride = planes[Y_PLANE].getRowStride();
        final int uPixelStride = planes[U_PLANE].getPixelStride();
        final int uRowStride = planes[U_PLANE].getRowStride();

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Y.pixel.Stride=" + yPixelStride
                    + "; U.pixel.Stride=" + uPixelStride
                    + "; Y.row.Stride=" + yRowStride
                    + "; U.row.Stride=" + uRowStride
                    + "; image width=" + width
                    + "; image height=" + height);
        }

        final int[] lookup = new int[width * height];

        for (int i = 0; i < height; i++) {
            final int halfH = i / 2;
            final int yOffset = yRowStride * i;
            final int uOffset = uRowStride * halfH;

            for (int j = 0; j < width; j++) {
                final int halfW = j / 2;
                final int yIndex = yOffset + (yPixelStride * j);
                final int uIndex = uOffset + (uPixelStride * halfW);
                lookup[yIndex] = uIndex;
            }
        }
        return lookup;
    }


    final void copyImageBufferToArray(final Image image, final byte[] arrayY, final byte[] arrayU, final byte[] arrayV) {
        final Image.Plane[] planes = image.getPlanes();
        planes[Y_PLANE].getBuffer().get(arrayY);
        planes[U_PLANE].getBuffer().get(arrayU);
        planes[V_PLANE].getBuffer().get(arrayV);
    }

    public void onStart(final ContextContainer contextContainer) {
        if (BuildConfig.DEBUG) {if (contextContainer == null) {throw new AssertionError("no context container");}}
        synchronized(this) {

            this.firstCall = true;
            this.scanY = null;
            this.scanU = null;
            this.scanV = null;
            this.firstScanY = null;
            this.firstScanU = null;
            this.firstScanV = null;

            configure(contextContainer);
        }
    }

    final void configure(final ContextContainer contextContainer) {
        final SharedPreferences prefs = contextContainer.getPreferences();

        if (BuildConfig.DEBUG) {if (prefs == null) {throw new AssertionError("no preferences available");}}

        this.captureDelay = prefs.getInt(PREFS_KEY_CAPTURE_DELAY, CAPTURE_DELAY_IN_MILLIS);
        this.luminanceMinimumDifference = prefs.getInt(PREFS_KEY_BRIGHTNESS_DIFFERENCE, BRIGHTNESS_DIFFERENCE);
        this.minDiffPercent = prefs.getInt(PREFS_KEY_MINIMUM_DIFFERENCE_PERCENT, MIN_DIFF_PERCENT);
        this.chrominanceMinimumDifference = prefs.getInt(PREFS_KEY_CHROMINANCE_MINIMUM_DIFFERENCE, CHROMINANCE_MINIMUM_DIFFERENCE_DEFAULT);
        this.whiteBallSupported = prefs.getBoolean(PREFS_KEY_WHITE_BALL_SUPPORT, WHITE_BALL_SUPPORT_DEFAULT);

        final GbcMainActivity mainActivity = contextContainer.getActivity();
        if (mainActivity == null) {
            Log.w(TAG, "GbcMainActivity reference is null. Fallback to default IMAGE_CAPTURE_DELAY_IN_MILLIS: " + GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS);
            this.imageCaptureDelay = GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS;
        } else {
            this.imageCaptureDelay = mainActivity.getImageCaptureDelayInMillis();
        }

        Log.v(TAG, "Settings: " + PREFS_KEY_CAPTURE_DELAY + "=" + this.captureDelay
                + "; " + PREFS_KEY_BRIGHTNESS_DIFFERENCE + "=" + this.luminanceMinimumDifference
                + "; " + PREFS_KEY_MINIMUM_DIFFERENCE_PERCENT + "=" + this.minDiffPercent
                + "; " + PREFS_KEY_WHITE_BALL_SUPPORT + "=" + this.whiteBallSupported
                + "; " + PREFS_KEY_CHROMINANCE_MINIMUM_DIFFERENCE + "=" + this.chrominanceMinimumDifference
                + "; imageCaptureDelay: "  + this.imageCaptureDelay);
    }

    public int getPreferencesId() {
        return R.xml.preferences_first_scan_background_contrast_diff_ball_detection_strategy_android51;
    }
}
