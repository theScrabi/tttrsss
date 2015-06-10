package org.fox.ttrss.offline;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.JsonElement;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.DatabaseHelper;

import java.util.HashMap;

public class OfflineUploadService extends IntentService {
	private final String TAG = this.getClass().getSimpleName();
	
	public static final int NOTIFY_UPLOADING = 2;
	public static final String INTENT_ACTION_SUCCESS = "org.fox.ttrss.intent.action.UploadComplete";
	
	private SQLiteDatabase m_writableDb;
	private SQLiteDatabase m_readableDb;
	private String m_sessionId;
	private NotificationManager m_nmgr;
	private boolean m_uploadInProgress = false;
	private boolean m_batchMode = false;
	
	public OfflineUploadService() {
		super("OfflineUploadService");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		m_nmgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		initDatabase();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		m_nmgr.cancel(NOTIFY_UPLOADING);
	}

	@SuppressWarnings("deprecation")
	private void updateNotification(String msg, int progress, int max, boolean showProgress) {
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, OnlineActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setContentText(msg)
                .setContentTitle(getString(R.string.notify_uploading_title))
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis())
				.setProgress(0, 0, true)
                .setSmallIcon(R.drawable.ic_cloud_upload)
                .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        R.drawable.ic_launcher))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVibrate(new long[0]);

		if (showProgress) builder.setProgress(max, progress, max == 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setColor(0x88b0f0)
                    .setGroup("org.fox.ttrss");
        }

        m_nmgr.notify(NOTIFY_UPLOADING, builder.build());
	}
	
	private void updateNotification(int msgResId, int progress, int max, boolean showProgress) {
		updateNotification(getString(msgResId), progress, max, showProgress);
	}

	private void initDatabase() {
		DatabaseHelper dh = DatabaseHelper.getInstance(this);
		m_writableDb = dh.getWritableDatabase();
		m_readableDb = dh.getReadableDatabase();
	}
	
	private synchronized SQLiteDatabase getReadableDb() {
		return m_readableDb;
	}
	
	private synchronized SQLiteDatabase getWritableDb() {
		return m_writableDb;
	}
	
	private void uploadRead() {
		Log.d(TAG, "syncing modified offline data... (read)");

		final String ids = getModifiedIds(ModifiedCriteria.READ);

		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						uploadMarked();
					} else {
						updateNotification(getErrorMessage(), 0, 0, false);
						uploadFailed();
					}
				}
			};

			@SuppressWarnings("serial")
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", ids);
					put("mode", "0");
					put("field", "2");
				}
			};

			req.execute(map);
		} else {
			uploadMarked();
		}
	}
	
	private enum ModifiedCriteria {
		READ, MARKED, PUBLISHED
	}

    private String getModifiedIds(ModifiedCriteria criteria) {

		String criteriaStr = "";

		switch (criteria) {
		case READ:
			criteriaStr = "unread = 0";
			break;
		case MARKED:
			criteriaStr = "marked = 1";
			break;
		case PUBLISHED:
			criteriaStr = "published = 1";
			break;
		}

		Cursor c = getReadableDb().query("articles", null,
				"modified = 1 AND " + criteriaStr, null, null, null, null);

		String tmp = "";

		while (c.moveToNext()) {
			tmp += c.getInt(0) + ",";
		}

		tmp = tmp.replaceAll(",$", "");

		c.close();

		return tmp;
	}

	private void uploadMarked() {
		Log.d(TAG, "syncing modified offline data... (marked)");

		final String ids = getModifiedIds(ModifiedCriteria.MARKED);

		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						uploadPublished();
					} else {
						updateNotification(getErrorMessage(), 0, 0, false);
						uploadFailed();
					}
				}
			};

			@SuppressWarnings("serial")
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", ids);
					put("mode", "1");
					put("field", "0");
				}
			};

			req.execute(map);
		} else {
			uploadPublished();
		}
	}
	
	private void uploadFailed() {
        m_readableDb.close();
        m_writableDb.close();

        // TODO send notification to activity?
        
        m_uploadInProgress = false;
	}

	private void uploadSuccess() {
		getWritableDb().execSQL("UPDATE articles SET modified = 0");

		if (m_batchMode) {
			
			SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = localPrefs.edit();
			editor.putBoolean("offline_mode_active", false);
			editor.apply();
			
		} else {
	        Intent intent = new Intent();
	        intent.setAction(INTENT_ACTION_SUCCESS);
	        intent.addCategory(Intent.CATEGORY_DEFAULT);
	        sendBroadcast(intent);
		}
        
        m_readableDb.close();
        m_writableDb.close();
		
        m_uploadInProgress = false;
        
		m_nmgr.cancel(NOTIFY_UPLOADING);
	}
	
	private void uploadPublished() {
		Log.d(TAG, "syncing modified offline data... (published)");

		final String ids = getModifiedIds(ModifiedCriteria.PUBLISHED);

		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						uploadSuccess();
					} else {
						updateNotification(getErrorMessage(), 0, 0, false);
						uploadFailed();
					}
				}
			};

			@SuppressWarnings("serial")
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", ids);
					put("mode", "1");
					put("field", "1");
				}
			};

			req.execute(map);
		} else {
			uploadSuccess();
		}
	}

	
	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			if (getWritableDb().isDbLockedByCurrentThread() || getWritableDb().isDbLockedByOtherThreads()) {
				return;
			}
	
			m_sessionId = intent.getStringExtra("sessionId");
			m_batchMode = intent.getBooleanExtra("batchMode", false);
	
			if (!m_uploadInProgress) {
				m_uploadInProgress = true;
	
				updateNotification(R.string.notify_uploading_sending_data, 0, 0, true);
				
				uploadRead();			
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
