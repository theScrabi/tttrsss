package org.fox.ttrss;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PreferencesFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

    }

    public void onResume() {
        super.onResume();

        String version = "?";
        int versionCode = -1;
        String buildTimestamp = "N/A";

        try {
            Activity activity = getActivity();

            PackageInfo packageInfo = activity.getPackageManager().
                    getPackageInfo(activity.getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = packageInfo.versionCode;

            ApplicationInfo appInfo = activity.getPackageManager().
                    getApplicationInfo(activity.getPackageName(), 0);

            ZipFile zf = new ZipFile(appInfo.sourceDir);
            ZipEntry ze = zf.getEntry("classes.dex");
            long time = ze.getTime();

            buildTimestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss",
                    Locale.getDefault()).format(time);


            findPreference("version").setSummary(getString(R.string.prefs_version, version, versionCode));
            findPreference("build_timestamp").setSummary(getString(R.string.prefs_build_timestamp, buildTimestamp));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}