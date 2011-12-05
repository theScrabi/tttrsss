package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class OfflineActivity extends FragmentActivity  {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private Menu m_menu;
	private boolean m_smallScreenMode;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_compatMode = false;
	private boolean m_enableCats = false;
	
	private int m_activeOfflineFeedId = 0;
	private int m_selectedOfflineArticleId = 0;
	
	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;
	
	/** Called when the activity is first created. */

	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());       

		m_compatMode = android.os.Build.VERSION.SDK_INT <= 10;
		
		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);

		m_themeName = m_prefs.getString("theme", "THEME_DARK");
	
		if (savedInstanceState != null) {
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_unreadArticlesOnly = savedInstanceState.getBoolean("unreadArticlesOnly");
			m_activeOfflineFeedId = savedInstanceState.getInt("offlineActiveFeedId");
			m_selectedOfflineArticleId = savedInstanceState.getInt("offlineArticleId");
		}
		
		m_enableCats = m_prefs.getBoolean("enable_cats", false);
		
		Display display = getWindowManager().getDefaultDisplay();
		
		int width = display.getWidth();
		int height = display.getHeight();
		
		if (height > width) { int tmp = height; width = tmp; height = width; }
		
		m_smallScreenMode = width < 960 || height < 720; 
		
		setContentView(R.layout.main);

		initDatabase();
		
		Log.d(TAG, "m_smallScreenMode=" + m_smallScreenMode);
		Log.d(TAG, "m_compatMode=" + m_compatMode);

		if (!m_compatMode) {
			new TransitionHelper((LinearLayout)findViewById(R.id.main));
		}

		List<PackageInfo> pkgs = getPackageManager().getInstalledPackages(0);
		
		findViewById(R.id.cats_fragment).setVisibility(View.GONE);
		findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
		findViewById(R.id.article_fragment).setVisibility(View.GONE);

		initMainMenu();
		
		findViewById(R.id.loading_container).setVisibility(View.INVISIBLE);
		findViewById(R.id.main).setVisibility(View.VISIBLE);

		if (m_activeOfflineFeedId == 0) {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			OfflineFeedsFragment frag = new OfflineFeedsFragment(); 
			ft.replace(R.id.feeds_fragment, frag);
			ft.commit();
		} else {
			//
		}
	}

	public void initDatabase() {
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
	
	public void switchOnline() {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("offline_mode_active", false);
		editor.commit();
		
		Intent refresh = new Intent(this, MainActivity.class);
		startActivity(refresh);
		finish();
	}
	
	public int getActiveOfflineFeedId() {
		return m_activeOfflineFeedId;
	}
	
	public void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView)findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(status);
		}
		
		View pb = findViewById(R.id.loading_progress);
		
		if (pb != null) {
			pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
		}
	}
				
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putInt("offlineActiveFeedId", m_activeOfflineFeedId);
		out.putInt("offlineArticleId", m_selectedOfflineArticleId);
	}

	@Override
	public void onResume() {
		super.onResume();

		boolean needRefresh = !m_prefs.getString("theme", "THEME_DARK").equals(m_themeName) ||
			m_prefs.getBoolean("enable_cats", false) != m_enableCats;
		
		if (needRefresh) {
			Intent refresh = new Intent(this, OfflineActivity.class);
			startActivity(refresh);
			finish();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		
		m_menu = menu;
		
		initMainMenu();
		
		MenuItem item = menu.findItem(R.id.show_feeds);
		
		/* if (getUnreadOnly()) {
			item.setTitle(R.string.menu_all_feeds);
		} else {
			item.setTitle(R.string.menu_unread_feeds);
		} */

		return true;
	}

	public void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);
		
		if (mi != null) {
			mi.setTitle(labelId);
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	
        	if (m_smallScreenMode) {
        		if (m_selectedOfflineArticleId != 0) {
        			closeArticle();
        		} else if (m_activeOfflineFeedId != 0) {
        			if (m_compatMode) {
        				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
        			}
        			
        			/* if (m_activeFeed != null && m_activeFeed.is_cat) {
        				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
        				findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
        				
            			refreshCategories();
        			} else { */
        				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
        				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
        			//}
    				m_activeOfflineFeedId = 0;
        			initMainMenu();

        		} else {
        			finish();
        		}
        	} else {
	        	if (m_selectedOfflineArticleId != 0) {
	        		closeArticle();
	        	} else {
	        		finish();
	        	}
        	}

        	return false;
        }
        return super.onKeyDown(keyCode, event);
    }
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.go_online:
			switchOnline();
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	public void refreshFeeds() {
		// TODO
	}
	
	private void closeArticle() {
		if (m_compatMode) {
			findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
		}

		if (m_smallScreenMode) {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);	
		} else {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			
		}

		m_selectedOfflineArticleId = 0;

		initMainMenu();
		refreshFeeds();

	}

	public void initMainMenu() {
		if (m_menu != null) {
			m_menu.setGroupVisible(R.id.menu_group_logged_in, false);
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);

			m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
			m_menu.findItem(R.id.go_online).setVisible(true);
		}
	}		
	
	
	@Override
	public void onPause() {
		super.onPause();
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	
		m_readableDb.close();
		m_writableDb.close();
		
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

    	switch (item.getItemId()) {
			default:
		    	Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
				return super.onContextItemSelected(item);
    	}
	}

	/* @Override
	public boolean dispatchKeyEvent(KeyEvent event) {
	    int action = event.getAction();
	    int keyCode = event.getKeyCode();
	        switch (keyCode) {
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	            if (action == KeyEvent.ACTION_DOWN) {
	            	HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);

	            	if (hf != null && m_activeFeed != null) {
	            		Article base = hf.getArticleById(hf.getActiveArticleId());
	            		
	            		Article next = base != null ? getRelativeArticle(base, RelativeArticle.AFTER) : hf.getArticleAtPosition(0);
	            		
	            		if (next != null) {
	            			hf.setActiveArticleId(next.id);
	            			
	            			boolean combinedMode = m_prefs.getBoolean("combined_mode", false);
	            			
	            			if (combinedMode || m_selectedArticle == null) {
	            				next.unread = false;
	            				saveArticleUnread(next);
	            			} else {
	            				openArticle(next, 0);
	            			}
	            		}
	            	}
	            }
	            return true;
	        case KeyEvent.KEYCODE_VOLUME_UP:
	            if (action == KeyEvent.ACTION_UP) {
	            	HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);

	            	if (hf != null && m_activeFeed != null) {
	            		Article base = hf.getArticleById(hf.getActiveArticleId());
	            		
	            		Article prev = base != null ? getRelativeArticle(base, RelativeArticle.BEFORE) : hf.getArticleAtPosition(0);
	            		
	            		if (prev != null) {
	            			hf.setActiveArticleId(prev.id);
	            			
	            			boolean combinedMode = m_prefs.getBoolean("combined_mode", false);
	            			
	            			if (combinedMode || m_selectedArticle == null) {
	            				prev.unread = false;
	            				saveArticleUnread(prev);
	            			} else {
	            				openArticle(prev, 0);
	            			}
	            		}
	            	}

	            }
	            return true;
	        default:
	            return super.dispatchKeyEvent(event);
	        }
	    } */

	public void offlineViewFeed(int feedId) {
		m_activeOfflineFeedId = feedId;
		
		initMainMenu();
		
		if (m_smallScreenMode) {
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
		}
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		OfflineHeadlinesFragment frag = new OfflineHeadlinesFragment(); 
		ft.replace(R.id.headlines_fragment, frag);
		ft.commit();
		
	}

	public void openOfflineArticle(int articleId, int compatAnimation) {
		m_selectedOfflineArticleId = articleId;
		
		initMainMenu();

		OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		
		if (hf != null) {
			hf.setActiveArticleId(articleId);
		}
		
		OfflineArticleFragment frag = new OfflineArticleFragment();
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
		ft.replace(R.id.article_fragment, frag);
		ft.commit();

		if (m_compatMode) {
			if (compatAnimation == 0)
				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_left));
			else
				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, compatAnimation));
		}

		if (m_smallScreenMode) {
			findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
			findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		}

		
	}

	public int getSelectedOfflineArticleId() {
		return m_selectedOfflineArticleId;
	}
}