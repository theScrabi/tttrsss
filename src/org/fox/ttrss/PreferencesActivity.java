package org.fox.ttrss;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		
		addPreferencesFromResource(R.xml.preferences);

		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) != Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			PreferenceCategory category = (PreferenceCategory)findPreference("category_look_and_feel");
			category.removePreference(findPreference("tablet_article_swipe"));
		}
		
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
