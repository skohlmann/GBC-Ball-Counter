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

import android.media.Image;

/**
 * Support for multiple different strategies to detect the balls in the capture image.
 */
public interface BallDetectionStrategy {
    /**
     * Will be called if and only if a new image was captured. The implementation will analyze,
     * how ever, the image and returns if <tt>true</tt> if and only if the strategy decides a
     * new ball was detected. <tt>False</tt> otherwise.
     * @param image a new captured image. The implementation should not call {@link Image#close()}
     * @return <tt>true</tt> if and only if a new ball was detected.
     */
    boolean hasImageScanChanged(final Image image);

    /**
     * Indicates a new capture session for ball detection.
     * Use the the implementation to initialize the detection strategy implementation.
     */
    void onStart();
}
