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
import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.DatabaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OfflineUploadService extends IntentService {
	private final String TAG = this.getClass().getSimpleName();

	public static final int NOTIFY_UPLOADING = 2;
	public static final String INTENT_ACTION_SUCCESS = "org.fox.ttrss.intent.action.UploadComplete";

	private String m_sessionId;
	private NotificationManager m_nmgr;
	private boolean m_uploadInProgress = false;
	private boolean m_batchMode = false;
	private DatabaseHelper m_databaseHelper;

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
	private void updateNotification(String msg, int progress, int max, boolean showProgress, boolean isError) {
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, OnlineActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setContentText(msg)
                .setContentTitle(getString(R.string.notify_uploading_title))
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_cloud_upload)
                .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        R.drawable.ic_launcher))
                .setOngoing(!isError)
                .setOnlyAlertOnce(true)
                .setVibrate(new long[0]);

		if (showProgress) builder.setProgress(max, progress, max == 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setColor(0x88b0f0)
                    .setGroup("org.fox.ttrss")
					.addAction(R.drawable.ic_launcher, getString(R.string.offline_sync_try_again), contentIntent);
        }

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.setChannelId(CommonActivity.NOTIFICATION_CHANNEL_NORMAL);
		}

		m_nmgr.notify(NOTIFY_UPLOADING, builder.build());
	}
	
	private void updateNotification(int msgResId, int progress, int max, boolean showProgress, boolean isError) {
		updateNotification(getString(msgResId), progress, max, showProgress, isError);
	}

	private void initDatabase() {
		m_databaseHelper = DatabaseHelper.getInstance(this);
	}

	private synchronized SQLiteDatabase getDatabase() {
		return m_databaseHelper.getWritableDatabase();
	}

	private enum ModifiedCriteria {
		READ, MARKED, UNMARKED, PUBLISHED, UNPUBLISHED
	}

    private List<Integer> getModifiedIds(ModifiedCriteria criteria) {

		String criteriaStr = "";

		switch (criteria) {
		case READ:
			criteriaStr = "unread = 0";
			break;
		case MARKED:
			criteriaStr = "modified_marked = 1 AND marked = 1";
			break;
		case UNMARKED:
			criteriaStr = "modified_marked = 1 AND marked = 0";
			break;
		case PUBLISHED:
			criteriaStr = "modified_published = 1 AND published = 1";
			break;
		case UNPUBLISHED:
			criteriaStr = "modified_published = 1 AND published = 0";
			break;
		}

		Cursor c = getDatabase().query("articles", null,
				"modified = 1 AND " + criteriaStr, null, null, null, null);

		List<Integer> tmp = new ArrayList<>();

		while (c.moveToNext()) {
			tmp.add(c.getInt(0));
		}

		c.close();

		return tmp;
	}

	private void uploadFailed() {
        m_uploadInProgress = false;
	}

	private void uploadSuccess() {
		getDatabase().execSQL("UPDATE articles SET modified = 0");

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

        m_uploadInProgress = false;
        
		m_nmgr.cancel(NOTIFY_UPLOADING);
	}

	interface CriteriaCallback {
		void onUploadSuccess();
	}

	private void uploadByCriteria(final ModifiedCriteria criteria, final CriteriaCallback callback) {

		final List<Integer> ids = getModifiedIds(criteria);

		Log.d(TAG, "syncing modified offline data for " + criteria + ": " + ids);

		if (ids.size() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						callback.onUploadSuccess();
					} else {
						Log.d(TAG, "syncing failed: " + getErrorMessage());

						updateNotification(getErrorMessage(), 0, 0, false, true);
						uploadFailed();
					}
				}
			};

			@SuppressWarnings("serial")
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", android.text.TextUtils.join(",", ids));

					switch (criteria) {
						case READ:
							put("mode", "0");
							put("field", "2");
							break;
						case PUBLISHED:
							put("mode", "1");
							put("field", "1");
							break;
						case UNPUBLISHED:
							put("mode", "0");
							put("field", "1");
							break;
						case MARKED:
							put("mode", "1");
							put("field", "0");
							break;
						case UNMARKED:
							put("mode", "0");
							put("field", "0");
							break;
					}
				}
			};

			req.execute(map);
		} else {
			callback.onUploadSuccess();
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			if (getDatabase().isDbLockedByCurrentThread() || getDatabase().isDbLockedByOtherThreads()) {
				return;
			}
	
			m_sessionId = intent.getStringExtra("sessionId");
			m_batchMode = intent.getBooleanExtra("batchMode", false);
	
			if (!m_uploadInProgress) {
				m_uploadInProgress = true;
	
				updateNotification(R.string.notify_uploading_sending_data, 0, 0, true, true);
				
				uploadByCriteria(ModifiedCriteria.READ, new CriteriaCallback() {
					@Override
					public void onUploadSuccess() {
						uploadByCriteria(ModifiedCriteria.MARKED, new CriteriaCallback() {
							@Override
							public void onUploadSuccess() {
								uploadByCriteria(ModifiedCriteria.UNMARKED, new CriteriaCallback() {
									@Override
									public void onUploadSuccess() {
										uploadByCriteria(ModifiedCriteria.PUBLISHED, new CriteriaCallback() {
											@Override
											public void onUploadSuccess() {
												uploadByCriteria(ModifiedCriteria.UNPUBLISHED, new CriteriaCallback() {
													@Override
													public void onUploadSuccess() {
														Log.d(TAG, "upload complete");

														uploadSuccess();
													}
												});
											}
										});
									}
								});
							}
						});
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
