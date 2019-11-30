package amrabed.android.release.evaluation.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Set;

import amrabed.android.release.evaluation.R;

/**
 * Preferences Fragment
 */
public class PreferenceSection extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String x) {
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
        addPreferencesFromResource(R.xml.preferences);

        setSummary(findPreference("fasting"));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key.equals("gender")) return; // Already handled

        final MultiSelectListPreference preference = findPreference(key);
        setSummary(preference);

        if (preference != null) {
            final Set<String> values = preference.getValues();
            if ("fasting".equals(key)) {
                final int value = getByteValue(values);
                preferences.edit().putInt("fastingDays", value).apply();
                if ((value & 0x08) == 0) {
                    preferences.edit().remove("ldof").apply();
                }
            }
        }
    }

    private void setSummary(MultiSelectListPreference preference) {
        if(preference != null) {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            final Set<String> selectedValues = preference.getValues();

            final StringBuilder summary = new StringBuilder();
            for (int i = 0; i < entries.length; i++) {
                if (selectedValues.contains(values[i].toString())) {
                    if (!TextUtils.isEmpty(summary)) {
                        summary.append(getString(R.string.comma));
                        summary.append(" ");
                    }
                    summary.append(entries[i]);
                }
            }
            preference.setSummary(summary);
        }
    }

    private int getByteValue(Set<String> selectedValues) {
        int value = 0;
        for (String v : selectedValues) {
            value |= 0x01 << (Integer.parseInt(v));
        }
        return value;
    }
}
