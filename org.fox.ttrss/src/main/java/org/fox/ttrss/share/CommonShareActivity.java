package org.fox.ttrss.share;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.SimpleLoginManager;

import icepick.State;


public abstract class CommonShareActivity extends CommonActivity {
	protected SharedPreferences m_prefs;
	@State protected String m_sessionId;
	@State protected int m_apiLevel = 0;

	private final String TAG = this.getClass().getSimpleName();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		super.onCreate(savedInstanceState);
	}

	protected abstract void onLoggedIn(int requestId);

	protected abstract void onLoggingIn(int requestId);

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
			
			SimpleLoginManager loginManager = new SimpleLoginManager() {
				
				@Override
				protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {
					m_sessionId = sessionId;
					m_apiLevel = apiLevel;
					
					CommonShareActivity.this.onLoggedIn(requestId);					
				}
				
				@Override
				protected void onLoginFailed(int requestId, ApiRequest ar) {
					toast(ar.getErrorMessage());
					setProgressBarIndeterminateVisibility(false);
				}
				
				@Override
				protected void onLoggingIn(int requestId) {
					CommonShareActivity.this.onLoggingIn(requestId);					
				}
			};
			
			String login = m_prefs.getString("login", "").trim();
			String password = m_prefs.getString("password", "").trim();
			
			loginManager.logIn(this, requestId, login, password);
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
		inflater.inflate(R.menu.activity_share, menu);
		return true;
	}



}
