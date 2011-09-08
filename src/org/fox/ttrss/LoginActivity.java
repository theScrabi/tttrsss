package org.fox.ttrss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private String m_sessionId = null;

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

		setContentView(R.layout.login);
	}

	protected void updateLoginStatus(int id) {
		TextView tv = (TextView) findViewById(R.id.login_status_text);
		if (tv != null) {
			tv.setText(id);
		}
	}

	protected void showLoginProgress(boolean show) {
		View v = findViewById(R.id.login_progress);
		v.setVisibility((show) ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!m_prefs.getString("theme", "THEME_DARK").equals(m_themeName)) {
			Intent refresh = new Intent(this, LoginActivity.class);
			startActivity(refresh);
			finish();
		}			

		showLoginProgress(false);

		if (isConfigured()) {
			updateLoginStatus(R.string.login_ready);
		} else {
			updateLoginStatus(R.string.login_need_configure);
		}

	}

	public boolean isConfigured() {
		String login = m_prefs.getString("login", "");
		String password = m_prefs.getString("password", "");
		String ttrssUrl = m_prefs.getString("ttrss_url", "");

		return !(login.equals("") || password.equals("") || ttrssUrl.equals(""));    	
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.login_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.login:
			performLogin();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@SuppressWarnings({ "serial", "unchecked" })
	private void performLogin() {
		ApiRequest task = new ApiRequest(null, m_prefs.getString("ttrss_url", null)) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null) {
					try {
					
						JsonObject rv = result.getAsJsonObject();

						int status = rv.get("status").getAsInt();
						
						if (status == 0) {
							JsonObject content = rv.get("content").getAsJsonObject();
							if (content != null) {
								m_sessionId = content.get("session_id").getAsString();
								
								showLoginProgress(false);
								updateLoginStatus(R.string.login_success);
							
								Intent intent = new Intent(getApplicationContext(), MainActivity.class);
								intent.putExtra("sessionId", m_sessionId);
								startActivityForResult(intent, 0);
								
								finish();
								return;
							}
						} else {
							JsonObject content = rv.get("content").getAsJsonObject();
							
							if (content != null) {
								String error = content.get("error").getAsString();

								if (error.equals("LOGIN_ERROR")) {
									updateLoginStatus(R.string.login_wrong_password);
								} else if (error.equals("API_DISABLED")) {
									updateLoginStatus(R.string.login_api_disabled);
								}								
							}							
						}
					} catch (Exception e) {
						e.printStackTrace();						
					}
				}				

				showLoginProgress(false);
				updateLoginStatus(R.string.login_failed);

			}
		};
		
		updateLoginStatus(R.string.login_in_progress);
		showLoginProgress(true);
		
		task.execute(new HashMap<String,String>() {   
			{
				put("op", "login");
				put("user", m_prefs.getString("login", null));
				put("password", m_prefs.getString("password", null));
			}			 
		});
		
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}        
}