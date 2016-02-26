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

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int prefsId = getIntent().getIntExtra(BallDetectionStrategy.KEY_PREFS_STRATEGY, -1);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment(prefsId)).commit();
    }

    public static final class SettingsFragment extends PreferenceFragment {

        private final int strategyPrefs;

        public SettingsFragment(final int strategyPrefs) {
            this.strategyPrefs = strategyPrefs;
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            if (this.strategyPrefs >= 0) {
                addPreferencesFromResource(this.strategyPrefs);
            }
        }
    }
}
