package org.fox.ttrss;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class PreferencesActivity extends PreferenceActivity {

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		
		addPreferencesFromResource(R.xml.preferences);

		findPreference("justify_article_text").setEnabled(!prefs.getBoolean("combined_mode", false));
		
		findPreference("combined_mode").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {				
				findPreference("justify_article_text").setEnabled(!newValue.toString().equals("true"));				
				return true;
			}
		});
	}
}
