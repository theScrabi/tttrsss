package org.fox.ttrss.tasker;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.offline.OfflineDownloadService;
import org.fox.ttrss.util.SimpleLoginManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class TaskerReceiver extends BroadcastReceiver {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Got action: " + intent.getAction());
		
		final Context fContext = context;
		
		if (com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction())) {
			Log.d(TAG, "about to download stuff!");
			
			SimpleLoginManager loginMgr = new SimpleLoginManager() {
				
				@Override
				protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {
					Log.d(TAG, "Got SID=" + sessionId);
					
					Intent intent = new Intent(
							fContext,
							OfflineDownloadService.class);
					intent.putExtra("sessionId", sessionId);
					intent.putExtra("batchMode", true);

					fContext.startService(intent);
				}
				
				@Override
				protected void onLoginFailed(int requestId) {
					Toast toast = Toast.makeText(fContext, "Could not download articles: login failed", Toast.LENGTH_SHORT);
					toast.show();
				}
				
				@Override
				protected void onLoggingIn(int requestId) {
					//
				}
			};
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			
			String login = prefs.getString("login", "").trim();
			String password = prefs.getString("password", "").trim();
			
			if (login.equals("") || password.equals("")) {
				Toast toast = Toast.makeText(fContext, "Could not download articles: not configured?", Toast.LENGTH_SHORT);
				toast.show();
			} else {				
				loginMgr.logIn(context, 1, login, password);
			}			
		}
	}

}
