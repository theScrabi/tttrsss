package org.fox.ttrss;

import java.util.HashMap;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DashClock extends DashClockExtension {
  
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	
	@Override
	protected void onInitialize(boolean isReconnect) {
		super.onInitialize(isReconnect);
		setUpdateWhenScreenOn(true);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	@Override
	protected void onUpdateData(int reason) {
		
		UnreadRequest req = new UnreadRequest(getApplicationContext());
				
		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("op", "login");
				put("user", m_prefs.getString("login", "").trim());
				put("password", m_prefs.getString("password", "").trim());
			}
		};

		req.execute(map);
	}
	
	protected class UnreadRequest extends ApiRequest {
		private String m_sessionId;
		
		private int m_unreadCount;
		
		public UnreadRequest(Context context) {
			super(context);
		}

		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {
					JsonObject content = result.getAsJsonObject();
					if (content != null) {
						m_sessionId = content.get("session_id").getAsString();

						// Log.d(TAG, "Authenticated!");
						
						ApiRequest req = new ApiRequest(m_context) {
							protected void onPostExecute(JsonElement result) {
								m_unreadCount = 0;

								if (result != null) {
									try {
										JsonElement unreadCount = result.getAsJsonObject().get("unread");
										
										if (unreadCount != null) {
											m_unreadCount = unreadCount.getAsInt();
										} else {
											m_unreadCount = -1;
										}
										
										ExtensionData updatedData = null; // when null DashClock hides the widget
										if (m_unreadCount > 0) {
											updatedData = new ExtensionData();
											updatedData.visible(true);
						
											updatedData.icon(R.drawable.dashclock);
											updatedData.status(String.valueOf(m_unreadCount));
						
											updatedData.expandedTitle(getString(R.string.n_unread_articles, m_unreadCount));
											//updatedData.expandedBody(getString(R.string.app_name));
						
											updatedData.clickIntent(new Intent().setClassName("org.fox.ttrss",
													"org.fox.ttrss.OnlineActivity"));
										}
										publishUpdate(updatedData);				
										
									} catch (Exception e) {
										e.printStackTrace();
									}
								}

								//Log.d(TAG, "unread count is: " + m_unreadCount);
							}
						};

						@SuppressWarnings("serial")
						HashMap<String, String> map = new HashMap<String, String>() {
							{
								put("sid", m_sessionId);
								put("op", "getUnread");
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
		}
	}
}
