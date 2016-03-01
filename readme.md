# GBC Ball Counter
The GBC (Great Ball Contraption) Ball Counter is a small Android (5.0.x or higher) application to count Lego&#174; balls passing. This is done by comparing two images captured by the back camera of an Android device.

## Installation
The application is currently not available via the Google Play store.

To install the application two steps are required:

1. Download the debuggable application (APK file) of the [current release](http://www.speexx.de/gbc/GBC%20Ball%20Counter-debug.apk) from my website or build the application from scratch.
2. Install the application manually on your Android device (5.0.x or higher).

To install the application manually you must grant the permission for local installations of APK files in your device settings.

For Android 5.0 devices navigate to <tt>Settings -> Security -> Unknown Origin</tt> and enable the setting.
 
Now you might transfer the <tt>GBC Ball Counter-debug.apk</tt> via Bluetooth to your device.
 
<strong>Note:</strong> The installation process might differ for your device. Please search in the web how it works for your device in detail.

# How it works?
The implementation is based on the following hypothesis:

1. The background of the passed ball is created with black Lego&#174; bricks.
2. Passed balls have a different color than the black background.
3. Passed balls are brighter than the black background.
4. Passing the camera does not take more than 200 milliseconds.

The camera captures every 100 milliseconds an image (*Capture delay*). This captured image is scanned pixel by pixel for difference in brightness (chrominance). The difference is calculated against the *first captured image* after starting the counter. If the difference in brightness exceeds a threshold (*Brightness difference*) it will increase the counter for *detected pixel of brightness change*.

The count of detected pixel of brightness changes must also exceed a threshold against all pixels of the detected image (*Minimum difference in percent*). If and only if this threshold ist also exceeded, the ball counter will be increased by one. 

If and only if the ball counter was increased, the detection algorithm waits for 200 milliseconds to capture the next image (*Capture after detection delay*).

The values for 

* Capture delay
* Brightness difference
* Minimum difference in percent
* Capture delay

can be changed in the settings dialog.

## Limitations
Due to an [issue](http://stackoverflow.com/questions/32927405/converting-yuv-image-to-rgb-results-in-greenish-picture "Converting YUV image to RGB results in greenish picture") with handling [YUV 420 888 images](http://developer.android.com/reference/android/graphics/ImageFormat.html#YUV_420_888 "Android Javadoc") in Android API 21 (5.0.x), the current release supports only a simple ball detection mode.
This issue can raise false positive counts if the brightness of the environment change to much.

I'll may fix this if I have a real device with Android API 22 (Android 5.1.x) of higher.

The change will be implemented in a way to also detect luminance differences in the image. This might reduce false positive detections but needs support for mostly white Lego&#174; balls (e.g soccerballs/footballs).

# More on GBC
Start for Lego&reg; GBC at [Youtube](https://www.youtube.com/results?search_query=lego+gbc "Search for Lego GBC on Youtube").

# Licence
This program is licensed under the terms of the [GNU GENERAL PUBLIC LICENSE V3](http://www.gnu.org/licenses/gpl-3.0.html).

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
