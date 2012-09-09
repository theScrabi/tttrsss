package org.fox.ttrss;

import java.util.HashMap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ShareActivity extends CommonActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	private String m_sessionId;
	private SharedPreferences m_prefs;
	private int m_apiLevel = 0;
	private boolean m_isLoggingIn = false;
	private String m_themeName = "";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		m_themeName = m_prefs.getString("theme", "THEME_DARK");

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_apiLevel = savedInstanceState.getInt("apiLevel");
		}

		setContentView(R.layout.share);
		
		setSmallScreen(findViewById(R.id.headlines_fragment) == null); 

		if (m_sessionId != null) {
			loginSuccess();
		} else {
			//login(); -- handled in onResume()
		}
	}
	
	private void loginSuccess() {
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		setProgressBarIndeterminateVisibility(false);
		
		if (m_apiLevel < 4) {
			setLoadingStatus(R.string.api_too_low, false);
		} else {
			Intent intent = getIntent();
			
			final  EditText url = (EditText) findViewById(R.id.url);
			url.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
			
			final  EditText title = (EditText) findViewById(R.id.title);
			title.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
			
			final EditText content = (EditText) findViewById(R.id.content);			
			
			Button share = (Button) findViewById(R.id.share_button);
			
			share.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ApiRequest req = new ApiRequest(getApplicationContext()) {
						protected void onPostExecute(JsonElement result) {
							setProgressBarIndeterminateVisibility(false);

							if (m_lastError != ApiError.NO_ERROR) {
								toast(getErrorMessage());
							} else {
								toast("Article posted.");
								finish();
							}
						}
					};

					HashMap<String, String> map = new HashMap<String, String>() {
						{
							put("sid", m_sessionId);
							put("op", "shareToPublished");
							put("title", title.getText().toString());
							put("url", url.getText().toString());
							put("content", content.getText().toString());
						}
					};

					setProgressBarIndeterminateVisibility(true);
					
					req.execute(map);
				}
			});
		}
	}
	
	private void logout() {
		m_sessionId = null;

		findViewById(R.id.loading_container).setVisibility(View.VISIBLE);

		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(R.string.login_ready);
		}
	}
	
	@SuppressWarnings({ "unchecked", "serial" })
	public void login() {

		logout();

		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

			setLoadingStatus(R.string.login_need_configure, false);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.dialog_need_configure_prompt)
			       .setCancelable(false)
			       .setPositiveButton(R.string.dialog_open_preferences, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			   			// launch preferences
			   			
			        	   Intent intent = new Intent(ShareActivity.this,
			        			   PreferencesActivity.class);
			        	   startActivityForResult(intent, 0);
			           }
			       })
			       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
			
		} else {

			LoginRequest ar = new LoginRequest(getApplicationContext());

			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", "").trim());
					put("password", m_prefs.getString("password", "").trim());
				}
			};

			ar.execute(map);

			setLoadingStatus(R.string.login_in_progress, true);
			
			m_isLoggingIn = true;
		}
	}	
	
	
	
	private void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(status);
		}
		
		setProgressBarIndeterminateVisibility(showProgress);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (m_sessionId == null && !m_isLoggingIn) {
			login();
		}
	}

	private class LoginRequest extends ApiRequest {
		public LoginRequest(Context context) {
			super(context);
		}

		@SuppressWarnings("unchecked")
		protected void onPostExecute(JsonElement result) {
			m_isLoggingIn = false;
			
			if (result != null) {
				try {
					JsonObject content = result.getAsJsonObject();
					if (content != null) {
						m_sessionId = content.get("session_id").getAsString();

						Log.d(TAG, "Authenticated!");

						ApiRequest req = new ApiRequest(m_context) {
							protected void onPostExecute(JsonElement result) {
								m_apiLevel = 0;

								if (result != null) {
									try {
										m_apiLevel = result.getAsJsonObject()
													.get("level").getAsInt();
									} catch (Exception e) {
										e.printStackTrace();
									}
								}

								Log.d(TAG, "Received API level: " + m_apiLevel);

								loginSuccess();
							}
						};

						@SuppressWarnings("serial")
						HashMap<String, String> map = new HashMap<String, String>() {
							{
								put("sid", m_sessionId);
								put("op", "getApiLevel");
							}
						};

						req.execute(map);

						setLoadingStatus(R.string.loading_message, true);

						return;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			m_sessionId = null;

			setLoadingStatus(getErrorMessage(), false);
		}

	}
}
