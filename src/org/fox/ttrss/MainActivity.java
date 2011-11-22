package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.jsoup.Jsoup;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ViewFlipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class MainActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	protected String m_sessionId;

	protected MenuItem m_syncStatus;

	protected String getSessionId() {
		return m_sessionId;
	}

	protected synchronized void setSessionId(String sessionId) {
		m_sessionId = sessionId;

		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("last_session_id", m_sessionId);	
		editor.commit();
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());       

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		m_themeName = m_prefs.getString("theme", "THEME_DARK");
		m_sessionId = m_prefs.getString("last_session_id", null);

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
		}

		setContentView(R.layout.main);
			
		ApiRequest ar = new ApiRequest();
		ar.setApi(m_prefs.getString("ttrss_url", null));

		HashMap<String,String> loginMap = new HashMap<String,String>() {
			{
				put("op", "login");
				put("user", m_prefs.getString("login", null));
				put("password", m_prefs.getString("password", null));
			}			 
		};

		ar.execute(loginMap);
		
		/* ViewFlipper vf = (ViewFlipper) findViewById(R.id.main_flipper);
		
		if (vf != null) {
			vf.showNext();
		}
		
		HeadlinesFragment hf = new HeadlinesFragment();
		FeedsFragment ff = new FeedsFragment();
		ArticleFragment af = new ArticleFragment();
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.add(R.id.main, ff);
		ft.add(R.id.main, hf);
		ft.add(R.id.main, af);
		ft.hide(hf);
		ft.hide(af);
		ft.commit(); */
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putString("sessionId", m_sessionId);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!m_prefs.getString("theme", "THEME_DARK").equals(m_themeName)) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			finish();
		}			
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
}