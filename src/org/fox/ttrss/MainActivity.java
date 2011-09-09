package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class MainActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private boolean m_feedsOpened = false;
	protected String m_sessionId;
	protected int m_offset = 0;
	protected int m_limit = 100;

	protected String getSessionId() {
		return m_sessionId;
	}
	
	protected synchronized void setSessionId(String sessionId) {
		m_sessionId = sessionId;
		
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("last_session_id", m_sessionId);	
		editor.commit();
	}
	
	private Timer m_timer;
	private TimerTask m_updateTask = new TimerTask() {
		@Override
		public void run() {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					downloadArticles();
				}

			});			
		}		
	};

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // allow database to upgrade before we do anything else
		DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
		SQLiteDatabase db = dh.getWritableDatabase();
		db.execSQL("DELETE FROM feeds;");
		db.execSQL("DELETE FROM articles;");
		db.close();
        
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());       
		
		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		m_themeName = m_prefs.getString("theme", "THEME_DARK");
        
		m_sessionId = m_prefs.getString("last_session_id", null);
		
		if (savedInstanceState != null) {
			m_feedsOpened = savedInstanceState.getBoolean("feedsOpened");
			m_sessionId = savedInstanceState.getString("sessionId");
		}
		
        setContentView(R.layout.main);
        
        if (!m_feedsOpened) {
        	Log.d(TAG, "Opening feeds fragment...");
        	
        	FragmentTransaction ft = getFragmentManager().beginTransaction();			
        	FeedsFragment frag = new FeedsFragment();
		
        	ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        	ft.replace(R.id.feeds_container, frag, "FEEDLIST");
        	ft.commit();
        	
        	m_feedsOpened = true;
        }

		m_timer = new Timer("UpdateArticles");
		m_timer.schedule(m_updateTask, 1000L, 5*1000L);
    }
    
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putBoolean("feedsOpened", m_feedsOpened);
		out.putString("sessionId", m_sessionId);
	}
    
	@Override
	public void onResume() {
		super.onResume();

		if (!m_prefs.getString("theme", "THEME_DARK").equals(m_themeName)) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			finish();
		}			
	}
    
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		m_timer.cancel();
		m_timer = null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void downloadArticles() {
		ApiRequest task = new ApiRequest(m_sessionId, 
				m_prefs.getString("ttrss_url", null),
				m_prefs.getString("login", null),
				m_prefs.getString("password", null)) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null && getAuthStatus() == STATUS_OK) {
					try {
						setSessionId(getSessionId());
						
						JsonArray feeds_object = (JsonArray) result.getAsJsonArray();
						
						Type listType = new TypeToken<List<Article>>() {}.getType();
						List<Article> articles = m_gson.fromJson(feeds_object, listType);

						DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
						SQLiteDatabase db = dh.getWritableDatabase();

						/* db.execSQL("DELETE FROM articles"); */
						
						SQLiteStatement stmtInsert = db.compileStatement("INSERT INTO articles " +
								"("+BaseColumns._ID+", unread, marked, published, updated, is_updated, title, link, feed_id, tags, content) " +
								"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

						SQLiteStatement stmtUpdate = db.compileStatement("UPDATE articles SET " +
								"unread = ?, marked = ?, published = ?, updated = ?, is_updated = ?, title = ?, link = ?, feed_id = ?, " +
								"tags = ?, content = ? WHERE " + BaseColumns._ID + " = ?");

						int articlesFound = 0;
						
						for (Article article : articles) {
							//Log.d(TAG, "Processing article #" + article.id);
							
							++articlesFound;
							
							Cursor c = db.query("articles", new String[] { BaseColumns._ID } , BaseColumns._ID + "=?", 
									new String[] { String.valueOf(article.id) }, null, null, null);
							
							if (c.getCount() != 0) {
								//Log.d(TAG, "article found");
								
							} else {
								//Log.d(TAG, "article not found");
						
								stmtInsert.bindLong(1, article.id);
								stmtInsert.bindLong(2, article.unread ? 1 : 0);
								stmtInsert.bindLong(3, article.marked ? 1 : 0);
								stmtInsert.bindLong(4, article.published ? 1 : 0);
								stmtInsert.bindLong(5, article.updated);
								stmtInsert.bindLong(6, article.is_updated ? 1 : 0);
								stmtInsert.bindString(7, article.title);
								stmtInsert.bindString(8, article.link);
								stmtInsert.bindLong(9, article.feed_id);
								stmtInsert.bindString(10, ""); // comma-separated tags
								stmtInsert.bindString(11, article.content);
								stmtInsert.execute();

							}
							
							c.close();
						}
						
						db.close();
						
						FeedsFragment ff = (FeedsFragment) getFragmentManager().findFragmentByTag("FEEDLIST");
						
						if (ff != null) {
							ff.m_cursor.requery();
							ff.m_adapter.notifyDataSetChanged();
						}
						
						Log.d(TAG, articlesFound + " articles processed");
						
						if (articlesFound == m_limit && m_offset <= 300) { 
							m_offset += m_limit;
						} else {
							m_offset = 0;
							m_timer.cancel();
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}										
				}
				
			} 
		};
		
		Log.d(TAG, "Requesting articles [offset=" + m_offset + "]");
		
		task.execute(new HashMap<String,String>() {   
			{
				put("sid", m_sessionId);
				put("op", "getHeadlines");
				put("feed_id", "-4");
				put("show_content", "1");
				put("limit", String.valueOf(m_limit));
				put("skip", String.valueOf(m_offset));
				put("view_mode", "unread");
			}			 
		});
	}				

}