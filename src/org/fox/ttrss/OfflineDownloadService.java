package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class OfflineDownloadService extends IntentService {

	private final String TAG = this.getClass().getSimpleName();

	public static final int NOTIFY_DOWNLOADING = 1;

	private static final int OFFLINE_SYNC_SEQ = 60;
	private static final int OFFLINE_SYNC_MAX = 60; //500
	
	private SQLiteDatabase m_writableDb;
	private SQLiteDatabase m_readableDb;
	private int m_articleOffset = 0;
	private String m_sessionId;
	private NotificationManager m_nmgr;
	
	private boolean m_downloadInProgress = false;
	
	public OfflineDownloadService() {
		super("OfflineDownloadService");
	}
	
	public OfflineDownloadService(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		m_nmgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		initDatabase();
	}
	
	public boolean getDownloadInProgress() {
		return m_downloadInProgress;
	}
	
	private void updateNotification(String msg) {
		Notification notification = new Notification(R.drawable.icon, 
				getString(R.string.app_name), System.currentTimeMillis());
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
		
        notification.setLatestEventInfo(this, getString(R.string.go_offline), msg, contentIntent);
                       
        m_nmgr.notify(NOTIFY_DOWNLOADING, notification);
	}

	private void updateNotification(int msgResId) {
		updateNotification(getString(msgResId));
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public void downloadComplete() {
		m_downloadInProgress = false;
		
        m_nmgr.cancel(NOTIFY_DOWNLOADING);
        
        Intent intent = new Intent();
        intent.setAction("org.fox.ttrss.intent.action.DownloadComplete");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(intent);
        
        m_readableDb.close();
        m_writableDb.close();
	}
	
	private void initDatabase() {
		DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
		m_writableDb = dh.getWritableDatabase();
		m_readableDb = dh.getReadableDatabase();
	}
	
	public synchronized SQLiteDatabase getReadableDb() {
		return m_readableDb;
	}
	
	public synchronized SQLiteDatabase getWritableDb() {
		return m_writableDb;
	}
	
	@SuppressWarnings("unchecked")
	private void downloadArticles() {
		Log.d(TAG, "offline: downloading articles... offset=" + m_articleOffset);
		
		updateNotification(getString(R.string.notify_downloading_articles, m_articleOffset));

		OfflineArticlesRequest req = new OfflineArticlesRequest(this);
		
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", m_sessionId);
				put("feed_id", "-4");
				put("view_mode", "unread");
				put("show_content", "true");
				put("skip", String.valueOf(m_articleOffset));
				put("limit", String.valueOf(OFFLINE_SYNC_SEQ));
			}			 
		};
		
		req.execute(map);
	}
	
	private void downloadFeeds() {
		//findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
		//findViewById(R.id.main).setVisibility(View.INVISIBLE);
		
		//setLoadingStatus(R.string.offline_downloading, true);
		
		// Download feeds
		
		updateNotification(R.string.notify_downloading_feeds);
		
		getWritableDb().execSQL("DELETE FROM feeds;");
		
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			@Override
			protected void onPostExecute(JsonElement content) {
				if (content != null) {
					
					try {
						Type listType = new TypeToken<List<Feed>>() {}.getType();
						List<Feed> feeds = new Gson().fromJson(content, listType);
						
						SQLiteStatement stmtInsert = getWritableDb().compileStatement("INSERT INTO feeds " +
								"("+BaseColumns._ID+", title, feed_url, has_icon, cat_id) " +
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
						
						getWritableDb().execSQL("DELETE FROM articles;");
						downloadArticles();
					} catch (Exception e) {
						e.printStackTrace();
						updateNotification(R.string.offline_switch_error);
						m_downloadInProgress = false;
						//setLoadingStatus(R.string.offline_switch_error, false);
					}
				
				} else {
					updateNotification(getErrorMessage());
					m_downloadInProgress = false;
					// TODO error, could not download feeds, properly report API error (toast)
				}
			}

		};
		
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getFeeds");
				put("sid", m_sessionId);
				put("cat_id", "-3");
				put("unread_only", "true");
			}			 
		};
		
		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	private void switchOffline() {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this).  
			setMessage(R.string.dialog_offline_switch_prompt).
			setPositiveButton(R.string.dialog_offline_go, new Dialog.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					
					Log.d(TAG, "offline: starting");
					
					if (m_sessionId != null) {
					
						//findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
						//findViewById(R.id.main).setVisibility(View.INVISIBLE);
						
						//setLoadingStatus(R.string.offline_downloading, true);
						
						// Download feeds
						
						getWritableDb().execSQL("DELETE FROM feeds;");
						
						ApiRequest req = new ApiRequest(getApplicationContext()) {
							@Override
							protected void onPostExecute(JsonElement content) {
								if (content != null) {
									
									try {
										Type listType = new TypeToken<List<Feed>>() {}.getType();
										List<Feed> feeds = new Gson().fromJson(content, listType);
										
										SQLiteStatement stmtInsert = getWritableDb().compileStatement("INSERT INTO feeds " +
												"("+BaseColumns._ID+", title, feed_url, has_icon, cat_id) " +
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
										
										getWritableDb().execSQL("DELETE FROM articles;");
				
										downloadArticles();
									} catch (Exception e) {
										e.printStackTrace();
										//setLoadingStatus(R.string.offline_switch_error, false);
									}
								
								} else {
									//setLoadingStatus(getErrorMessage(), false);
									// TODO error, could not download feeds, properly report API error (toast)
								}
							}
						};
						
						HashMap<String,String> map = new HashMap<String,String>() {
							{
								put("op", "getFeeds");
								put("sid", m_sessionId);
								put("cat_id", "-3");
								put("unread_only", "true");
							}			 
						};
						
						req.execute(map);
					} else {
						downloadComplete();
					}
				}
			}).
			setNegativeButton(R.string.dialog_cancel, new Dialog.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					//
				}
			});
	
		AlertDialog dlg = builder.create();
		dlg.show();

	}
	
	public void download() {
		if (!m_downloadInProgress) {
			updateNotification(R.string.notify_downloading_init);
			m_downloadInProgress = true;
		
			downloadFeeds();
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		m_nmgr.cancel(NOTIFY_DOWNLOADING);
		
		//m_readableDb.close();
		//m_writableDb.close();
	}

	public class OfflineArticlesRequest extends ApiRequest {
		public OfflineArticlesRequest(Context context) {
			super(context);
		}

		@Override
		protected void onPostExecute(JsonElement content) {
			if (content != null) {
				try {
					Type listType = new TypeToken<List<Article>>() {}.getType();
					List<Article> articles = new Gson().fromJson(content, listType);
	
					SQLiteStatement stmtInsert = getWritableDb().compileStatement("INSERT INTO articles " +
							"("+BaseColumns._ID+", unread, marked, published, updated, is_updated, title, link, feed_id, tags, content) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
	
					for (Article article : articles) {
	
						String tagsString = "";
						
						for (String t : article.tags) {
							tagsString += t + ", ";
						}
						
						tagsString = tagsString.replaceAll(", $", "");
						
						stmtInsert.bindLong(1, article.id);
						stmtInsert.bindLong(2, article.unread ? 1 : 0);
						stmtInsert.bindLong(3, article.marked ? 1 : 0);
						stmtInsert.bindLong(4, article.published ? 1 : 0);
						stmtInsert.bindLong(5, article.updated);
						stmtInsert.bindLong(6, article.is_updated ? 1 : 0);
						stmtInsert.bindString(7, article.title);
						stmtInsert.bindString(8, article.link);
						stmtInsert.bindLong(9, article.feed_id);
						stmtInsert.bindString(10, tagsString); // comma-separated tags
						stmtInsert.bindString(11, article.content);
						
						try {
							stmtInsert.execute();
						} catch (Exception e) {
							e.printStackTrace();
						}
	
					}

					stmtInsert.close();

					//m_canGetMoreArticles = articles.size() == 30;
					m_articleOffset += articles.size();
	
					Log.d(TAG, "offline: received " + articles.size() + " articles");
					
					if (articles.size() == OFFLINE_SYNC_SEQ && m_articleOffset < OFFLINE_SYNC_MAX) {
						downloadArticles();
					} else {
						downloadComplete();
					}
					
					return;
					
				} catch (Exception e) {
					updateNotification(R.string.offline_switch_error);
					Log.d(TAG, "offline: failed: exception when loading articles");
					e.printStackTrace();
					m_downloadInProgress = false;
				}
				
			} else {
				Log.d(TAG, "offline: failed: " + getErrorMessage());
				m_downloadInProgress = false;
				updateNotification(getErrorMessage());
			}
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle extras = intent.getExtras();
		
		m_sessionId = extras.getString("sessionId");
		
		download();
	}
}
