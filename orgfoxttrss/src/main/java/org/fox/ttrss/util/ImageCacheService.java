package org.fox.ttrss.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.offline.OfflineDownloadService;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

public class ImageCacheService extends IntentService {

	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();

	public static final int NOTIFY_DOWNLOADING = 1;
	
	private static final String CACHE_PATH = "/image-cache/";

	private int m_imagesDownloaded = 0;
	
	private NotificationManager m_nmgr;
	
	public ImageCacheService() {
		super("ImageCacheService");
	}

	private boolean isDownloadServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if ("org.fox.ttrss.OfflineDownloadService".equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	

	@Override
	public void onCreate() {
		super.onCreate();
		m_nmgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
	}

	public static boolean isUrlCached(Context context, String url) {
		String hashedUrl = md5(url);
		
		File storage = context.getExternalCacheDir();
		
		File file = new File(storage.getAbsolutePath() + CACHE_PATH + "/" + hashedUrl + ".png");
		
		return file.exists();
	}

	public static String getCacheFileName(Context context, String url) {
		String hashedUrl = md5(url);
		
		File storage = context.getExternalCacheDir();
		
		File file = new File(storage.getAbsolutePath() + CACHE_PATH + "/" + hashedUrl + ".png");
		
		return file.getAbsolutePath();
	}
	
	public static void cleanupCache(Context context, boolean deleteAll) {
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File storage = context.getExternalCacheDir();
			File cachePath = new File(storage.getAbsolutePath() + CACHE_PATH);
		
			long now = new Date().getTime();
			
			if (cachePath.isDirectory()) {
				for (File file : cachePath.listFiles()) {
					if (deleteAll || now - file.lastModified() > 1000*60*60*24*7) {
						file.delete();
					}					
				}				
			}
		}
	}

	protected static String md5(String s) {
		try {
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
	        digest.update(s.getBytes());
	        byte messageDigest[] = digest.digest();
	        
	        StringBuffer hexString = new StringBuffer();
	        for (int i=0; i<messageDigest.length; i++)
	            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
	        
	        return hexString.toString();
	        
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private InputStream getStream(String urlString) {
	    try {
	        URL url = new URL(urlString);
	        URLConnection urlConnection = url.openConnection();
	        urlConnection.setConnectTimeout(250);
	        urlConnection.setReadTimeout(5*1000);
	        return urlConnection.getInputStream();
	    } catch (Exception ex) {
	        return null;
	    }
	}
	
	@SuppressWarnings("deprecation")
	private void updateNotification(String msg) {
		Notification notification = new Notification(R.drawable.icon, 
				getString(R.string.notify_downloading_title), System.currentTimeMillis());
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, OnlineActivity.class), 0);
		
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		
        notification.setLatestEventInfo(this, getString(R.string.notify_downloading_title), msg, contentIntent);
                       
        m_nmgr.notify(NOTIFY_DOWNLOADING, notification);
	}

	/* private void updateNotification(int msgResId) {
		updateNotification(getString(msgResId));
	} */
	
	@Override
	protected void onHandleIntent(Intent intent) {
		String url = intent.getStringExtra("url");

		//Log.d(TAG, "got request to download URL=" + url);
		
		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			return;
		
		String hashedUrl = md5(url);
		
		File storage = getExternalCacheDir();
		File cachePath = new File(storage.getAbsolutePath() + CACHE_PATH);
		if (!cachePath.exists()) cachePath.mkdirs();
		
		if (cachePath.isDirectory() && hashedUrl != null) {
			File outputFile = new File(cachePath.getAbsolutePath() + "/" + hashedUrl + ".png");
			
			if (!outputFile.exists()) {

				//Log.d(TAG, "downloading to " + outputFile.getAbsolutePath());

				InputStream is = getStream(url);
				
				if (is != null) {
					try {					
						FileOutputStream fos = new FileOutputStream(outputFile);
						
						byte[] buffer = new byte[1024];
						int len = 0;
						while ((len = is.read(buffer)) != -1) {
						    fos.write(buffer, 0, len);
						}
						
						fos.close();
						is.close();
						
						m_imagesDownloaded++;
						
						updateNotification(getString(R.string.notify_downloading_images, m_imagesDownloaded));
						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}			
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();

	    if (!isDownloadServiceRunning()) {
	    	m_nmgr.cancel(NOTIFY_DOWNLOADING);
	    	
			Intent success = new Intent();
			success.setAction(OfflineDownloadService.INTENT_ACTION_SUCCESS);
			success.addCategory(Intent.CATEGORY_DEFAULT);
			sendBroadcast(success);
	    }
	}
	
}
