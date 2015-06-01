package org.fox.ttrss;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PreferencesActivity extends PreferenceActivity {
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        /* if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
        	findPreference("enable_condensed_fonts").setEnabled(false);
        } */

        String version = "?";
        int versionCode = -1;
        String buildTimestamp = "N/A";

        try {
            PackageInfo packageInfo = getPackageManager().
                    getPackageInfo(getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = packageInfo.versionCode;

            ApplicationInfo appInfo = getPackageManager().
                    getApplicationInfo(getPackageName(), 0);

            ZipFile zf = new ZipFile(appInfo.sourceDir);
            ZipEntry ze = zf.getEntry("classes.dex");
            long time = ze.getTime();

            buildTimestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss",
                    Locale.getDefault()).format(time);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        findPreference("version").setSummary(getString(R.string.prefs_version, version, versionCode));
        findPreference("build_timestamp").setSummary(getString(R.string.prefs_build_timestamp, buildTimestamp));
    }

}
