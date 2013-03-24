package org.fox.ttrss.share;

import java.util.HashMap;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;


public abstract class CommonShareActivity extends CommonActivity {
	protected SharedPreferences m_prefs;
	protected String m_sessionId;
	protected int m_apiLevel = 0;

	private final String TAG = this.getClass().getSimpleName();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		super.onCreate(savedInstanceState);
		
		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_apiLevel = savedInstanceState.getInt("apiLevel");
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
		out.putInt("apiLevel", m_apiLevel);
	}

	protected abstract void onLoggedIn(int requestId);

	protected abstract void onLoggingIn(int requestId);

	@SuppressWarnings({ "unchecked", "serial" })
	public void login(int requestId) {

		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.dialog_need_configure_prompt)
			       .setCancelable(false)
			       .setPositiveButton(R.string.dialog_open_preferences, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			   			// launch preferences
			   			
			        	   Intent intent = new Intent(CommonShareActivity.this,
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

			LoginRequest ar = new LoginRequest(getApplicationContext(), requestId); 

			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", "").trim());
					put("password", m_prefs.getString("password", "").trim());
				}
			};

			onLoggingIn(requestId);
			
			ar.execute(map);
		}
	}	
	
	protected class LoginRequest extends ApiRequest {
		private int m_requestId;
		
		public LoginRequest(Context context, int requestId) {
			super(context);
			m_requestId = requestId;
		}

		@SuppressWarnings("unchecked")
		protected void onPostExecute(JsonElement result) {
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

								onLoggedIn(m_requestId);
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

						return;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			m_sessionId = null;

			toast(getErrorMessage());
			setProgressBarIndeterminateVisibility(false);
		}

	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(CommonShareActivity.this,
					PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		default:
			Log.d(TAG,
					"onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.share_menu, menu);
		return true;
	}



}
