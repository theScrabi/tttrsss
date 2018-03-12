package org.fox.ttrss.offline;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.BuildConfig;
import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.util.DatabaseHelper;
import org.fox.ttrss.util.ImageCacheService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

public class OfflineDownloadService extends Service {

	private final String TAG = this.getClass().getSimpleName();

	// enable downloading read articles in debug configuration for testing
	private static boolean OFFLINE_DEBUG_READ = false;

	public static final int NOTIFY_DOWNLOADING = 1;
	public static final int NOTIFY_DOWNLOAD_SUCCESS = 2;

	public static final int PI_GENERIC = 0;
	public static final int PI_CANCEL = 1;
	public static final int PI_SUCCESS = 2;

	//public static final String INTENT_ACTION_SUCCESS = "org.fox.ttrss.intent.action.DownloadComplete";
	public static final String INTENT_ACTION_CANCEL = "org.fox.ttrss.intent.action.Cancel";
	public static final String INTENT_ACTION_SWITCH_OFFLINE = "org.fox.ttrss.intent.action.SwitchOffline";

	private static final int OFFLINE_SYNC_SEQ = 50;
	private static final int OFFLINE_SYNC_MAX = OFFLINE_SYNC_SEQ * 10;
	
	private int m_articleOffset = 0;
	private String m_sessionId;
	private NotificationManager m_nmgr;
	
	private boolean m_batchMode = false;
	private boolean m_downloadInProgress = false;
	private boolean m_downloadImages = false;
	private int m_syncMax;
	private SharedPreferences m_prefs;
	private boolean m_canProceed = true;
	
	private final IBinder m_binder = new LocalBinder();
	private DatabaseHelper m_databaseHelper;

	public class LocalBinder extends Binder {
        OfflineDownloadService getService() {
            return OfflineDownloadService.this;
        }
    }
	
    @Override
    public IBinder onBind(Intent intent) {
        return m_binder;
    }
    
	@Override
	public void onCreate() {
		super.onCreate();
		m_nmgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		m_prefs = PreferenceManager
						.getDefaultSharedPreferences(getApplicationContext());
 
		m_downloadImages = m_prefs.getBoolean("offline_image_cache_enabled", false);
		m_syncMax = Integer.parseInt(m_prefs.getString("offline_sync_max", String.valueOf(OFFLINE_SYNC_MAX)));
		
		initDatabase();
	}
	
	@SuppressWarnings("deprecation")
	private void updateNotification(String msg, int progress, int max, boolean showProgress, boolean isError) {
		Intent intent = new Intent(this, OnlineActivity.class);
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, PI_GENERIC,
                intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setContentText(msg)
                .setContentTitle(getString(R.string.notify_downloading_title))
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_cloud_download)
                .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        R.drawable.ic_launcher))
                .setOngoing(!isError)
                .setOnlyAlertOnce(true);

		if (showProgress) builder.setProgress(max, progress, max == 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

			intent = new Intent(this, OnlineActivity.class);
			intent.setAction(INTENT_ACTION_CANCEL);

			PendingIntent cancelIntent = PendingIntent.getActivity(this, PI_CANCEL, intent, 0);

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

	@SuppressWarnings("deprecation")
	private void notifyDownloadComplete() {
		Intent intent = new Intent(this, OnlineActivity.class);

		if (m_articleOffset > 0) {
			intent.setAction(INTENT_ACTION_SWITCH_OFFLINE);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, PI_SUCCESS,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
				.setContentIntent(contentIntent)
				.setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_notification)
				.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(),
						R.drawable.ic_launcher))
				.setOnlyAlertOnce(true)
				.setPriority(Notification.PRIORITY_HIGH)
				.setDefaults(Notification.DEFAULT_ALL)
				.setAutoCancel(true);

		if (m_articleOffset > 0) {
			builder
					.setContentTitle(getString(R.string.dialog_offline_success))
					.setContentText(getString(R.string.offline_tap_to_switch));
		} else {
			builder
					.setContentTitle(getString(R.string.offline_switch_failed))
					.setContentText(getString(R.string.offline_no_articles));

		}

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

	private void updateNotification(int msgResId, int progress, int max, boolean showProgress, boolean isError) {
		updateNotification(getString(msgResId), progress, max, showProgress, isError);
	}

	private void downloadFailed() {
        //m_nmgr.cancel(NOTIFY_DOWNLOADING);
        
        // TODO send notification to activity?
        
        m_downloadInProgress = false;
        stopSelf();
	}
	
	private boolean isCacheServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if ("org.fox.ttrss.util.ImageCacheService".equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public void downloadComplete() {
		m_downloadInProgress = false;
		
        // if cache service is running, it will send a finished intent on its own
        if (!isCacheServiceRunning()) {
            m_nmgr.cancel(NOTIFY_DOWNLOADING);

            if (m_batchMode) {
            	
            	SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = localPrefs.edit();
				editor.putBoolean("offline_mode_active", true);
				editor.apply();
            	
            } else {

            	/*Intent intent = new Intent();
            	intent.setAction(INTENT_ACTION_SUCCESS);
            	intent.addCategory(Intent.CATEGORY_DEFAULT);
            	sendBroadcast(intent);*/

				notifyDownloadComplete();
            }
        }

        stopSelf();
	}
	
	private void initDatabase() {
		m_databaseHelper = DatabaseHelper.getInstance(this);
	}
	
	/* private synchronized SQLiteDatabase getReadableDb() {
		return m_readableDb;
	} */
	
	private synchronized SQLiteDatabase getDatabase() {
		return m_databaseHelper.getWritableDatabase();
	}
	
	@SuppressWarnings("unchecked")
	private void downloadArticles() {
		Log.d(TAG, "offline: downloading articles... offset=" + m_articleOffset);
		
		updateNotification(getString(R.string.notify_downloading_articles, m_articleOffset), m_articleOffset, m_syncMax, true, false);
		
		OfflineArticlesRequest req = new OfflineArticlesRequest(this);
		
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", m_sessionId);
				put("feed_id", "-4");

				if (BuildConfig.DEBUG && OFFLINE_DEBUG_READ) {
					put("view_mode", "all_articles");
				} else {
					put("view_mode", "unread");
				}
				put("show_content", "true");
				put("skip", String.valueOf(m_articleOffset));
				put("limit", String.valueOf(OFFLINE_SYNC_SEQ));
			}			 
		};
		
		req.execute(map);
	}
	
	private void downloadFeeds() {

		updateNotification(R.string.notify_downloading_feeds, 0, 0, true, false);
		
		getDatabase().execSQL("DELETE FROM feeds;");
		
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			@Override
			protected JsonElement doInBackground(HashMap<String, String>... params) {
				JsonElement content = super.doInBackground(params);

				if (content != null) {

					try {
						Type listType = new TypeToken<List<Feed>>() {}.getType();
						List<Feed> feeds = new Gson().fromJson(content, listType);
						
						SQLiteStatement stmtInsert = getDatabase().compileStatement("INSERT INTO feeds " +
								"(" + BaseColumns._ID + ", title, feed_url, has_icon, cat_id) " +
								"VALUES (?, ?, ?, ?, ?);");
						
						for (Feed feed : feeds) {
							stmtInsert.bindLong(1, feed.id);
							stmtInsert.bindString(2, feed.title);
							stmtInsert.bindString(3, feed.feed_url);
							stmtInsert.bindLong(4, feed.has_icon ? 1 : 0);
							stmtInsert.bindLong(5, feed.cat_id);
	
							stmtInsert.execute();
						}
	
						stmtInsert.close();
	
						Log.d(TAG, "offline: done downloading feeds");
						
						m_articleOffset = 0;
						
						getDatabase().execSQL("DELETE FROM articles;");
					} catch (Exception e) {
						e.printStackTrace();
						updateNotification(getErrorMessage(), 0, 0, false, true);
						downloadFailed();
					}
				}
				
				return content;
			}
			
			@Override
			protected void onPostExecute(JsonElement content) {
				if (content != null) {
					if (m_canProceed) { 
						downloadArticles();
					} else {
						downloadFailed();
					}
				} else {
					updateNotification(getErrorMessage(), 0, 0, false, true);
					downloadFailed();
				}
			}

		};
		
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getFeeds");
				put("sid", m_sessionId);
				put("cat_id", "-3");

				if (!BuildConfig.DEBUG && OFFLINE_DEBUG_READ) {
					put("unread_only", "true");
				}
			}			 
		};
		
		req.execute(map);
	}

	private void downloadCategories() {

		updateNotification(R.string.notify_downloading_categories, 0, 0, true, false);
		
		getDatabase().execSQL("DELETE FROM categories;");
		
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected JsonElement doInBackground(HashMap<String, String>... params) {
				JsonElement content = super.doInBackground(params);
				
				if (content != null) {
					try {
						Type listType = new TypeToken<List<FeedCategory>>() {}.getType();
						List<FeedCategory> cats = new Gson().fromJson(content, listType);
						
						SQLiteStatement stmtInsert = getDatabase().compileStatement("INSERT INTO categories " +
								"(" + BaseColumns._ID + ", title) " +
								"VALUES (?, ?);");
						
						for (FeedCategory cat : cats) {
							stmtInsert.bindLong(1, cat.id);
							stmtInsert.bindString(2, cat.title);

							stmtInsert.execute();
						}

						stmtInsert.close();

						Log.d(TAG, "offline: done downloading categories");
						
					} catch (Exception e) {
						e.printStackTrace();
						updateNotification(getErrorMessage(), 0, 0, false, true);
						downloadFailed();
					}
				}
			
				return content;
			}
			@Override
			protected void onPostExecute(JsonElement content) {
				if (content != null) {
					if (m_canProceed) { 
						downloadFeeds();
					} else {
						downloadFailed();
					}
				} else {
					updateNotification(getErrorMessage(), 0, 0, false, true);
					downloadFailed();
				}
			}

		};
		
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getCategories");
				put("sid", m_sessionId);
				//put("cat_id", "-3");

				if (!BuildConfig.DEBUG && OFFLINE_DEBUG_READ) {
					put("unread_only", "true");
				}
			}			 
		};
		
		req.execute(map);
	}

	
	@Override
	public void onDestroy() {
		super.onDestroy();
		m_nmgr.cancel(NOTIFY_DOWNLOADING);

		m_canProceed = false;
		Log.d(TAG, "onDestroy");

	}

	public class OfflineArticlesRequest extends ApiRequest {
		List<Article> m_articles;
		
		public OfflineArticlesRequest(Context context) {
			super(context);
		}

		@Override
		protected JsonElement doInBackground(HashMap<String, String>... params) {
			JsonElement content = super.doInBackground(params);
			
			if (content != null) {

				try {
					Type listType = new TypeToken<List<Article>>() {}.getType();
					m_articles = new Gson().fromJson(content, listType);
	
					SQLiteStatement stmtInsert = getDatabase().compileStatement("INSERT INTO articles " +
							"(" + BaseColumns._ID + ", unread, marked, published, score, updated, is_updated, title, link, feed_id, tags, content, author) " +
							"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
	
					for (Article article : m_articles) {
	
						String tagsString = "";
						
						for (String t : article.tags) {
							tagsString += t + ", ";
						}
						
						tagsString = tagsString.replaceAll(", $", "");
						
						int index = 1;
						stmtInsert.bindLong(index++, article.id);
						stmtInsert.bindLong(index++, article.unread ? 1 : 0);
						stmtInsert.bindLong(index++, article.marked ? 1 : 0);
						stmtInsert.bindLong(index++, article.published ? 1 : 0);
						stmtInsert.bindLong(index++, article.score);
						stmtInsert.bindLong(index++, article.updated);
						stmtInsert.bindLong(index++, article.is_updated ? 1 : 0);
						stmtInsert.bindString(index++, article.title);
						stmtInsert.bindString(index++, article.link);
						stmtInsert.bindLong(index++, article.feed_id);
						stmtInsert.bindString(index++, tagsString); // comma-separated tags
						stmtInsert.bindString(index++, article.content);
						stmtInsert.bindString(index++, article.author != null ? article.author : "");
						
						if (m_downloadImages) {
							Document doc = Jsoup.parse(article.content);
							
							if (doc != null) {
								Elements images = doc.select("img,source");
								
								for (Element img : images) {
									String url = img.attr("src");
									
									if (url.indexOf("://") != -1) {
										if (!ImageCacheService.isUrlCached(OfflineDownloadService.this, url)) {										
											Intent intent = new Intent(OfflineDownloadService.this,
													ImageCacheService.class);
										
											intent.putExtra("url", url);
											startService(intent);
										}
									}
								}

								Elements videos = doc.select("video");

								for (Element vid : videos) {
									String url = vid.attr("poster");

									if (url.indexOf("://") != -1) {
										if (!ImageCacheService.isUrlCached(OfflineDownloadService.this, url)) {
											Intent intent = new Intent(OfflineDownloadService.this,
													ImageCacheService.class);

											intent.putExtra("url", url);
											startService(intent);
										}
									}
								}

							}
						}
						
						try {
							stmtInsert.execute();
						} catch (Exception e) {
							e.printStackTrace();
						}
	
					}

					m_articleOffset += m_articles.size();

					Log.d(TAG, "offline: received " + m_articles.size() + " articles; canProc=" + m_canProceed);

					stmtInsert.close();

				} catch (Exception e) {
					updateNotification(R.string.offline_switch_failed, 0, 0, false, true);
					Log.d(TAG, "offline: failed: exception when loading articles");
					e.printStackTrace();
					downloadFailed();
				}
				
			}
			
			return content;
		}
		
		@Override
		protected void onPostExecute(JsonElement content) {
			if (content != null) {
				
				if (m_canProceed && m_articles != null) {
					if (m_articles.size() == OFFLINE_SYNC_SEQ && m_articleOffset < m_syncMax) {
						downloadArticles();
					} else {
						downloadComplete();
					}
				} else {
					downloadFailed();
				}

			} else {
				Log.d(TAG, "offline: failed: " + getErrorMessage());
				updateNotification(getErrorMessage(), 0, 0, false, true);
				downloadFailed();
			}
		}
	}

	@Override
	public void onStart(Intent intent, int startId) {
		try {
			if (getDatabase().isDbLockedByCurrentThread() || getDatabase().isDbLockedByOtherThreads()) {
				return;
			}
			
			m_sessionId = intent.getStringExtra("sessionId");
			m_batchMode = intent.getBooleanExtra("batchMode", false);
		
			if (!m_downloadInProgress) {
				if (m_downloadImages) ImageCacheService.cleanupCache(this, false);
			
				updateNotification(R.string.notify_downloading_init, 0, 0, true, false);
				m_downloadInProgress = true;
		
				downloadCategories();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
