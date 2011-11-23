package org.fox.ttrss;

import java.util.HashMap;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MainActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	protected String m_sessionId;

	protected MenuItem m_syncStatus;

	public synchronized String getSessionId() {
		return m_sessionId;
	}
	
	/** Called when the activity is first created. */

	public void toast(String message) {
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.show();
	}
	
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
	
		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
		}
		
		setContentView(R.layout.main);

		/* HeadlinesFragment hf = new HeadlinesFragment();
		FeedsFragment ff = new FeedsFragment();
		ArticleFragment af = new ArticleFragment();
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		
		ft.add(R.id.main, ff);
		ft.add(R.id.main, hf);
		ft.add(R.id.main, af); 
		ft.hide(hf);
		ft.hide(af);
		ft.commit(); */

		FragmentTransaction ft = getFragmentManager().beginTransaction();
		//ft.hide(getFragmentManager().findFragmentById(R.id.headlines_fragment));
		ft.hide(getFragmentManager().findFragmentById(R.id.article_fragment));
		ft.commit();
		
		LoginRequest ar = new LoginRequest();
		ar.setApi(m_prefs.getString("ttrss_url", null));

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "login");
				put("user", m_prefs.getString("login", null));
				put("password", m_prefs.getString("password", null));
			}			 
		};

		ar.execute(map);
		
		setLoadingStatus(R.string.login_in_progress, true);
		
		/* ViewFlipper vf = (ViewFlipper) findViewById(R.id.main_flipper);
		
		if (vf != null) {
			vf.showNext();
		} */
		
	}

	public void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView)findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(status);
		}
		
		View pb = findViewById(R.id.loading_progress);
		
		if (pb != null) {
			pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
		}
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
	
	private class LoginRequest extends ApiRequest {
		
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonObject rv = result.getAsJsonObject();

					int status = rv.get("status").getAsInt();
					
					if (status == 0) {
						JsonObject content = rv.get("content").getAsJsonObject();
						if (content != null) {
							m_sessionId = content.get("session_id").getAsString();
							
							setLoadingStatus(R.string.loading_message, true);
							
							FragmentManager fm = getFragmentManager();
							FeedsFragment ff = (FeedsFragment) fm.findFragmentById(R.id.feeds_fragment);

							if (ff != null) {
								ff.initialize(m_sessionId);
							}
							
							ViewFlipper vf = (ViewFlipper) findViewById(R.id.main_flipper);
							
							if (vf != null) {
								vf.showNext();
							}
						}
					} else {
						JsonObject content = rv.get("content").getAsJsonObject();
						
						if (content != null) {
							String error = content.get("error").getAsString();

							m_sessionId = null;

							if (error.equals("LOGIN_ERROR")) {
								setLoadingStatus(R.string.login_wrong_password, false);
							} else if (error.equals("API_DISABLED")) {
								setLoadingStatus(R.string.login_api_disabled, false);
							} else {
								setLoadingStatus(R.string.login_failed, false);
							}
						}							
					}
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}
	    }
	}
}