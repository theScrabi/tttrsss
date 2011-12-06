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
import android.database.Cursor;
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

public class MainActivity extends FragmentActivity implements OnlineServices {
	private final String TAG = this.getClass().getSimpleName();
	
	private final int OFFLINE_SYNC_SEQ = 60;
	private final int OFFLINE_SYNC_MAX = 500;

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private String m_sessionId;
	private Article m_selectedArticle;
	private Feed m_activeFeed;
	private FeedCategory m_activeCategory;
	private Timer m_refreshTimer;
	private RefreshTask m_refreshTask;
	private Menu m_menu;
	private boolean m_smallScreenMode;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_compatMode = false;
	private boolean m_enableCats = false;
	private int  m_isLicensed = -1;
	private int m_apiLevel = 0;
	private int m_articleOffset = 0;
	private boolean m_isOffline = false;
	
	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;
	
	public void updateHeadlines() {
		HeadlinesFragment frag = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		if (frag != null) {
			frag.notifyUpdated();
		}
	}
	
	@Override
	public boolean getLicensed() {
		return m_isLicensed == 1;
	}
	
	@Override
	public int getApiLevel() {
		return m_apiLevel;
	}
	
	private boolean hasPendingOfflineData() {
		Cursor c = getReadableDb().query("articles", 
				new String[] { "COUNT(*)" }, "modified = 1", null, null, null, null);
		if (c.moveToFirst()) {
			int modified = c.getInt(0);
			c.close();
			
			return modified > 0;
		}
		
		return false;
	}

	public void clearPendingOfflineData() {
		getWritableDb().execSQL("UPDATE articles SET modified = 0");
	}

	private boolean hasOfflineData() {
		Cursor c = getReadableDb().query("articles", 
				new String[] { "COUNT(*)" }, null, null, null, null, null);
		if (c.moveToFirst()) {
			int modified = c.getInt(0);
			c.close();
			
			return modified > 0;
		}
		
		return false;
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleUnread(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String,String> map = new HashMap<String,String>() {
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
	
		HashMap<String,String> map = new HashMap<String,String>() {
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
	
		HashMap<String,String> map = new HashMap<String,String>() {
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
				if (!m_enableCats || m_activeCategory != null)
					refreshFeeds();
				else
					refreshCategories();
			}
			
		};
	
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("sid", m_sessionId);
				put("op", "catchupFeed");
				put("feed_id", String.valueOf(feed.id));
				if (feed.is_cat) put("is_cat", "1");
			}			 
		};

		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	private void toggleArticlesMarked(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());
	
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
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
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("sid", m_sessionId);
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "2");
			}			 
		};

		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	private void toggleArticlesPublished(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());
	
		@SuppressWarnings("serial")
		HashMap<String,String> map = new HashMap<String,String>() {
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
			ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
			
			if (cm.getBackgroundDataSetting()) {
				NetworkInfo networkInfo = cm.getActiveNetworkInfo();
				if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
			
					if (!m_enableCats || m_activeCategory != null)
						refreshFeeds();
					else
						refreshCategories();
				}
			}
		}
	}
	
	public synchronized void refreshFeeds() {
		if (m_sessionId != null) {
			FeedsFragment frag = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds_fragment);
	
			Log.d(TAG, "Refreshing feeds...");
	
			if (frag != null) {
				frag.refresh(true);
			}
		}
	}
	
	public synchronized void refreshCategories() {
		FeedCategoriesFragment frag = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentById(R.id.cats_fragment);

		Log.d(TAG, "Refreshing categories...");

		if (frag != null) {
			frag.refresh(true);
		}
	}

	private void setUnreadOnly(boolean unread) {
		m_unreadOnly = unread;
		
		if (!m_enableCats || m_activeCategory != null )
			refreshFeeds();
		else
			refreshCategories();
	}
	
	@Override
	public boolean getUnreadOnly() {
		return m_unreadOnly;
	}

	private void setUnreadArticlesOnly(boolean unread) {
		m_unreadArticlesOnly = unread;
		
		HeadlinesFragment frag = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		
		if (frag != null) frag.refresh(false);
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
			m_sessionId = savedInstanceState.getString("sessionId");
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_activeFeed = savedInstanceState.getParcelable("activeFeed");
			m_selectedArticle = savedInstanceState.getParcelable("selectedArticle");
			m_unreadArticlesOnly = savedInstanceState.getBoolean("unreadArticlesOnly");
			m_activeCategory = savedInstanceState.getParcelable("activeCategory");
			m_apiLevel = savedInstanceState.getInt("apiLevel");
			m_isLicensed = savedInstanceState.getInt("isLicensed");
		}
		
		m_enableCats = m_prefs.getBoolean("enable_cats", false);
		
		Display display = getWindowManager().getDefaultDisplay();
		
		int width = display.getWidth();
		int height = display.getHeight();
		
		if (height > width) { int tmp = height; width = tmp; height = width; }
		
		m_smallScreenMode = width < 960 || height < 720; 
		
		setContentView(R.layout.main);

		initDatabase();
		
		m_isOffline = m_prefs.getBoolean("offline_mode_active", false);
		
		Log.d(TAG, "m_isOffline=" + m_isOffline);
		Log.d(TAG, "m_smallScreenMode=" + m_smallScreenMode);
		Log.d(TAG, "m_compatMode=" + m_compatMode);

		if (!m_compatMode) {
			new TransitionHelper((LinearLayout)findViewById(R.id.main));
		}

		if (m_isOffline) {
			Intent refresh = new Intent(this, OfflineActivity.class);
			startActivity(refresh);
			finish();
		} else {
			List<PackageInfo> pkgs = getPackageManager().getInstalledPackages(0);
			
			for (PackageInfo p : pkgs) {
				if ("org.fox.ttrss.key".equals(p.packageName)) {
					m_isLicensed = 1;
					Log.d(TAG, "license apk found");
					break;
				}
			}
			
			if (m_smallScreenMode) {
				if (m_selectedArticle != null) {
					findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
					findViewById(R.id.cats_fragment).setVisibility(View.GONE);
					findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
				} else if (m_activeFeed != null) {
					findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
					findViewById(R.id.article_fragment).setVisibility(View.GONE);
					findViewById(R.id.cats_fragment).setVisibility(View.GONE);
				} else {
					findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
					//findViewById(R.id.article_fragment).setVisibility(View.GONE);
					
					if (m_enableCats && m_activeCategory == null) {
						findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
						findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
					} else {
						findViewById(R.id.cats_fragment).setVisibility(View.GONE);
					}
				}
			} else {
				if (m_selectedArticle == null) {
					findViewById(R.id.article_fragment).setVisibility(View.GONE);
					
					if (!m_enableCats || m_activeCategory != null)
						findViewById(R.id.cats_fragment).setVisibility(View.GONE);
					else
						findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				
				} else {
					findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
					findViewById(R.id.cats_fragment).setVisibility(View.GONE);
				}
			}
			
			if (m_sessionId != null) {
				loginSuccess();
			} else {
				login();
			}
		}
		
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
	private void offlineGetArticles() {
		Log.d(TAG, "offline: downloading articles... offset=" + m_articleOffset);

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

	@SuppressWarnings("unchecked")
	private void switchOffline() {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this).  
			setMessage(R.string.dialog_offline_switch_prompt).
			setPositiveButton(R.string.dialog_offline_go, new Dialog.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					
					Log.d(TAG, "offline: starting");
					
					if (m_sessionId != null) {
					
						findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
						findViewById(R.id.main).setVisibility(View.INVISIBLE);
						
						setLoadingStatus(R.string.offline_downloading, true);
						
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
				
										offlineGetArticles();
									} catch (Exception e) {
										e.printStackTrace();
										setLoadingStatus(R.string.offline_switch_error, false);
									}
								
								} else {
									setLoadingStatus(getErrorMessage(), false);
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
						switchOfflineSuccess();
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
	
	private void switchOfflineSuccess() {
		logout();
		//setLoadingStatus(R.string.blank, false);

		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("offline_mode_active", true);
		editor.commit();
		
		Intent refresh = new Intent(this, OfflineActivity.class);
		startActivity(refresh);
		finish();
		
	}
	
	private void setLoadingStatus(int status, boolean showProgress) {
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

		out.putString("sessionId", m_sessionId);
		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putParcelable("activeFeed", m_activeFeed);
		out.putParcelable("selectedArticle", m_selectedArticle);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putParcelable("activeCategory", m_activeCategory);
		out.putInt("apiLevel", m_apiLevel);
		out.putInt("isLicensed", m_isLicensed);
	}

	@Override
	public void onResume() {
		super.onResume();

		boolean needRefresh = !m_prefs.getString("theme", "THEME_DARK").equals(m_themeName) ||
			m_prefs.getBoolean("enable_cats", false) != m_enableCats;
		
		if (needRefresh) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			finish();
		} else if (m_sessionId != null) {
			m_refreshTask = new RefreshTask();
			m_refreshTimer = new Timer("Refresh");
			
			m_refreshTimer.schedule(m_refreshTask, 60*1000L, 120*1000L);
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

		/* item = menu.findItem(R.id.show_all_articles);
		
		if (getUnreadArticlesOnly()) {
			item.setTitle(R.string.show_all_articles);
		} else {
			item.setTitle(R.string.show_unread_articles);
		} */

		return true;
	}

	private void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);
		
		if (mi != null) {
			mi.setTitle(labelId);
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	
        	if (m_smallScreenMode) {
        		if (m_selectedArticle != null) {
        			closeArticle();
        		} else if (m_activeFeed != null) {
        			if (m_compatMode) {
        				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
        			}
        			
        			if (m_activeFeed != null && m_activeFeed.is_cat) {
        				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
        				findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
        				
            			refreshCategories();
        			} else {
        				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
        				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);

            			refreshFeeds();
        			}
    				m_activeFeed = null;
        			initMainMenu();

        		} else if (m_activeCategory != null) {
        			if (m_compatMode) {
        				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
        			}

        			closeCategory();
        			
        		} else {
        			finish();
        		}
        	} else {
	        	if (m_selectedArticle != null) {
	        		closeArticle();
	        	} else if (m_activeCategory != null) {
	        		findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
            		findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);

        			m_activeCategory = null;
        			
        			initMainMenu();
        			refreshCategories();
	        	} else {
	        		finish();
	        	}
        	}

        	return false;
        }
        return super.onKeyDown(keyCode, event);
    }
	
	private void closeCategory() {
		
		findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
		findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);

		m_activeCategory = null;
		
		initMainMenu();
		refreshCategories();	
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);

		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.update_feeds:
			if (!m_enableCats || m_activeCategory != null )
				refreshFeeds();
			else
				refreshCategories();
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
		case R.id.close_article:
			closeArticle();
			return true;
		case R.id.back_to_categories:
			closeCategory();
			return true;
		case R.id.headlines_select:
			if (hf != null) {
				Dialog dialog = new Dialog(this);
				AlertDialog.Builder builder = new AlertDialog.Builder(this)
					.setTitle(R.string.headlines_select_dialog)
					.setSingleChoiceItems(new String[] { getString(R.string.headlines_select_all), 
						getString(R.string.headlines_select_unread), getString(R.string.headlines_select_none) }, 0, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
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
				
				hf.notifyUpdated();
	
				ApiRequest req = new ApiRequest(getApplicationContext());

				final String articleIds = articlesToIdString(articles);

				@SuppressWarnings("serial")
				HashMap<String,String> map = new HashMap<String,String>() {
					{
						put("sid", m_sessionId);
						put("op", "updateArticle");
						put("article_ids", articleIds);
						put("mode", "0");
						put("field", "2");
					}			 
				};

				req.execute(map);
			}
			return true;
		case R.id.share_article:
			shareArticle(m_selectedArticle);
			return true;
		case R.id.toggle_marked:
			if (m_selectedArticle != null) {
				m_selectedArticle.marked = !m_selectedArticle.marked;
				saveArticleMarked(m_selectedArticle);
				updateHeadlines();
			}
			return true;
		case R.id.selection_select_none:
			if (hf != null) {				
				ArticleList selected = hf.getSelectedArticles();
				if (selected.size() > 0) {
					selected.clear();
					hf.notifyUpdated();
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
					hf.notifyUpdated();
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
					hf.notifyUpdated();
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
					hf.notifyUpdated();
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
						hf.notifyUpdated();
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
		/* case R.id.show_all_articles:
			setUnreadArticlesOnly(!getUnreadArticlesOnly());
	
			if (getUnreadArticlesOnly()) {
				item.setTitle(R.string.show_all_articles);
			} else {
				item.setTitle(R.string.show_unread_articles);
			}
			
			return true; */
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	private void shareArticle(Article article) {
		if (article != null) {
			Intent intent = new Intent(Intent.ACTION_SEND);
			
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, article.title);
			intent.putExtra(Intent.EXTRA_TEXT, article.link);

			startActivity(Intent.createChooser(intent, getString(R.id.share_article)));
		}
	}
	
	private void closeArticle() {
		if (m_compatMode) {
			findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
		}

		//boolean browseCats = m_prefs.getBoolean("browse_cats_like_feeds", false);
		
		if (m_smallScreenMode) {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);	
		} else {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			
			if (m_activeFeed != null) {
				if (m_activeFeed.is_cat) {
					findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
				} else {
					findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				}
			}
		}

		m_selectedArticle = null;

		initMainMenu();
		refreshFeeds();

	}

	@Override
	public void initMainMenu() {
		if (m_menu != null) {
			if (m_sessionId != null) {
				
				m_menu.setGroupVisible(R.id.menu_group_logged_in, true);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
				
				if (m_activeFeed != null) {
					//m_menu.findItem(R.id.load_more_articles).setVisible(m_canLoadMore);
					//m_menu.findItem(R.id.show_all_articles).setVisible(true);
				} else {
					m_menu.setGroupVisible(R.id.menu_group_headlines, false); 
					m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
				}
				
				if (m_selectedArticle != null) {
					m_menu.setGroupVisible(R.id.menu_group_article, true);
					
					m_menu.setGroupVisible(R.id.menu_group_feeds, false); 
					
					if (m_smallScreenMode) {
						m_menu.setGroupVisible(R.id.menu_group_headlines, false);
						m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
					} else {
						m_menu.setGroupVisible(R.id.menu_group_headlines, true); 
					}
				
				} else {
					m_menu.setGroupVisible(R.id.menu_group_article, false);

					if (m_activeFeed != null) {
						
						HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
						
						if (hf != null) {
							int numSelected = hf.getSelectedArticles().size();
							
							if (numSelected != 0) {
								m_menu.setGroupVisible(R.id.menu_group_headlines, false);
								m_menu.setGroupVisible(R.id.menu_group_headlines_selection, true);
							} else {
								m_menu.setGroupVisible(R.id.menu_group_headlines, true);
								m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
							}
							
						} else {
							m_menu.setGroupVisible(R.id.menu_group_headlines, true);
							m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
						}
						
						m_menu.setGroupVisible(R.id.menu_group_feeds, false); 
					} else {
						m_menu.setGroupVisible(R.id.menu_group_feeds, true); 
					}

					if (!m_smallScreenMode || m_activeFeed == null) {
						m_menu.findItem(R.id.show_feeds).setVisible(true);
						m_menu.findItem(R.id.update_feeds).setVisible(true);
					}
					
					m_menu.findItem(R.id.back_to_categories).setVisible(m_activeCategory != null);
				}

			} else {
				m_menu.setGroupVisible(R.id.menu_group_logged_in, false);
				m_menu.setGroupVisible(R.id.menu_group_feeds, false);
				m_menu.setGroupVisible(R.id.menu_group_headlines, false);
				m_menu.setGroupVisible(R.id.menu_group_article, false);
				m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);

				/* if (m_isOffline) {
					m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
					m_menu.findItem(R.id.go_online).setVisible(true);
					
				} else { */
					m_menu.setGroupVisible(R.id.menu_group_logged_out, true);
				//}
			}
		}		
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
	
		m_readableDb.close();
		m_writableDb.close();
		
	}

	private void syncOfflineRead() {
		Log.d(TAG, "syncing modified offline data... (read)");

		final String ids = getOfflineModifiedIds(ModifiedCriteria.READ);
		
		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						syncOfflineMarked();
					} else {
						setLoadingStatus(getErrorMessage(), false);
					}
				}
			};
			
			@SuppressWarnings("serial")
			HashMap<String,String> map = new HashMap<String,String>() {
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
			syncOfflineMarked();
		}
	}

	private void syncOfflineMarked() {
		Log.d(TAG, "syncing modified offline data... (marked)");

		final String ids = getOfflineModifiedIds(ModifiedCriteria.MARKED);
		
		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						syncOfflinePublished();
					} else {
						setLoadingStatus(getErrorMessage(), false);
					}
				}
			};
			
			@SuppressWarnings("serial")
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", ids);
					put("mode", "0");
					put("field", "0");
				}			 
			};

			req.execute(map);
		} else {
			syncOfflinePublished();
		}
	}

	private void syncOfflinePublished() {
		Log.d(TAG, "syncing modified offline data... (published)");

		final String ids = getOfflineModifiedIds(ModifiedCriteria.MARKED);
		
		if (ids.length() > 0) {
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				@Override
				protected void onPostExecute(JsonElement result) {
					if (result != null) {
						loginSuccessInitUI();
						loginSuccess();
						clearPendingOfflineData();
					} else {
						setLoadingStatus(getErrorMessage(), false);
					}
				}
			};
			
			@SuppressWarnings("serial")
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("sid", m_sessionId);
					put("op", "updateArticle");
					put("article_ids", ids);
					put("mode", "0");
					put("field", "1");
				}			 
			};

			req.execute(map);
		} else {
			loginSuccessInitUI();
			loginSuccess();
			clearPendingOfflineData();
		}
	}

	private void syncOfflineData() {
		setLoadingStatus(R.string.syncing_offline_data, true);
		syncOfflineRead();
	}
	
	private void loginSuccessInitUI() {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		if (m_enableCats) {
			FeedCategoriesFragment frag = new FeedCategoriesFragment();
			ft.replace(R.id.cats_fragment, frag);
		} else {
			FeedsFragment frag = new FeedsFragment(); 
			ft.replace(R.id.feeds_fragment, frag);
		}

		try {
			ft.commit();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}
	
	private void loginSuccess() {
		findViewById(R.id.loading_container).setVisibility(View.INVISIBLE);
		findViewById(R.id.main).setVisibility(View.VISIBLE);

		m_isOffline = false;
		
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
		
		m_refreshTimer.schedule(m_refreshTask, 60*1000L, 120*1000L);
	}
	
	private enum ModifiedCriteria { READ, MARKED, PUBLISHED };
		
	private String getOfflineModifiedIds(ModifiedCriteria criteria) {
		
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
		
		Cursor c = getReadableDb().query("articles", 
				null, "modified = 1 AND " + criteriaStr, null, null, null, null);

		String tmp = "";
		
		while (c.moveToNext()) {
			tmp += c.getInt(0) + ",";
		}

		tmp = tmp.replaceAll(",$", "");
		
		//Log.d(TAG, "getOfflineModifiedIds " + criteria + " = " + tmp);
		
		c.close();
		
		return tmp;
	}
	
	private class LoginRequest extends ApiRequest {
		public LoginRequest(Context context) {
			super(context);
		}
		
		@SuppressWarnings("unchecked")
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonObject content = result.getAsJsonObject();
					if (content != null) {
						m_sessionId = content.get("session_id").getAsString();
						
						Log.d(TAG, "Authenticated!");
						
						ApiRequest req = new ApiRequest(m_context) {
							protected void onPostExecute(JsonElement result) {
								if (result != null) {
									m_apiLevel = result.getAsJsonObject().get("level").getAsInt();
								} else {
									m_apiLevel = 0;
								}
								
								Log.d(TAG, "Received API level: " + m_apiLevel);
								
								if (hasPendingOfflineData()) {

									syncOfflineData();
									
									//loginSuccess();
								} else {
									loginSuccessInitUI();
									loginSuccess();
								}

							}
						};
						
						@SuppressWarnings("serial")
						HashMap<String,String> map = new HashMap<String,String>() {
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
				
				AlertDialog.Builder builder = new AlertDialog.Builder(m_context).  
					setMessage(R.string.dialog_offline_prompt).
					setPositiveButton(R.string.dialog_offline_go, new Dialog.OnClickListener() {
			        public void onClick(DialogInterface dialog, int which) {
			        	switchOfflineSuccess();
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
			
			//m_menu.findItem(R.id.login).setVisible(true);
		}

	}

	@Override
	public void onFeedSelected(Feed feed) {
		viewFeed(feed, false);
	}

	public Article getSelectedArticle() {
		return m_selectedArticle;
	}
	
	public void viewFeed(Feed feed, boolean append) {
		m_activeFeed = feed;
	
		initMainMenu();
		
		if (m_smallScreenMode) {
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
		}
		
		if (!append) {
			HeadlinesFragment hf = new HeadlinesFragment();
		
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
			ft.replace(R.id.headlines_fragment, hf);
			ft.commit();
		} else {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
			if (hf != null) {
				hf.refresh(true);
			}
		}
	}

	public void viewCategory(FeedCategory cat, boolean openAsFeed) {
		
		if (!openAsFeed) {
			findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);

			m_activeCategory = cat;

			FeedsFragment frag = new FeedsFragment();
	
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
			ft.replace(R.id.feeds_fragment, frag);
			ft.commit();
		} else {
			if (m_smallScreenMode) findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);

			m_activeFeed = new Feed(cat.id, cat.title, true);
			
			HeadlinesFragment frag = new HeadlinesFragment();
	
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
			ft.replace(R.id.headlines_fragment, frag);
			ft.commit();
			
		}
		
		initMainMenu();
	}

	public void openArticle(Article article, int compatAnimation) {
		m_selectedArticle = article;
		
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}
		
		initMainMenu();

		HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		
		if (hf != null) {
			hf.setActiveArticleId(article.id);
		}
		
		ArticleFragment frag = new ArticleFragment();
		
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
	
	@Override
	public Feed getActiveFeed() {
		return m_activeFeed;
	}

	@Override
	public FeedCategory getActiveCategory() {
		return m_activeCategory;
	}
	
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
		findViewById(R.id.main).setVisibility(View.INVISIBLE);
	
		TextView tv = (TextView)findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(R.string.login_ready);		
		}
		
		findViewById(R.id.loading_progress).setVisibility(View.GONE);
		
		initMainMenu();
	}

	@Override
	@SuppressWarnings({ "unchecked", "serial" })
	public void login() {		

		logout();
		
		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

			setLoadingStatus(R.string.login_need_configure, false);
			
		} else {
		
			LoginRequest ar = new LoginRequest(this); // do not use getApplicationContext() here because alertdialog chokes on it
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", "").trim());
					put("password", m_prefs.getString("password", "").trim());
				}			 
			};
	
			ar.execute(map);
			
			setLoadingStatus(R.string.login_in_progress, true);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    
		HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		FeedsFragment ff = (FeedsFragment)getSupportFragmentManager().findFragmentById(R.id.feeds_fragment);
		FeedCategoriesFragment cf = (FeedCategoriesFragment)getSupportFragmentManager().findFragmentById(R.id.cats_fragment);

    	switch (item.getItemId()) {
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
					hf.notifyUpdated();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.marked = !article.marked;
						saveArticleMarked(article);
						hf.notifyUpdated();
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
					hf.notifyUpdated();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.published = !article.published;
						saveArticlePublished(article);
						hf.notifyUpdated();
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
					hf.notifyUpdated();
				} else {
					Article article = hf.getArticleAtPosition(info.position);
					if (article != null) {
						article.unread = !article.unread;
						saveArticleUnread(article);
						hf.notifyUpdated();
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
						hf.notifyUpdated();
					}
				}
			}
			return true;
		/* case R.id.set_unread:
			if (hf != null) {
				Article article = hf.getArticleAtPosition(info.position);
				if (article != null) {
					article.unread = true;
					saveArticleUnread(article);
				}
			}
			break; */
			default:
		    	Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
				return super.onContextItemSelected(item);
    	}
	}


	@Override
	public Article getRelativeArticle(Article article, RelativeArticle ra) {
		HeadlinesFragment frag = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		if (frag != null) {
			ArticleList articles = frag.getAllArticles();
			for (int i = 0; i < articles.size(); i++) {
				Article a = articles.get(i);
				
				if (a.id == article.id) {
					if (ra == RelativeArticle.AFTER) {
						try {
							return articles.get(i+1);
						} catch (IndexOutOfBoundsException e) {
							return null;
						}
					} else {
						try {
							return articles.get(i-1);
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
	    }
	
	@Override
	public void onCatSelected(FeedCategory cat) {
		Log.d(TAG, "onCatSelected");
		boolean browse = m_prefs.getBoolean("browse_cats_like_feeds", false);
		
		viewCategory(cat, browse && cat.id >= 0);
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
						offlineGetArticles();
					} else {
						switchOfflineSuccess();
					}
					
					return;
					
				} catch (Exception e) {
					setLoadingStatus(R.string.offline_switch_error, false);
					Log.d(TAG, "offline: failed: exception when loading articles");
					e.printStackTrace();
				}
				
			} else {
				Log.d(TAG, "offline: failed: " + getErrorMessage());
				setLoadingStatus(getErrorMessage(), false);
			}
		}
	}

}