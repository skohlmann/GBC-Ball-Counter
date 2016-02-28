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
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;


public class SeekBarPreference extends DialogPreference {

    private static final String BASE_ANDROID_NS = "http://schemas.android.com/apk/res/";
    private static final String PREFERENCE_NS = BASE_ANDROID_NS + SeekBarPreference.class.getPackage().getName();
    private static final String ANDROID_NS = BASE_ANDROID_NS + "android";

    private static final String ATTR_DEFAULT_VALUE = "defaultValue";
    private static final String ATTR_MIN_VALUE = "minValue";
    private static final String ATTR_MAX_VALUE = "maxValue";

    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 100;

    private int mMinValue;
    private int mMaxValue;
    private int mDefaultValue;
    private int mCurrentValue;

    private TextView mTextView;

    public SeekBarPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        handleAttributes(attrs);
    }

    public SeekBarPreference(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        handleAttributes(attrs);
    }

    public SeekBarPreference(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        handleAttributes(attrs);
    }

    @Override
    protected View onCreateDialogView() {
        this.mCurrentValue = getPersistedInt(this.mDefaultValue);

        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.dialog_slider, null);

        ((TextView) view.findViewById(R.id.min_value)).setText(String.format("%d", this.mMinValue));
        ((TextView) view.findViewById(R.id.max_value)).setText(String.format("%d", this.mMaxValue));

        final SeekBar sb = (SeekBar) view.findViewById(R.id.seek_bar);
        sb.setMax(this.mMaxValue - this.mMinValue);
        sb.setProgress(this.mCurrentValue - this.mMinValue);
        sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seek, final int value, final boolean fromTouch) {
                SeekBarPreference.this.mCurrentValue = value + SeekBarPreference.this.mMinValue;
                SeekBarPreference.this.mTextView.setText(String.format("%d", SeekBarPreference.this.mCurrentValue));
            }

            @Override public void onStartTrackingTouch(final SeekBar seek) { /* Not required */ }
            @Override public void onStopTrackingTouch(final SeekBar seek) { /* Not required */ }
        });

        this.mTextView = (TextView) view.findViewById(R.id.current_value);
        this.mTextView.setText(String.format("%d", this.mCurrentValue));

        return view;
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            return;
        }

        if (shouldPersist()) {
            persistInt(this.mCurrentValue);
        }

        notifyChanged();
    }

    final void handleAttributes(final AttributeSet attrs) {
        this.mMinValue = attrs.getAttributeIntValue(PREFERENCE_NS, ATTR_MIN_VALUE, DEFAULT_MIN_VALUE);
        this.mMaxValue = Math.max(attrs.getAttributeIntValue(PREFERENCE_NS, ATTR_MAX_VALUE, DEFAULT_MAX_VALUE), this.mMinValue + 1);
        this.mDefaultValue = Math.max(Math.min(attrs.getAttributeIntValue(ANDROID_NS, ATTR_DEFAULT_VALUE, (this.mMaxValue - this.mMinValue) / 2 + this.mMinValue), this.mMaxValue), this.mMinValue);
    }

    @Override
    public CharSequence getSummary() {
        final String summary = super.getSummary().toString();
        final int value = getPersistedInt(this.mDefaultValue);
        return String.format(summary, value);
    }
}
