//
//  Copyright (c) 2014 Lior Hass
//
package com.liorhass;

import com.liorhass.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        sp.registerOnSharedPreferenceChangeListener(this);

        // Update the summary lines of the settings screen
        setSummaryLines(sp);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        setSummaryLines(sharedPreferences);
    }

    /** Sets the summary lines of preferences that have such a line */
    private void setSummaryLines(SharedPreferences sharedPreferences) {
        String baudrate = sharedPreferences.getString("pref_baudrate", "");
        ((ListPreference)findPreference("pref_baudrate")).setSummary(baudrate);
    }
}
