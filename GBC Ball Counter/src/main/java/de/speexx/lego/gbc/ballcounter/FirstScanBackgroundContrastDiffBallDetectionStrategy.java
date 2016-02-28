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


/**
 * The strategy detects the luma component (the brightness) different of a given first
 * captured image and all following images.
 * <p>Only {@link ImageFormat#YUV_420_888} is supported.</p>
 * <p><strong>Note: </strong> Due to a given issue in Android API 21 (Android 5.0.x), no color
 * changes will be detected. Feel free to enhance the code for API 22 (Android 5.1.x) or higher.
 * See also <a href='http://stackoverflow.com/questions/32927405/converting-yuv-image-to-rgb-results-in-greenish-picture'>Converting YUV image to RGB results in greenish picture</a></p>
 */
public final class FirstScanBackgroundContrastDiffBallDetectionStrategy implements BallDetectionStrategy {

    private static  final String TAG = FirstScanBackgroundContrastDiffBallDetectionStrategy.class.getSimpleName();

    private static final int BRIGHTNESS_DIFFERENCE = 30;
    private static final int MIN_DIFF_PERCENT = 5;
    private static final int CAPTURE_DELAY_IN_MILLIS = 200;

    private byte[] firstScan = null;
    private byte[] scan = null;
    private int onePercent = 0;
    private boolean firstCall = true;
    private int scanIgnoreCountdown = 0;

    private int captureDelay = CAPTURE_DELAY_IN_MILLIS;
    private int brightnessDifference = BRIGHTNESS_DIFFERENCE;
    private int minDiffPercent = MIN_DIFF_PERCENT;

    private int imageCaptureDelay = GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS;

    public boolean hasImageScanChanged(final Image image) {
        if (image == null) {
            Log.w(TAG, "Image is null.");
            return false;
        }

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            Log.w(TAG, "Image format (" + image.getFormat() + " not supported.");
            return false;
        }

        synchronized(this) {
            if (this.firstCall) {
                firstCall(image);
                return false;
            }

            if (!isScanRequired()) {
                return false;
            }

            copyImageBufferToArray(image, this.scan);
            if (compareImageArrays()) {
                calculateAndSetScanIgnoreCount();
                return true;
            }
        }
        return false;
    }

    final void calculateAndSetScanIgnoreCount() {
        this.scanIgnoreCountdown = this.captureDelay / this.imageCaptureDelay;
    }

    final boolean compareImageArrays() {
        if (BuildConfig.DEBUG) {if (this.firstScan == null || this.scan == null) {throw new AssertionError("this.firstScan == null || this.scan == null");}}
        if (BuildConfig.DEBUG) {if (this.scan.length != this.firstScan.length) {throw new AssertionError("this.scan.length != this.firstScan.length");}}

        final int length = this.firstScan.length;
        int diffCount = 0;
        for (int i = 0; i < length; i++) {
            final int diff = this.firstScan[i] - this.scan[i];
            final int absDiff = diff < 0 ? -diff : diff;
            if (absDiff > this.brightnessDifference) {
                diffCount++;
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
        this.firstScan = new byte[image.getHeight() * image.getWidth()];
        this.scan = new byte[image.getHeight() * image.getWidth()];
        this.onePercent = this.scan.length / 100;
        copyImageBufferToArray(image, this.firstScan);
        this.firstCall = false;
    }

    final void copyImageBufferToArray(final Image image, final byte[] array) {
        assert image != null;
        assert array != null;

        image.getPlanes()[0].getBuffer().get(array);
    }

    public void onStart(final ContextContainer contextContainer) {
        if (BuildConfig.DEBUG) {if (contextContainer == null) {throw new AssertionError("no context container");}}
        synchronized(this) {

            this.firstCall = true;
            this.scan = null;
            this.firstScan = null;

            configure(contextContainer);
        }
    }

    final void configure(final ContextContainer contextContainer) {
        final SharedPreferences prefs = contextContainer.getPreferences();

        if (BuildConfig.DEBUG) {if (prefs == null) {throw new AssertionError("no preferences available");}}

        this.captureDelay = prefs.getInt("capture_delay", CAPTURE_DELAY_IN_MILLIS);
        this.brightnessDifference = prefs.getInt("brightness_difference", BRIGHTNESS_DIFFERENCE);
        this.minDiffPercent = prefs.getInt("minimum_difference_percent", MIN_DIFF_PERCENT);

        final GbcMainActivity mainActivity = contextContainer.getActivity();
        if (mainActivity == null) {
            Log.w(TAG, "GbcMainActivity reference is null. Fallback to default IMAGE_CAPTURE_DELAY_IN_MILLIS: " + GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS);
            this.imageCaptureDelay = GbcMainActivity.IMAGE_CAPTURE_DELAY_IN_MILLIS;
        } else {
            this.imageCaptureDelay = mainActivity.getImageCaptureDelayInMillis();
        }

        Log.v(TAG, "Settings: capture_delay=" + this.captureDelay
                + "; brightness_difference=" + this.brightnessDifference
                + "; minimum_difference_percent=" + this.minDiffPercent
                + "; imageCaptureDelay: "  + this.imageCaptureDelay);
    }

    public int getPreferencesId() {
        return R.xml.preferences_first_scan_background_contrast_diff_ball_detection_strategy;
    }
}
