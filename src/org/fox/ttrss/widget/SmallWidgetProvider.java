package org.fox.ttrss.widget;

import org.fox.ttrss.R;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class SmallWidgetProvider extends AppWidgetProvider {
	private final String TAG = this.getClass().getSimpleName();

	public static final String FORCE_UPDATE_ACTION = "org.fox.ttrss.WIDGET_FORCE_UPDATE";
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		//RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_small);
		
		final int N = appWidgetIds.length;
		
		for (int i=0; i < N; i++) {
			int appWidgetId = appWidgetIds[i];

			Intent updateIntent = new Intent(context, org.fox.ttrss.widget.WidgetUpdateService.class);
            PendingIntent updatePendingIntent = PendingIntent.getService(context, 0, updateIntent, 0);
			
            Intent intent = new Intent(context, org.fox.ttrss.OnlineActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);
            views.setOnClickPendingIntent(R.id.widget_main, pendingIntent);
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
            
            try {
				updatePendingIntent.send();
			} catch (CanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	@Override
    public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
				
		if (FORCE_UPDATE_ACTION.equals(intent.getAction())) {
			
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		    ComponentName thisAppWidget = new ComponentName(context.getPackageName(), SmallWidgetProvider.class.getName());
		    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);

		    onUpdate(context, appWidgetManager, appWidgetIds);
		}
	} 
	
}
