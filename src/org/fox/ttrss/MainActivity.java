package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.fox.ttrss.billing.BillingHelper;
import org.fox.ttrss.billing.BillingService;
import org.fox.ttrss.offline.OfflineActivity;
import org.fox.ttrss.offline.OfflineDownloadService;
import org.fox.ttrss.offline.OfflineUploadService;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.Label;
import org.fox.ttrss.util.AppRater;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class MainActivity extends CommonActivity implements OnlineServices {
	private final String TAG = this.getClass().getSimpleName();
	
	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private String m_sessionId;
	private Article m_selectedArticle;
	private Feed m_activeFeed;
	private FeedCategory m_activeCategory;
	private Timer m_refreshTimer;
	private RefreshTask m_refreshTask;
	private Menu m_menu;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_enableCats = false;
	private int m_apiLevel = 0;
	private boolean m_isLoggingIn = false;
	private boolean m_isOffline = false;
	private int m_offlineModeStatus = 0;
	private int m_selectedProduct = -1;
	private long m_lastRefresh = 0;

	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;
	private NavigationListener m_navigationListener;
	private NavigationAdapter m_navigationAdapter;
	private ArrayList<NavigationEntry> m_navigationEntries = new ArrayList<NavigationEntry>();
	
	private class NavigationListener implements ActionBar.OnNavigationListener {
		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			Log.d(TAG, "onNavigationItemSelected: " + itemPosition);

			NavigationEntry entry = m_navigationAdapter.getItem(itemPosition);
			entry._onItemSelected(itemPosition, m_navigationAdapter.getCount()-1);
			
			return false;
		}
	}
	
	private class ArticleNavigationEntry extends NavigationEntry {
		public ArticleNavigationEntry(Article article) {
			super(article.title);
		}		

		@Override	
		public void onItemSelected() {

		}
	}
	
	private class RootNavigationEntry extends NavigationEntry {
		public RootNavigationEntry(String title) {
			super(title);
		}

		@Override	
		public void onItemSelected() {
			
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			m_activeFeed = null;
			m_selectedArticle = null;
			m_activeCategory = null;

			if (isSmallScreen()) {
				
				if (m_enableCats) {
					ft.replace(R.id.fragment_container, new FeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.fragment_container, new FeedsFragment(), FRAG_FEEDS);
				}
				
				Fragment hf = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				if (hf != null) ft.remove(hf);
				
				Fragment af = getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				if (af != null) ft.remove(af);

			} else {
				if (m_enableCats) {
					ft.replace(R.id.feeds_fragment, new FeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.feeds_fragment, new FeedsFragment(), FRAG_FEEDS);
				}
				
				findViewById(R.id.article_fragment).setVisibility(View.GONE);
				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				
				ft.replace(R.id.headlines_fragment, new DummyFragment(), "");
			}
			
			ft.commit();
			initMainMenu();
		}
	}

	private class CategoryNavigationEntry extends NavigationEntry {
		FeedCategory m_category = null;
		
		public CategoryNavigationEntry(FeedCategory category) {
			super(category.title);

			m_category = category;
		}

		@Override	
		public void onItemSelected() {
			m_selectedArticle = null;

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (isSmallScreen()) {

				Fragment hf = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				if (hf != null) ft.remove(hf);
				
				Fragment af = getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				if (af != null) ft.remove(af);
				
				if (m_activeFeed.is_cat) {
					FeedCategoriesFragment cats = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
					ft.show(cats);
					
					cats.setSelectedCategory(null);
				} else {
					FeedsFragment feeds = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
		
					ft.show(feeds);

					feeds.setSelectedFeed(null);					
				}
				
			} else {
				findViewById(R.id.article_fragment).setVisibility(View.GONE);
				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				
				updateHeadlines();
				
				//ft.replace(R.id.headlines_fragment, new DummyFragment(), "");
			}
			ft.commit();

			m_activeFeed = null;
			refresh();
			initMainMenu();
		}
	}

	private class FeedNavigationEntry extends NavigationEntry {
		Feed m_feed = null;
		
		public FeedNavigationEntry(Feed feed) {
			super(feed.title);

			m_feed = feed;
		}

		@Override	
		public void onItemSelected() {

			m_selectedArticle = null;
			
			if (!isSmallScreen())
				findViewById(R.id.article_fragment).setVisibility(View.GONE);							

			viewFeed(m_feed, false);
		}
	}

	private abstract class NavigationEntry {
		private String title = null;
		private int timesCalled = 0;
		
		public void _onItemSelected(int position, int size) {
			Log.d(TAG, "_onItemSelected; TC=" + timesCalled + " P/S=" + position + "/" + size);
			
			if (position == size && timesCalled == 0) {
				++timesCalled;			
			} else {
				onItemSelected();
			}			
		}
		
		public NavigationEntry(String title) {
			this.title = title;
		}
		
		public String toString() {
			return title;
		}		
		
		public abstract void onItemSelected();
	}
	
	private class NavigationAdapter extends ArrayAdapter<NavigationEntry> {
		public NavigationAdapter(Context context, int textViewResourceId, ArrayList<NavigationEntry> items) {
			super(context, textViewResourceId, items);
		}
	}
	
	private class HeadlinesActionModeCallback implements ActionMode.Callback {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			deselectAllArticles();
			m_headlinesActionMode = null;
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			
			 MenuInflater inflater = getMenuInflater();
	            inflater.inflate(R.menu.headlines_action_menu, menu);
			
			return true;
		}
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			onOptionsItemSelected(item);
			return false;
		}
	};
	
	private BroadcastReceiver m_broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context content, Intent intent) {

			if (intent.getAction().equals(OfflineDownloadService.INTENT_ACTION_SUCCESS)) {
			
				m_offlineModeStatus = 2;
				
				switchOffline();
				
			} else if (intent.getAction().equals(OfflineUploadService.INTENT_ACTION_SUCCESS)) {
				//Log.d(TAG, "offline upload service reports success");

				refresh();

				Toast toast = Toast.makeText(MainActivity.this, R.string.offline_sync_success, Toast.LENGTH_SHORT);
				toast.show();
			}

		}
	};

	public void updateHeadlines() {
		HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);
		if (frag != null) {
			frag.setActiveArticle(m_selectedArticle);
		}
	}

	@Override
	public int getApiLevel() {
		return m_apiLevel;
	}

	private boolean hasPendingOfflineData() {
		try {
			Cursor c = getReadableDb().query("articles",
					new String[] { "COUNT(*)" }, "modified = 1", null, null, null,
					null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();
	
				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed? ugh
		}

		return false;
	}

	private boolean hasOfflineData() {
		try {
			Cursor c = getReadableDb().query("articles",
					new String[] { "COUNT(*)" }, null, null, null, null, null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();
	
				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed?
		}

		return false;
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleUnread(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.unread ? "1" : "0");
				put("field", "2");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleMarked(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.marked ? "1" : "0");
				put("field", "0");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticlePublished(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.published ? "1" : "0");
				put("field", "1");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleNote(final Article article, final String note) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", "1");
				put("data", note);
				put("field", "3");
			}
		};

		req.execute(map);
	}

	public static String articlesToIdString(ArticleList articles) {
		String tmp = "";

		for (Article a : articles)
			tmp += String.valueOf(a.id) + ",";

		return tmp.replaceAll(",$", "");
	}

	@SuppressWarnings("unchecked")
	public void catchupFeed(final Feed feed) {
		Log.d(TAG, "catchupFeed=" + feed);

		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				refresh();
			}

		};

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "catchupFeed");
				put("feed_id", String.valueOf(feed.id));
				if (feed.is_cat)
					put("is_cat", "1");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	private void toggleArticlesMarked(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "0");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	private void toggleArticlesUnread(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "2");
			}
		};

		req.execute(map);
		refresh();
	}

	@SuppressWarnings("unchecked")
	private void toggleArticlesPublished(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "1");
			}
		};

		req.execute(map);
	}

	private class RefreshTask extends TimerTask {

		@Override
		public void run() {
			ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
					.getSystemService(Context.CONNECTIVITY_SERVICE);

			if (cm.getBackgroundDataSetting()) {
				NetworkInfo networkInfo = cm.getActiveNetworkInfo();
				if (networkInfo != null && networkInfo.isAvailable()
						&& networkInfo.isConnected()) {

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							refresh();
						}
					});
					
				}
			}
		}
	}

	private synchronized void refresh() {
		Date date = new Date();
		
		if (m_sessionId != null && date.getTime() - m_lastRefresh > 5000) {
			 
			FeedsFragment ff = (FeedsFragment) getSupportFragmentManager()
					.findFragmentByTag(FRAG_FEEDS);

			if (ff != null) {
				Log.d(TAG, "Refreshing feeds/" + m_activeFeed);
				ff.refresh(true);
			}
			
			FeedCategoriesFragment cf = (FeedCategoriesFragment) getSupportFragmentManager()
					.findFragmentByTag(FRAG_CATS);

			if (cf != null) {
				Log.d(TAG, "Refreshing categories/" + m_activeCategory);
				cf.refresh(true);				
			}

			m_lastRefresh = date.getTime();
		}
	}

	/* private synchronized void refreshHeadlines() {
		if (m_sessionId != null) {
			HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
					.findFragmentByTag(FRAG_HEADLINES);

			Log.d(TAG, "Refreshing headlines...");

			if (frag != null) {
				frag.refresh(true);
			}
		}
	} */

	private void setUnreadOnly(boolean unread) {
		m_unreadOnly = unread;
		m_lastRefresh = 0;
		refresh();
	}

	@Override
	public boolean getUnreadOnly() {
		return m_unreadOnly;
	}

	@Override
	public boolean getUnreadArticlesOnly() {
		return m_unreadArticlesOnly;
	}

	@Override
	public String getSessionId() {
		return m_sessionId;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);
		
		if (OfflineDownloadService.INTENT_ACTION_CANCEL.equals(getIntent().getAction())) {
			cancelOfflineSync();
		}
		
		//Log.d(TAG, "started with intent action=" + intentAction);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);  
		
		m_themeName = m_prefs.getString("theme", "THEME_DARK");

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_activeFeed = savedInstanceState.getParcelable("activeFeed");
			m_selectedArticle = savedInstanceState
					.getParcelable("selectedArticle");
			m_unreadArticlesOnly = savedInstanceState
					.getBoolean("unreadArticlesOnly");
			m_activeCategory = savedInstanceState
					.getParcelable("activeCategory");
			m_apiLevel = savedInstanceState.getInt("apiLevel");
			m_offlineModeStatus = savedInstanceState.getInt("offlineModeStatus");
		}

		m_enableCats = m_prefs.getBoolean("enable_cats", false);

		setContentView(R.layout.main);

		IntentFilter filter = new IntentFilter();
		filter.addAction(OfflineDownloadService.INTENT_ACTION_SUCCESS);
		filter.addAction(OfflineUploadService.INTENT_ACTION_SUCCESS);
		filter.addCategory(Intent.CATEGORY_DEFAULT);

		registerReceiver(m_broadcastReceiver, filter);

		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		
		m_isOffline = localPrefs.getBoolean("offline_mode_active", false);

		Log.d(TAG, "m_isOffline=" + m_isOffline);

		if (!isCompatMode()) {
			
			if (!isSmallScreen()) {				
				findViewById(R.id.feeds_fragment).setVisibility(m_selectedArticle != null && getOrientation() % 2 != 0 ? View.GONE : View.VISIBLE);
				findViewById(R.id.article_fragment).setVisibility(m_selectedArticle != null ? View.VISIBLE : View.GONE);
			}
			
			LayoutTransition transitioner = new LayoutTransition();
			((ViewGroup) findViewById(R.id.fragment_container)).setLayoutTransition(transitioner);
			
			m_navigationAdapter = new NavigationAdapter(this, android.R.layout.simple_spinner_dropdown_item, m_navigationEntries);
			
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
			m_navigationListener = new NavigationListener();
			
			getActionBar().setListNavigationCallbacks(m_navigationAdapter, m_navigationListener);
		}
		
		if (isSmallScreen()) {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			// temporary workaround against viewpager going a bit crazy when restoring after rotation
			if (m_selectedArticle != null) {
				ft.remove(getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE));
				m_selectedArticle = null;
			}
			
			if (m_activeFeed != null) {
				if (m_activeFeed.is_cat) {
					ft.hide(getSupportFragmentManager().findFragmentByTag(FRAG_CATS));
				} else {
					ft.hide(getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS));
				}
			}
			ft.commit();
		}

		if (m_isOffline) {
			Intent offline = new Intent(MainActivity.this,
					OfflineActivity.class);
			startActivity(offline);
			finish();
		} else {
			//AppRater.showRateDialog(this, null);
			AppRater.appLaunched(this);
			
			if (m_sessionId != null) {
				loginSuccess();
			} else {
				login();
			}
		}
	}

	private void switchOffline() {
		if (m_offlineModeStatus == 2) {
			
			AlertDialog.Builder builder = new AlertDialog.Builder(
					MainActivity.this)
					.setMessage(R.string.dialog_offline_success)
					.setPositiveButton(R.string.dialog_offline_go,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									
									m_offlineModeStatus = 0;
									
									SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
									SharedPreferences.Editor editor = localPrefs.edit();
									editor.putBoolean("offline_mode_active", true);
									editor.commit();
									
									Intent refresh = new Intent(
											MainActivity.this,
											OfflineActivity.class);
									startActivity(refresh);
									finish();
								}
							})
					.setNegativeButton(R.string.dialog_cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {

									m_offlineModeStatus = 0;

								}
							});

			AlertDialog dlg = builder.create();
			dlg.show();
			
		} else if (m_offlineModeStatus == 0) {
		
			AlertDialog.Builder builder = new AlertDialog.Builder(this)
					.setMessage(R.string.dialog_offline_switch_prompt)
					.setPositiveButton(R.string.dialog_offline_go,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
	
									if (m_sessionId != null) {
										Log.d(TAG, "offline: starting");
										
										m_offlineModeStatus = 1;
	
										Intent intent = new Intent(
												MainActivity.this,
												OfflineDownloadService.class);
										intent.putExtra("sessionId", m_sessionId);
	
										startService(intent);
									}
								}
							})
					.setNegativeButton(R.string.dialog_cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									//
								}
							});
	
			AlertDialog dlg = builder.create();
			dlg.show();
		} else if (m_offlineModeStatus == 1) {
			cancelOfflineSync();
		}
	}

	private void cancelOfflineSync() {		
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
		.setMessage(R.string.dialog_offline_sync_in_progress)
		.setNegativeButton(R.string.dialog_offline_sync_stop,
				new Dialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int which) {

						if (m_sessionId != null) {
							Log.d(TAG, "offline: stopping");
							
							m_offlineModeStatus = 0;

							Intent intent = new Intent(
									MainActivity.this,
									OfflineDownloadService.class);

							stopService(intent);
							
							dialog.dismiss();

							restart();
						}
					}
				})
		.setPositiveButton(R.string.dialog_offline_sync_continue,
				new Dialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int which) {
					
						dialog.dismiss();

						restart();
					}
				});

		AlertDialog dlg = builder.create();
		dlg.show();
	}
	
	private void switchOfflineSuccess() {
		logout();
		// setLoadingStatus(R.string.blank, false);

		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("offline_mode_active", true);
		editor.commit();

		Intent offline = new Intent(MainActivity.this, OfflineActivity.class);
		startActivity(offline);
		finish();

	}

	private void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(status);
		}
		
		setProgressBarIndeterminateVisibility(showProgress);
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putString("sessionId", m_sessionId);
		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putParcelable("activeFeed", m_activeFeed);
		out.putParcelable("selectedArticle", m_selectedArticle);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putParcelable("activeCategory", m_activeCategory);
		out.putInt("apiLevel", m_apiLevel);
		out.putInt("offlineModeStatus", m_offlineModeStatus);
	}

	@Override
	public void onResume() {
		super.onResume();

		boolean needRefresh = !m_prefs.getString("theme", "THEME_DARK").equals(
				m_themeName)
				|| m_prefs.getBoolean("enable_cats", false) != m_enableCats;

		if (needRefresh) {
			restart();
		} else if (m_sessionId != null) {
			m_refreshTask = new RefreshTask();
			m_refreshTimer = new Timer("Refresh");

			m_refreshTimer.schedule(m_refreshTask, 60 * 1000L, 120 * 1000L);
		} else {
			if (!m_isLoggingIn) {
				login();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		m_menu = menu;

		initMainMenu();

		MenuItem item = menu.findItem(R.id.show_feeds);

		if (getUnreadOnly()) {
			item.setTitle(R.string.menu_all_feeds);
		} else {
			item.setTitle(R.string.menu_unread_feeds);
		}

		/*
		 * item = menu.findItem(R.id.show_all_articles);
		 * 
		 * if (getUnreadArticlesOnly()) {
		 * item.setTitle(R.string.show_all_articles); } else {
		 * item.setTitle(R.string.show_unread_articles); }
		 */

		return true;
	}

	private void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);

		if (mi != null) {
			mi.setTitle(labelId);
		}
	}

	@Override
	public void onBackPressed() {
		goBack(true);
	}

	private void closeCategory() {
		m_activeCategory = null;
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		if (isSmallScreen()) {
			ft.replace(R.id.fragment_container, new FeedCategoriesFragment(), FRAG_CATS);
		} else {
			ft.replace(R.id.feeds_fragment, new FeedCategoriesFragment(), FRAG_CATS);
		}
		ft.commit();

		initMainMenu();
		refresh();
	}
	
	private void deselectAllArticles() {
		HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
									.findFragmentByTag(FRAG_HEADLINES);

		if (hf != null) {
			ArticleList selected = hf.getSelectedArticles();
			if (selected.size() > 0) {
				selected.clear();
				initMainMenu();
				updateHeadlines();
			}
		}
	}
	
	private void goBack(boolean allowQuit) {
		if (isSmallScreen()) {
			if (m_selectedArticle != null) {
				closeArticle();
			} else if (m_activeFeed != null) {
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				if (m_activeFeed.is_cat) {
						
					Fragment headlines = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
					FeedCategoriesFragment cats = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			
					ft.show(cats);
					ft.remove(headlines);
						
				} else {
					Fragment headlines = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
					FeedsFragment feeds = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			
					ft.show(feeds);
					ft.remove(headlines);
				}			
				ft.commit();
			
				m_activeFeed = null;

				refresh();					

				initMainMenu();

			} else if (m_activeCategory != null) {
				closeCategory();
			} else if (allowQuit) {
				finish();
			}
		} else {
			if (m_selectedArticle != null) {
				closeArticle();
				refresh();
			/* } else if (m_activeFeed != null) {
				closeFeed(); */	
			} else if (m_activeCategory != null) {
				closeCategory();
			} else if (allowQuit) {
				finish();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		switch (item.getItemId()) {
		case R.id.close_feed:
			if (m_activeFeed != null) {
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.replace(R.id.headlines_fragment, new DummyFragment(), "");
				ft.commit();
				
				if (m_activeFeed.is_cat) {
					FeedCategoriesFragment cats = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
					cats.setSelectedCategory(null);
				} else {
					FeedsFragment feeds = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
					feeds.setSelectedFeed(null);					
				}
	
				m_activeFeed = null;
	
				initMainMenu();
			}
			return true;
		case R.id.close_article:
			closeArticle();
			return true;
		case R.id.donate:
			if (true) {
				CharSequence[] items = { "Silver Donation ($2)", "Gold Donation ($5)", "Platinum Donation ($10)" };
			
				Dialog dialog = new Dialog(MainActivity.this);
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
						.setTitle(R.string.donate_select)
						.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								m_selectedProduct = which;
							}
						}).setNegativeButton(R.string.dialog_close, new OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}					
						}).setPositiveButton(R.string.donate_do, new OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (m_selectedProduct != -1 && m_selectedProduct < 3) {
									CharSequence[] products = { "donation_silver", "donation_gold", "donation_platinum2" };
									
									Log.d(TAG, "Selected product: " + products[m_selectedProduct]);

									BillingHelper.requestPurchase(MainActivity.this, (String) products[m_selectedProduct]);
									
									dialog.dismiss();									
								}
							}
						});
	
				dialog = builder.create();
				dialog.show();
			}
			return true;
		case android.R.id.home:
			goBack(false);
			return true;
		case R.id.search:
			if (hf != null && isCompatMode()) {
				Dialog dialog = new Dialog(this);

				final EditText edit = new EditText(this);

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.search)
						.setPositiveButton(getString(R.string.search),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										
										String query = edit.getText().toString().trim();
										
										hf.setSearchQuery(query);

									}
								})
						.setNegativeButton(getString(R.string.cancel),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										
										//

									}
								}).setView(edit);
				
				dialog = builder.create();
				dialog.show();
			}
			
			return true;
		case R.id.preferences:
			Intent intent = new Intent(MainActivity.this,
					PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.update_feeds:
			m_lastRefresh = 0;
			refresh();
			return true;
		case R.id.logout:
			logout();
			return true;
		case R.id.login:
			login();
			return true;
		case R.id.go_offline:
			switchOffline();
			return true;
		case R.id.article_set_note:
			if (m_selectedArticle != null) {
				editArticleNote(m_selectedArticle);				
			}
			return true;
		case R.id.headlines_select:
			if (hf != null) {
				Dialog dialog = new Dialog(this);
				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.headlines_select_dialog)
						.setSingleChoiceItems(
								new String[] {
										getString(R.string.headlines_select_all),
										getString(R.string.headlines_select_unread),
										getString(R.string.headlines_select_none) },
								0, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										switch (which) {
										case 0:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.ALL);
											break;
										case 1:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.UNREAD);
											break;
										case 2:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.NONE);
											break;
										}
										dialog.cancel();
										initMainMenu();
									}
								});

				dialog = builder.create();
				dialog.show();
			}
			return true;
		case R.id.headlines_mark_as_read:
			if (hf != null) {
				ArticleList articles = hf.getUnreadArticles();

				for (Article a : articles)
					a.unread = false;

				updateHeadlines();

				ApiRequest req = new ApiRequest(getApplicationContext());

				final String articleIds = articlesToIdString(articles);

				@SuppressWarnings("serial")
				HashMap<String, String> map = new HashMap<String, String>() {
					{
						put("sid", m_sessionId);
						put("op", "updateArticle");
						put("article_ids", articleIds);
						put("mode", "0");
						put("field", "2");
					}
				};

				req.execute(map);
				refresh();
			}
			return true;
		case R.id.share_article:
			if (android.os.Build.VERSION.SDK_INT < 14) {
				shareArticle(m_selectedArticle);
			}
			return true;
		case R.id.toggle_marked:
			if (m_selectedArticle != null) {
				m_selectedArticle.marked = !m_selectedArticle.marked;
				saveArticleMarked(m_selectedArticle);				
				updateHeadlines();
			}
			return true;
		case R.id.selection_select_none:
			deselectAllArticles();
			return true;
		case R.id.selection_toggle_unread:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.unread = !a.unread;

					toggleArticlesUnread(selected);
					updateHeadlines();
				}
				refresh();
			}
			return true;
		case R.id.selection_toggle_marked:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.marked = !a.marked;

					toggleArticlesMarked(selected);
					updateHeadlines();
				}
			}
			return true;
		case R.id.selection_toggle_published:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.published = !a.published;

					toggleArticlesPublished(selected);
					updateHeadlines();
				}
			}
			return true;
		case R.id.toggle_published:
			if (m_selectedArticle != null) {
				m_selectedArticle.published = !m_selectedArticle.published;
				saveArticlePublished(m_selectedArticle);
				updateHeadlines();
			}
			return true;
		case R.id.catchup_above:
			if (hf != null) {
				if (m_selectedArticle != null) {
					ArticleList articles = hf.getAllArticles();
					ArticleList tmp = new ArticleList();
					for (Article a : articles) {
						a.unread = false;
						tmp.add(a);
						if (m_selectedArticle.id == a.id)
							break;
					}
					if (tmp.size() > 0) {
						toggleArticlesUnread(tmp);
						updateHeadlines();
					}
				}
			}
			return true;
		case R.id.set_unread:
			if (m_selectedArticle != null) {
				m_selectedArticle.unread = true;
				saveArticleUnread(m_selectedArticle);
				updateHeadlines();
			}
			return true;
		case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());

			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_all_feeds);
			} else {
				item.setTitle(R.string.menu_unread_feeds);
			}

			return true;
		case R.id.set_labels:
			if (m_selectedArticle != null) {
				editArticleLabels(m_selectedArticle);				
			}
			return true;
		default:
			Log.d(TAG,
					"onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	private void editArticleNote(final Article article) {
		String note = "";
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);  
		builder.setTitle(article.title);
		final EditText topicEdit = new EditText(this);
		topicEdit.setText(note);
		builder.setView(topicEdit);
		
		builder.setPositiveButton(R.string.article_set_note, new Dialog.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	saveArticleNote(article, topicEdit.getText().toString().trim());
	        	article.published = true;	        	
	        	saveArticlePublished(article);
	        	updateHeadlines();	        	
	        }
	    });
		
		builder.setNegativeButton(R.string.dialog_cancel, new Dialog.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	//
	        }
	    });
		
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void editArticleLabels(Article article) {
		final int articleId = article.id;									

		ApiRequest req = new ApiRequest(getApplicationContext()) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null) {
					Type listType = new TypeToken<List<Label>>() {}.getType();
					final List<Label> labels = new Gson().fromJson(result, listType);

					CharSequence[] items = new CharSequence[labels.size()];
					final int[] itemIds = new int[labels.size()];
					boolean[] checkedItems = new boolean[labels.size()];
					
					for (int i = 0; i < labels.size(); i++) {
						items[i] = labels.get(i).caption;
						itemIds[i] = labels.get(i).id;
						checkedItems[i] = labels.get(i).checked;
					}
					
					Dialog dialog = new Dialog(MainActivity.this);
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
							.setTitle(R.string.article_set_labels)
							.setMultiChoiceItems(items, checkedItems, new OnMultiChoiceClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which, final boolean isChecked) {
									final int labelId = itemIds[which];
									
									@SuppressWarnings("serial")
									HashMap<String, String> map = new HashMap<String, String>() {
										{
											put("sid", m_sessionId);
											put("op", "setArticleLabel");
											put("label_id", String.valueOf(labelId));
											put("article_ids", String.valueOf(articleId));
											if (isChecked) put("assign", "true");
										}
									};
									
									ApiRequest req = new ApiRequest(m_context);
									req.execute(map);
									
								}
							}).setPositiveButton(R.string.dialog_close, new OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.cancel();
								}
							});

					dialog = builder.create();
					dialog.show();

				}
			}
		};

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "getLabels");
				put("article_id", String.valueOf(articleId));
			}
		};
		
		req.execute(map);
	}
	
	private Intent getShareIntent(Article article) {
		Intent intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		//intent.putExtra(Intent.EXTRA_SUBJECT, article.title);
		intent.putExtra(Intent.EXTRA_TEXT, article.title + " " + article.link);

		return intent;
	}
	
	private void shareArticle(Article article) {
		if (article != null) {

			Intent intent = getShareIntent(article);
			
			startActivity(Intent.createChooser(intent,
					getString(R.string.share_article)));
		}
	}

	private void closeArticle() {
		m_selectedArticle = null;
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		if (isSmallScreen()) {
			ft.remove(getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE));
			ft.show(getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES));
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);	
			findViewById(R.id.article_fragment).setVisibility(View.GONE);
			ft.replace(R.id.article_fragment, new DummyFragment(), FRAG_ARTICLE);

			updateHeadlines();
		}
		ft.commit();
		
		initMainMenu();
	}
	
	private void updateTitle() {
		if (!isCompatMode()) {
			
			m_navigationAdapter.clear();

			if (m_activeCategory != null || (m_activeFeed != null && (isSmallScreen() || getOrientation() % 2 != 0))) {
				getActionBar().setDisplayShowTitleEnabled(false);
				getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
				
				m_navigationAdapter.add(new RootNavigationEntry(getString(R.string.app_name)));
				
				if (m_activeCategory != null)
					m_navigationAdapter.add(new CategoryNavigationEntry(m_activeCategory));

				if (m_activeFeed != null)
					m_navigationAdapter.add(new FeedNavigationEntry(m_activeFeed));

				//if (m_selectedArticle != null)
				//	m_navigationAdapter.add(new ArticleNavigationEntry(m_selectedArticle));

				getActionBar().setSelectedNavigationItem(getActionBar().getNavigationItemCount());
			
			} else {
				getActionBar().setDisplayShowTitleEnabled(true);
				getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
				getActionBar().setTitle(R.string.app_name);
			}

			if (isSmallScreen()) {
				getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticle != null || m_activeCategory != null || m_activeFeed != null);
			} else {
				getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticle != null || m_activeCategory != null);
			}
			
		} else {
			if (m_activeFeed != null) {
				setTitle(m_activeFeed.title);
			} else if (m_activeCategory != null) {
				setTitle(m_activeCategory.title);
			} else {
				setTitle(R.string.app_name);
			}
		}
	}

	@SuppressLint({ "NewApi", "NewApi", "NewApi" })
	public void initMainMenu() {
		if (m_menu != null) {

			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);

			if (m_sessionId != null) {

				m_menu.setGroupVisible(R.id.menu_group_logged_in, true);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
				
				HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_HEADLINES);

				int numSelected = 0;

				if (hf != null)
					numSelected = hf.getSelectedArticles().size();

				if (numSelected != 0) {
					if (isCompatMode()) {
						m_menu.setGroupVisible(R.id.menu_group_headlines_selection, true);
					} else {
						if (m_headlinesActionMode == null)
							m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
					}
					
				} else if (m_selectedArticle != null) {
					m_menu.setGroupVisible(R.id.menu_group_article, true);
					m_menu.findItem(R.id.close_article).setVisible(!isSmallScreen());
					
					if (android.os.Build.VERSION.SDK_INT >= 14) {			
						ShareActionProvider shareProvider = (ShareActionProvider) m_menu.findItem(R.id.share_article).getActionProvider();
						
						if (m_selectedArticle != null) {
							Log.d(TAG, "setting up share provider");
							shareProvider.setShareIntent(getShareIntent(m_selectedArticle));
							
							if (!m_prefs.getBoolean("tablet_article_swipe", false) && !isSmallScreen()) {
								m_menu.findItem(R.id.share_article).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
							}
						}
					}

				} else if (m_activeFeed != null) {
					m_menu.setGroupVisible(R.id.menu_group_headlines, true);
					m_menu.findItem(R.id.close_feed).setVisible(!isSmallScreen());
					
					MenuItem search = m_menu.findItem(R.id.search);
					
					search.setEnabled(m_apiLevel >= 2);
					
					if (!isCompatMode()) {
						SearchView searchView = (SearchView) search.getActionView();
						searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
							private String query = "";
							
							@Override
							public boolean onQueryTextSubmit(String query) {
								HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
										.findFragmentByTag(FRAG_HEADLINES);
								
								if (frag != null) {
									frag.setSearchQuery(query);
									this.query = query;
								}
								
								return false;
							}
							
							@Override
							public boolean onQueryTextChange(String newText) {
								if (newText.equals("") && !newText.equals(this.query)) {
									HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
											.findFragmentByTag(FRAG_HEADLINES);
									
									if (frag != null) {
										frag.setSearchQuery(newText);
										this.query = newText;
									}
								}
								
								return false;
							}
						});
					}
					
				} else {
					m_menu.setGroupVisible(R.id.menu_group_feeds, true);
				}

				if (numSelected == 0 && m_headlinesActionMode != null) {
					m_headlinesActionMode.finish();
				}

				//Log.d(TAG, "isCompatMode=" + isCompatMode());
			
				
				m_menu.findItem(R.id.set_labels).setEnabled(m_apiLevel >= 1);
				m_menu.findItem(R.id.article_set_note).setEnabled(m_apiLevel >= 1);

				m_menu.findItem(R.id.donate).setVisible(BillingHelper.isBillingSupported());
								
			} else {
				m_menu.setGroupVisible(R.id.menu_group_logged_in, false);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, true);
			}
		}
		
		updateTitle();
	}

	@Override
	public void onPause() {
		super.onPause();

		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}

		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		unregisterReceiver(m_broadcastReceiver);
	}

	private void syncOfflineData() {
		Log.d(TAG, "offlineSync: starting");
		
		Intent intent = new Intent(
				MainActivity.this,
				OfflineUploadService.class);
		
		intent.putExtra("sessionId", m_sessionId);

		startService(intent);
	}

	private void loginSuccess() {
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		setProgressBarIndeterminateVisibility(false);
		
		m_isOffline = false;

		startService(new Intent(MainActivity.this, BillingService.class));
		
		initMainMenu();
		
		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}

		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}

		m_refreshTask = new RefreshTask();
		m_refreshTimer = new Timer("Refresh");

		m_refreshTimer.schedule(m_refreshTask, 60 * 1000L, 120 * 1000L);
	}


	private class LoginRequest extends ApiRequest {
		public LoginRequest(Context context) {
			super(context);
		}

		@SuppressWarnings("unchecked")
		protected void onPostExecute(JsonElement result) {
			m_isLoggingIn = false;
			
			if (result != null) {
				try {
					JsonObject content = result.getAsJsonObject();
					if (content != null) {
						m_sessionId = content.get("session_id").getAsString();

						Log.d(TAG, "Authenticated!");

						ApiRequest req = new ApiRequest(m_context) {
							protected void onPostExecute(JsonElement result) {
								m_apiLevel = 0;

								if (result != null) {
									try {
										m_apiLevel = result.getAsJsonObject()
													.get("level").getAsInt();
									} catch (Exception e) {
										e.printStackTrace();
									}
								}

								Log.d(TAG, "Received API level: " + m_apiLevel);

								if (hasPendingOfflineData())
									syncOfflineData();

								FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

								if (m_enableCats) {
									FeedCategoriesFragment frag = new FeedCategoriesFragment();
									if (isSmallScreen()) {
										ft.replace(R.id.fragment_container, frag, FRAG_CATS);
									} else {
										ft.replace(R.id.feeds_fragment, frag, FRAG_CATS);
									}

								} else {
									FeedsFragment frag = new FeedsFragment();
									if (isSmallScreen()) {
										ft.replace(R.id.fragment_container, frag, FRAG_FEEDS);
									} else {
										ft.replace(R.id.feeds_fragment, frag, FRAG_FEEDS);
									}
								}

								try {
									ft.commit();
								} catch (IllegalStateException e) {
									e.printStackTrace();
								}
								
								loginSuccess();

							}
						};

						@SuppressWarnings("serial")
						HashMap<String, String> map = new HashMap<String, String>() {
							{
								put("sid", m_sessionId);
								put("op", "getApiLevel");
							}
						};

						req.execute(map);

						setLoadingStatus(R.string.loading_message, true);

						return;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			m_sessionId = null;

			setLoadingStatus(getErrorMessage(), false);

			if (hasOfflineData()) {

				AlertDialog.Builder builder = new AlertDialog.Builder(
						MainActivity.this)
						.setMessage(R.string.dialog_offline_prompt)
						.setPositiveButton(R.string.dialog_offline_go,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										switchOfflineSuccess();
									}
								})
						.setNegativeButton(R.string.dialog_cancel,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										//
									}
								});

				AlertDialog dlg = builder.create();
				dlg.show();
			}

			// m_menu.findItem(R.id.login).setVisible(true);
		}

	}

	@Override
	public void onFeedSelected(Feed feed) {
		viewFeed(feed, false);
	}

	public void viewFeed(Feed feed, boolean append) {
		Log.d(TAG, "viewFeeed/" + feed.id);
		
		m_activeFeed = feed;

		if (!append) {
			//m_selectedArticle = null;
			
			if (m_menu != null) {
				MenuItem search = m_menu.findItem(R.id.search);
			
				if (search != null && !isCompatMode()) {
					SearchView sv = (SearchView) search.getActionView();
					sv.setQuery("", false);				
				}
			}
			
			HeadlinesFragment hf = new HeadlinesFragment(feed);

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			
			if (isSmallScreen()) {
				Fragment cats = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
				if (cats != null) ft.hide(cats);

				Fragment feeds = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
				if (feeds != null) ft.hide(feeds);

				ft.add(R.id.fragment_container, hf, FRAG_HEADLINES);
			} else {
				//findViewById(R.id.article_fragment).setVisibility(View.GONE);
				findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
				ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
			}
			ft.commit();
			
		} else {
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
					.findFragmentByTag(FRAG_HEADLINES);
			if (hf != null) {
				hf.refresh(true);
			}
		}
		
		initMainMenu();
	}

	public void viewCategory(FeedCategory cat, boolean openAsFeed) {

		Log.d(TAG, "viewCategory");

		if (!openAsFeed) {
			m_activeCategory = cat;

			FeedsFragment frag = new FeedsFragment(cat);

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			
			if (isSmallScreen()) {			
				ft.replace(R.id.fragment_container, frag, FRAG_FEEDS);
			} else {				
				ft.replace(R.id.feeds_fragment, frag, FRAG_FEEDS);
			}
			ft.commit();
			
		} else {
			Feed feed = new Feed(cat.id, cat.title, true);

			if (m_menu != null) {
				MenuItem search = m_menu.findItem(R.id.search);
			
				if (search != null && !isCompatMode()) {
					SearchView sv = (SearchView) search.getActionView();
					sv.setQuery("", false);				
				}
			}
			viewFeed(feed, false);
		}

		initMainMenu();
	}
	
	@Override
	public void onArticleSelected(Article article) {
		openArticle(article);		
	}

	public void openArticle(Article article) {
		m_selectedArticle = article;

		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}

		initMainMenu();

		Fragment frag;
		
		if (isSmallScreen() || m_prefs.getBoolean("tablet_article_swipe", false)) {
			frag = new ArticlePager(article);
		} else {
			frag = new ArticleFragment(article);
		}

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		if (isSmallScreen()) {
			ft.hide(getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES));
			ft.add(R.id.fragment_container, frag, FRAG_ARTICLE);
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(getOrientation() % 2 != 0 ? View.GONE : View.VISIBLE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
			ft.replace(R.id.article_fragment, frag, FRAG_ARTICLE);
			
			if (getOrientation() % 2 == 0) refresh();
		}
		ft.commit();
	}

	/* private Feed getActiveFeed() {
		return m_activeFeed;
	}

	private FeedCategory getActiveCategory() {
		return m_activeCategory;
	} */

	private void logout() {
		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}

		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}

		m_sessionId = null;

		findViewById(R.id.loading_container).setVisibility(View.VISIBLE);

		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(R.string.login_ready);
		}
		
		initMainMenu();
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void login() {

		logout();

		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

			setLoadingStatus(R.string.login_need_configure, false);

			// launch preferences
			Intent intent = new Intent(MainActivity.this,
					PreferencesActivity.class);
			startActivityForResult(intent, 0);

		} else {

			LoginRequest ar = new LoginRequest(getApplicationContext());

			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", "").trim());
					put("password", m_prefs.getString("password", "").trim());
				}
			};

			ar.execute(map);

			setLoadingStatus(R.string.login_in_progress, true);
			
			m_isLoggingIn = true;
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);
		FeedsFragment ff = (FeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);
		FeedCategoriesFragment cf = (FeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		switch (item.getItemId()) {
		case R.id.article_link_copy:
			if (true) {
				Article article = null;
			
				if (m_selectedArticle != null) {
					article = m_selectedArticle;
				} else if (info != null) {
					article = hf.getArticleAtPosition(info.position);
				}
				
				if (article != null) {
					copyToClipboard(article.link);
				}
			}
			return true;
		case R.id.article_link_share:
			if (m_selectedArticle != null) {
				shareArticle(m_selectedArticle);
			}
			return true;
		case R.id.set_labels:
			if (true) {
				Article article = null;
			
				if (m_selectedArticle != null) {
					article = m_selectedArticle;
				} else if (info != null) {
					article = hf.getArticleAtPosition(info.position);
				}
			
				if (article != null) {
					editArticleLabels(article);				
				}
			}
			return true;
		case R.id.article_set_note:
			if (true) {
				Article article = null;
			
				if (m_selectedArticle != null) {
					article = m_selectedArticle;
				} else if (info != null) {
					article = hf.getArticleAtPosition(info.position);
				}
			
				if (article != null) {
					editArticleNote(article);				
				}
			}
			return true;
		case R.id.browse_articles:
			if (cf != null) {
				FeedCategory cat = cf.getCategoryAtPosition(info.position);
				if (cat != null) {
					viewCategory(cat, true);
					cf.setSelectedCategory(cat);
				}
			}
			return true;
		case R.id.browse_feeds:
			if (cf != null) {
				FeedCategory cat = cf.getCategoryAtPosition(info.position);
				if (cat != null) {
					viewCategory(cat, false);
					cf.setSelectedCategory(cat);
				}
			}
			return true;
		case R.id.catchup_category:
			if (cf != null) {
				FeedCategory cat = cf.getCategoryAtPosition(info.position);
				if (cat != null) {
					catchupFeed(new Feed(cat.id, cat.title, true));
				}
			}
			return true;
		case R.id.catchup_feed:
			if (ff != null) {
				Feed feed = ff.getFeedAtPosition(info.position);
				if (feed != null) {
					catchupFeed(feed);
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.marked = !a.marked;

					toggleArticlesMarked(selected);
					updateHeadlines();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.marked = !article.marked;
						saveArticleMarked(article);
						updateHeadlines();
					}
				}
			}
			return true;
		case R.id.selection_toggle_published:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.published = !a.published;

					toggleArticlesPublished(selected);
					updateHeadlines();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.published = !article.published;
						saveArticlePublished(article);
						updateHeadlines();
					}
				}
			}
			return true;
		case R.id.selection_toggle_unread:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.unread = !a.unread;

					toggleArticlesUnread(selected);
					updateHeadlines();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.unread = !article.unread;
						saveArticleUnread(article);
						updateHeadlines();
					}
				}
			}
			return true;
		case R.id.share_article:
			if (hf != null) {
				Article article = hf.getArticleAtPosition(info.position);
				if (article != null)
					shareArticle(article);
			}
			return true;
		case R.id.catchup_above:
			if (hf != null) {
				Article article = hf.getArticleAtPosition(info.position);
				if (article != null) {
					ArticleList articles = hf.getAllArticles();
					ArticleList tmp = new ArticleList();
					for (Article a : articles) {
						a.unread = false;
						tmp.add(a);
						if (article.id == a.id)
							break;
					}
					if (tmp.size() > 0) {
						toggleArticlesUnread(tmp);
						updateHeadlines();
					}
				}
			}
			return true;
			/*
			 * case R.id.set_unread: if (hf != null) { Article article =
			 * hf.getArticleAtPosition(info.position); if (article != null) {
			 * article.unread = true; saveArticleUnread(article); } } break;
			 */
		default:
			Log.d(TAG,
					"onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}

	private Article getRelativeArticle(Article article, RelativeArticle ra) {
		HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);
		if (frag != null) {
			ArticleList articles = frag.getAllArticles();
			for (int i = 0; i < articles.size(); i++) {
				Article a = articles.get(i);

				if (a.id == article.id) {
					if (ra == RelativeArticle.AFTER) {
						try {
							return articles.get(i + 1);
						} catch (IndexOutOfBoundsException e) {
							return null;
						}
					} else {
						try {
							return articles.get(i - 1);
						} catch (IndexOutOfBoundsException e) {
							return null;
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (action == KeyEvent.ACTION_DOWN) {
				HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_HEADLINES);

				if (hf != null && m_activeFeed != null) {
					Article base = hf.getActiveArticle();

					Article next = base != null ? getRelativeArticle(base,
							RelativeArticle.AFTER) : hf.getArticleAtPosition(0);

					if (next != null) {
						hf.setActiveArticle(next);

						boolean combinedMode = m_prefs.getBoolean(
								"combined_mode", false);

						if (combinedMode || m_selectedArticle == null) {
							next.unread = false;
							saveArticleUnread(next);
						} else {
							openArticle(next);
						}
					}
				}
			}
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (action == KeyEvent.ACTION_UP) {
				HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_HEADLINES);

				if (hf != null && m_activeFeed != null) {
					Article base = hf.getActiveArticle();

					Article prev = base != null ? getRelativeArticle(base,
							RelativeArticle.BEFORE) : hf
							.getArticleAtPosition(0);

					if (prev != null) {
						hf.setActiveArticle(prev);

						boolean combinedMode = m_prefs.getBoolean(
								"combined_mode", false);

						if (combinedMode || m_selectedArticle == null) {
							prev.unread = false;
							saveArticleUnread(prev);
						} else {
							openArticle(prev);
						}
					}
				}

			}
			return true;
		default:
			return super.dispatchKeyEvent(event);
		}
	}

	@Override
	public void onCatSelected(FeedCategory cat) {
		Log.d(TAG, "onCatSelected");
		boolean browse = m_prefs.getBoolean("browse_cats_like_feeds", false);

		viewCategory(cat, browse && cat.id >= 0);
	}

	@Override
	public void setSelectedArticle(Article article) {
		m_selectedArticle = article;
		updateHeadlines();
		initMainMenu();
	}

	@Override
	public void restart() {
		Intent refresh = new Intent(MainActivity.this, MainActivity.class);
		refresh.putExtra("sessionId", m_sessionId);
		startActivity(refresh);
		finish();
	}

	@Override
	public void onArticleListSelectionChange(ArticleList selection) {
		initMainMenu();
	}
}