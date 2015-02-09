package org.fox.ttrss;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.preferences);

        boolean compatMode = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB;

        if (compatMode) {
        	findPreference("webview_hardware_accel").setEnabled(false);
        }
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
        	findPreference("enable_condensed_fonts").setEnabled(false);
        }

        Preference versionPref = (Preference)findPreference("version");
        String version = null;
        int versionCode = -1;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = packageInfo.versionCode;

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        versionPref.setTitle(getString(R.string.version, version, versionCode));
    }

}
