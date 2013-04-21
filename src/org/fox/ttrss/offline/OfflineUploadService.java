package org.fox.ttrss.offline;

import java.util.HashMap;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.DatabaseHelper;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.JsonElement;

public class OfflineUploadService extends IntentService {
	private final String TAG = this.getClass().getSimpleName();
	
	public static final int NOTIFY_UPLOADING = 2;
	public static final String INTENT_ACTION_SUCCESS = "org.fox.ttrss.intent.action.UploadComplete";
	
	private SQLiteDatabase m_writableDb;
	private SQLiteDatabase m_readableDb;
	private String m_sessionId;
	private NotificationManager m_nmgr;
	private boolean m_uploadInProgress = false;
	
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
	private void updateNotification(String msg) {
		Notification notification = new Notification(R.drawable.icon, 
				getString(R.string.notify_uploading_title), System.currentTimeMillis());
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, OnlineActivity.class), 0);
		
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		
        notification.setLatestEventInfo(this, getString(R.string.notify_uploading_title), msg, contentIntent);
                       
        m_nmgr.notify(NOTIFY_UPLOADING, notification);
	}
	
	private void updateNotification(int msgResId) {
		updateNotification(getString(msgResId));
	}

	private void initDatabase() {
		DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
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
						updateNotification(getErrorMessage());
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
	};

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
						updateNotification(getErrorMessage());
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

        Intent intent = new Intent();
        intent.setAction(INTENT_ACTION_SUCCESS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(intent);
        
        m_readableDb.close();
        m_writableDb.close();
		
        m_uploadInProgress = false;
        
		m_nmgr.cancel(NOTIFY_UPLOADING);
	}
	
	private void uploadPublished() {
		Log.d(TAG, "syncing modified offline data... (published)");

		final String ids = getModifiedIds(ModifiedCriteria.MARKED);

		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						uploadSuccess();
					} else {
						updateNotification(getErrorMessage());
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
	
			if (!m_uploadInProgress) {
				m_uploadInProgress = true;
	
				updateNotification(R.string.notify_uploading_sending_data);
				
				uploadRead();			
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
