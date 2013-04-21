package org.fox.ttrss;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.preferences);

        boolean compatMode = android.os.Build.VERSION.SDK_INT <= 10;

        if (compatMode) {
        	findPreference("dim_status_bar").setEnabled(false);        	
        }
    }

}
