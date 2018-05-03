package org.fox.ttrss.tasker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.fox.ttrss.ApiCommon;
import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.offline.OfflineDownloadService;
import org.fox.ttrss.offline.OfflineUploadService;
import org.fox.ttrss.util.SimpleLoginManager;

public class TaskerReceiver extends BroadcastReceiver {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Got action: " + intent.getAction());
		
		final Context fContext = context;
		
		if (com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction())) {
			
			final Bundle settings = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
			final int actionId = settings != null ? settings.getInt("actionId", -1) : -1;
			
			Log.d(TAG, "received action id=" + actionId);
			
			SimpleLoginManager loginMgr = new SimpleLoginManager() {
				
				@Override
				protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {

					switch (actionId) {
					case TaskerSettingsActivity.ACTION_DOWNLOAD:
						if (true) {
							Intent intent = new Intent(fContext,
									OfflineDownloadService.class);
							intent.putExtra("sessionId", sessionId);
							intent.putExtra("batchMode", true);

							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
								fContext.startForegroundService(intent);
							} else {
								fContext.startService(intent);
							}
						}
						break;
					case TaskerSettingsActivity.ACTION_UPLOAD:
						if (true) {
							Intent intent = new Intent(fContext,
									OfflineUploadService.class);
							intent.putExtra("sessionId", sessionId);
							intent.putExtra("batchMode", true);

							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
								fContext.startForegroundService(intent);
							} else {
								fContext.startService(intent);
							}

						}						
						break;
					default:
						Log.d(TAG, "unknown action id=" + actionId);
					}					
				}
				
				@Override
				protected void onLoginFailed(int requestId, ApiRequest ar) {
					Toast toast = Toast.makeText(fContext, fContext.getString(ar.getErrorMessage()), Toast.LENGTH_SHORT);
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
			String ttrssUrl = prefs.getString("ttrss_url", "").trim();
			ApiCommon.trustAllHosts(prefs.getBoolean("ssl_trust_any", false), prefs.getBoolean("ssl_trust_any_host", false));
			
			if (ttrssUrl.equals("")) {
				Toast toast = Toast.makeText(fContext, "Could not download articles: not configured?", Toast.LENGTH_SHORT);
				toast.show();
			} else {				
				loginMgr.logIn(context, 1, login, password);
			}			
		}
	}

}
