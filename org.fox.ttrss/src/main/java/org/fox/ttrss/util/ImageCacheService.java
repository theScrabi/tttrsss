package org.fox.ttrss.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.offline.OfflineDownloadService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class ImageCacheService extends IntentService {

	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();

	public static final int NOTIFY_DOWNLOADING = 1;
	public static final int NOTIFY_DOWNLOAD_SUCCESS = 2;

	public static final String INTENT_ACTION_ICS_STOP = "org.fox.ttrss.intent.action.ICSStop";

	private static final String CACHE_PATH = "/image-cache/";

	private int m_imagesDownloaded = 0;
	private boolean m_canProceed = true;

	private NotificationManager m_nmgr;
	private BroadcastReceiver m_receiver;
	private int m_queueSize = 0;

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

		m_receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, "received broadcast action: " + intent.getAction());

				if (INTENT_ACTION_ICS_STOP.equals(intent.getAction())) {
					m_canProceed = false;
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(INTENT_ACTION_ICS_STOP);
		filter.addCategory(Intent.CATEGORY_DEFAULT);

		registerReceiver(m_receiver, filter);
	}

	public static boolean isUrlCached(Context context, String url) {
		String hashedUrl = md5(url);
		
		File storage = context.getExternalCacheDir();
		
		File file = new File(storage.getAbsolutePath() + CACHE_PATH + "/" + hashedUrl);
		
		return file.exists();
	}

	public static String getCacheFileName(Context context, String url) {
		String hashedUrl = md5(url);
		
		File storage = context.getExternalCacheDir();
		
		File file = new File(storage.getAbsolutePath() + CACHE_PATH + "/" + hashedUrl);
		
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
	private void notifyDownloadSuccess() {
		Intent intent = new Intent(this, OnlineActivity.class);
		intent.setAction(OfflineDownloadService.INTENT_ACTION_SWITCH_OFFLINE);

		PendingIntent contentIntent = PendingIntent.getActivity(this, OfflineDownloadService.PI_SUCCESS,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
				.setContentTitle(getString(R.string.dialog_offline_success))
				.setContentText(getString(R.string.offline_tap_to_switch))
				.setContentIntent(contentIntent)
				.setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_notification)
				.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
						R.drawable.ic_launcher))
				.setOnlyAlertOnce(true)
				.setPriority(Notification.PRIORITY_HIGH)
				.setDefaults(Notification.DEFAULT_ALL)
				.setAutoCancel(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setCategory(Notification.CATEGORY_MESSAGE)
					.setVibrate(new long[0])
					.setVisibility(Notification.VISIBILITY_PUBLIC)
					.setColor(0x88b0f0)
					.setGroup("org.fox.ttrss");
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setChannelId(CommonActivity.NOTIFICATION_CHANNEL_PRIORITY);
		}

		m_nmgr.notify(NOTIFY_DOWNLOAD_SUCCESS, builder.build());
	}


	@SuppressWarnings("deprecation")
	private void updateNotification(String msg, int progress, int max, boolean showProgress) {
		Intent intent = new Intent(this, OnlineActivity.class);

		PendingIntent contentIntent = PendingIntent.getActivity(this, OfflineDownloadService.PI_GENERIC,
                intent, 0);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
				.setContentText(msg)
				.setContentTitle(getString(R.string.notify_downloading_title))
				.setContentIntent(contentIntent)
				.setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_cloud_download)
				.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
						R.drawable.ic_launcher))
				.setOngoing(true)
				.setOnlyAlertOnce(true);

		if (showProgress) builder.setProgress(max, progress, max == 0);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			intent = new Intent(this, OnlineActivity.class);
			intent.setAction(OfflineDownloadService.INTENT_ACTION_CANCEL);

			PendingIntent cancelIntent = PendingIntent.getActivity(this, OfflineDownloadService.PI_CANCEL, intent, 0);

			builder.setCategory(Notification.CATEGORY_PROGRESS)
					.setVibrate(new long[0])
					.setVisibility(Notification.VISIBILITY_PUBLIC)
					.setColor(0x88b0f0)
					.setGroup("org.fox.ttrss")
					.addAction(R.drawable.ic_launcher, getString(R.string.cancel), cancelIntent);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setChannelId(CommonActivity.NOTIFICATION_CHANNEL_NORMAL);
		}

		m_nmgr.notify(NOTIFY_DOWNLOADING, builder.build());
	}

	/* private void updateNotification(int msgResId) {
		updateNotification(getString(msgResId));
	} */

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		m_queueSize++;

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		m_queueSize--;
		m_imagesDownloaded++;

		String url = intent.getStringExtra("url");

		Log.d(TAG, "got request to download URL=" + url + "; canProceed=" + m_canProceed);

		if (!m_canProceed || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			return;
		
		String hashedUrl = md5(url);
		
		File storage = getExternalCacheDir();
		File cachePath = new File(storage.getAbsolutePath() + CACHE_PATH);
		if (!cachePath.exists()) cachePath.mkdirs();
		
		if (cachePath.isDirectory() && hashedUrl != null) {
			File outputFile = new File(cachePath.getAbsolutePath() + "/" + hashedUrl);
			
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

						updateNotification(getString(R.string.notify_downloading_media), m_imagesDownloaded,
								m_imagesDownloaded+m_queueSize, true);
						
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
	    	
			/*Intent success = new Intent();
			success.setAction(OfflineDownloadService.INTENT_ACTION_SUCCESS);
			success.addCategory(Intent.CATEGORY_DEFAULT);
			sendBroadcast(success);*/

			try {
				unregisterReceiver(m_receiver);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (m_canProceed) {
				notifyDownloadSuccess();
			}
		}
	}
	
}
