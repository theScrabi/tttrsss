package org.fox.ttrss.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.R;
import org.fox.ttrss.util.SimpleLoginManager;

import java.util.HashMap;

public class WidgetUpdateService extends Service {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind");
		
		// TODO Auto-generated method stub
		return null;
	}
	
	/* @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand");
	
		return super.onStartCommand(intent, flags, startId);
	} */
	
	public void update() {
		
		
	}
	
    @Override
    public void onStart(Intent intent, int startId) {
    	final RemoteViews view = new RemoteViews(getPackageName(), R.layout.widget_small);
    	
    	final ComponentName thisWidget = new ComponentName(this, SmallWidgetProvider.class);
    	final AppWidgetManager manager = AppWidgetManager.getInstance(this);

    	try {
        	view.setTextViewText(R.id.counter, String.valueOf("..."));

	        manager.updateAppWidget(thisWidget, view);
	        
	        final SharedPreferences m_prefs = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());
	   	
	   		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {
	    			
	    			// Toast: need configure
	    			
	   		} else {
	
	   			SimpleLoginManager loginManager = new SimpleLoginManager() {
					
					@Override
					protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {
					
						ApiRequest aru = new ApiRequest(getApplicationContext()) {
								@Override
								protected void onPostExecute(JsonElement result) {
									if (result != null) {
										try {
   										JsonObject content = result.getAsJsonObject();
   										
   										if (content != null) {
   											int unread = content.get("unread").getAsInt();
   											
   											view.setTextViewText(R.id.counter, String.valueOf(unread));
   											manager.updateAppWidget(thisWidget, view);
   											
   											return;
   										}
										} catch (Exception e) {
											e.printStackTrace();
										}
									}	   										
								
									view.setTextViewText(R.id.counter, "?");
									manager.updateAppWidget(thisWidget, view);
								}
						};
						
						final String fSessionId = sessionId;
						
						HashMap<String, String> umap = new HashMap<String, String>() {
				   				{
				   					put("op", "getUnread");
				   					put("sid", fSessionId);
				   				}
				   			};

							aru.execute(umap);
					}
					
					@Override
					protected void onLoginFailed(int requestId, ApiRequest ar) {
						
	   			    	view.setTextViewText(R.id.counter, "?");
	   			        manager.updateAppWidget(thisWidget, view);
					}
					
					@Override
					protected void onLoggingIn(int requestId) {
						
						
					}
				};

				String login = m_prefs.getString("login", "").trim();
				String password = m_prefs.getString("password", "").trim();
				
				loginManager.logIn(getApplicationContext(), 1, login, password);
	   		}
    	} catch (Exception e) {
    		e.printStackTrace();
    		
	    	view.setTextViewText(R.id.counter, getString(R.string.app_name));
	        manager.updateAppWidget(thisWidget, view);  					
	
    	}
    }
}
