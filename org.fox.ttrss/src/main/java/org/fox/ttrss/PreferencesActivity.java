package org.fox.ttrss;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.preferences);

        boolean compatMode = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB;

        if (compatMode) {
        	findPreference("dim_status_bar").setEnabled(false);
        	findPreference("webview_hardware_accel").setEnabled(false);        	
        }
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
        	findPreference("enable_condensed_fonts").setEnabled(false);
        }
    }

}
