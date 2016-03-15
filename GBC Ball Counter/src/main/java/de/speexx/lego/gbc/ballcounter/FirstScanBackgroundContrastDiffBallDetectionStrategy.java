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
import android.media.Image;
import android.os.Build;
import android.util.Log;

public class FirstScanBackgroundContrastDiffBallDetectionStrategy implements BallDetectionStrategy {

    private static final String TAG = FirstScanBackgroundContrastDiffBallDetectionStrategy.class.getSimpleName();

    private static final String PREFS_KEY_ANDROID50_COMPATIBILITY = "android_50_compatibility";
    private static final boolean ANDROID50_COMPATIBILITY_DEFAULT = false;

    private BallDetectionStrategy mImpl;

    @Override
    public boolean hasImageScanChanged(final Image image) {
        if (this.mImpl == null) throw new IllegalStateException("onStart(Context) not called before hasImageScanChanged(Image)");
        return this.mImpl.hasImageScanChanged(image);
    }

    @Override
    public void onStart(final ContextContainer contextContainer) {
        if (checkForAndroid50Strategy(contextContainer)) {
            Log.i(TAG, "Android 5.0 (Lollipop) compatibility forced.");
            this.mImpl = new FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50();
        } else {
            this.mImpl = androidVersionBasedBallDetectionStrategy();
        }
        this.mImpl.onStart(contextContainer);
    }

    BallDetectionStrategy androidVersionBasedBallDetectionStrategy() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            Log.v(TAG, "Android Lollipop 5.1 or higher detected. Using luminance and chrominance support strategy for YUV image data.");
            return new FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid51();
        }
        Log.v(TAG, "Android Lollipop 5.0 detected. Using luminance only support strategy for YUV image data. See http://stackoverflow.com/questions/32927405/");
        return new FirstScanBackgroundContrastDiffBallDetectionStrategyAndroid50();
    }

    @Override
    public int getPreferencesId() {
        return androidVersionBasedBallDetectionStrategy().getPreferencesId();
    }

    final boolean checkForAndroid50Strategy(final ContextContainer contextContainer) {
        final SharedPreferences prefs = contextContainer.getPreferences();
        final boolean android50 = prefs.getBoolean(PREFS_KEY_ANDROID50_COMPATIBILITY, ANDROID50_COMPATIBILITY_DEFAULT);
        if (BuildConfig.DEBUG) {Log.v(TAG, "android_50_compatibility=" + android50);}
        return android50;
    }
}
