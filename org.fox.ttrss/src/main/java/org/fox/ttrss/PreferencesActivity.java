package org.fox.ttrss;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PreferencesActivity extends CommonActivity {
	@Override
    public void onCreate(Bundle savedInstanceState) {
        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        android.app.FragmentTransaction ft = getFragmentManager().beginTransaction();

        ft.replace(R.id.preferences_container, new PreferencesFragment());
        ft.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
