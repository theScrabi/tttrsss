package org.fox.ttrss.widget;

import java.util.HashMap;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.R;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

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
        	view.setTextViewText(R.id.counter, String.valueOf(""));
        	view.setViewVisibility(R.id.progress, View.VISIBLE);

	        manager.updateAppWidget(thisWidget, view);
	        
	        final SharedPreferences m_prefs = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());
	   	
	   		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {
	    			
	    			// Toast: need configure
	    			
	   		} else {
	
	   			ApiRequest ar = new ApiRequest(getApplicationContext()) {
	   				@SuppressWarnings({ "unchecked", "serial" })
					@Override
	   				protected void onPostExecute(JsonElement result) {
	   					if (result != null) {
	   						JsonObject content = result.getAsJsonObject();
	   						
	   						if (content != null) {
	   							final String sessionId = content.get("session_id").getAsString();
	   						
	   							ApiRequest aru = new ApiRequest(getApplicationContext()) {
	   								@Override
	   								protected void onPostExecute(JsonElement result) {
	   									if (result != null) {
	   										try {
		   										JsonObject content = result.getAsJsonObject();
		   										
		   										if (content != null) {
		   											int unread = content.get("unread").getAsInt();
		   											
		   											view.setViewVisibility(R.id.progress, View.GONE);
		   											view.setTextViewText(R.id.counter, String.valueOf(unread));
		   											manager.updateAppWidget(thisWidget, view);
		   											
		   											return;
		   										}
	   										} catch (Exception e) {
	   											e.printStackTrace();
	   										}
	   									}	   										
	   								
	   									view.setViewVisibility(R.id.progress, View.GONE);
	   									view.setTextViewText(R.id.counter, "?");
	   									manager.updateAppWidget(thisWidget, view);
	   								}
	   							};
	
	   				   			HashMap<String, String> umap = new HashMap<String, String>() {
	   				   				{
	   				   					put("op", "getUnread");
	   				   					put("sid", sessionId);
	   				   				}
	   				   			};
	
	   							aru.execute(umap);
	   							return;
	   						}
	   					}
	   					
						// Toast: login failed
						
	   			    	view.setViewVisibility(R.id.progress, View.GONE);
	   			    	view.setTextViewText(R.id.counter, "?");
	   			        manager.updateAppWidget(thisWidget, view);  					
	   				};
	   			};
	
	   			HashMap<String, String> map = new HashMap<String, String>() {
	   				{
	   					put("op", "login");
	   					put("user", m_prefs.getString("login", "").trim());
	   					put("password", m_prefs.getString("password", "").trim());
	   				}
	   			};
	    			
	   			ar.execute(map);
	   		}
    	} catch (Exception e) {
    		e.printStackTrace();
    		
	    	view.setViewVisibility(R.id.progress, View.GONE);
	    	view.setTextViewText(R.id.counter, getString(R.string.app_name));
	        manager.updateAppWidget(thisWidget, view);  					
	
    	}
    }
}
