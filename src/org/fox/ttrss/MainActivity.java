package org.fox.ttrss;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MainActivity extends FragmentActivity implements FeedsFragment.OnFeedSelectedListener, ArticleOps, FeedCategoriesFragment.OnCatSelectedListener {
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
	private boolean m_smallScreenMode;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_canLoadMore = true;
	private boolean m_compatMode = false;
	private boolean m_enableCats = false;
	private int m_apiLevel = 0;

	public void updateHeadlines() {
		HeadlinesFragment frag = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		if (frag != null) {
			frag.notifyUpdated();
		}
	}
	
	public int getApiLevel() {
		return m_apiLevel;
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
	public void toggleArticlesMarked(final ArticleList articles) {
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
	public void toggleArticlesUnread(final ArticleList articles) {
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
	public void toggleArticlesPublished(final ArticleList articles) {
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
		FeedsFragment frag = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds_fragment);

		Log.d(TAG, "Refreshing feeds...");

		if (frag != null) {
			frag.refresh(true);
		}
	}
	
	public synchronized void refreshCategories() {
		FeedCategoriesFragment frag = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentById(R.id.cats_fragment);

		Log.d(TAG, "Refreshing categories...");

		if (frag != null) {
			frag.refresh(true);
		}
	}

	public void setUnreadOnly(boolean unread) {
		m_unreadOnly = unread;
		
		if (!m_enableCats || m_activeCategory != null )
			refreshFeeds();
		else
			refreshCategories();
	}
	
	public boolean getUnreadOnly() {
		return m_unreadOnly;
	}

	public void setUnreadArticlesOnly(boolean unread) {
		m_unreadArticlesOnly = unread;
		
		HeadlinesFragment frag = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
		
		if (frag != null) frag.refresh(false);
	}
	
	public boolean getUnreadArticlesOnly() {
		return m_unreadArticlesOnly;
	}

	public String getSessionId() {
		return m_sessionId;
	}
	
	/** Called when the activity is first created. */

	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());       

		m_compatMode = android.os.Build.VERSION.SDK_INT <= 10;
		
		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(m_compatMode ? R.style.DarkCompatTheme : R.style.DarkTheme);
		} else {
			setTheme(m_compatMode ? R.style.LightCompatTheme : R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);

		m_themeName = m_prefs.getString("theme", "THEME_DARK");
	
		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_activeFeed = savedInstanceState.getParcelable("activeFeed");
			m_selectedArticle = savedInstanceState.getParcelable("selectedArticle");
			m_unreadArticlesOnly = savedInstanceState.getBoolean("unreadArticlesOnly");
			m_canLoadMore = savedInstanceState.getBoolean("canLoadMore");
			m_activeCategory = savedInstanceState.getParcelable("activeCategory");
			m_apiLevel = savedInstanceState.getInt("apiLevel");
		}
		
		m_enableCats = m_prefs.getBoolean("enable_cats", false);
		
		Display display = getWindowManager().getDefaultDisplay();
		int orientation = display.getOrientation();
		int minWidth = orientation % 2 == 0 ? 1024 : 600;
		int minHeight = orientation % 2 == 0 ? 600 : 1024;
		
		if (display.getWidth() > minWidth && display.getHeight() >= minHeight) {
			m_smallScreenMode = false;
			
			setContentView(R.layout.main);
		} else {
			m_smallScreenMode = true;
		
			setContentView(R.layout.main_small);
		}

		Log.d(TAG, "m_smallScreenMode=" + m_smallScreenMode);
		Log.d(TAG, "orientation=" + display.getOrientation());
		Log.d(TAG, "m_compatMode=" + m_compatMode);

		if (!m_compatMode) {
			LayoutTransition transitioner = new LayoutTransition();
			LinearLayout layout = (LinearLayout)findViewById(R.id.main);
			layout.setLayoutTransition(transitioner);
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

		out.putString("sessionId", m_sessionId);
		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putParcelable("activeFeed", m_activeFeed);
		out.putParcelable("selectedArticle", m_selectedArticle);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putBoolean("canLoadMore", m_canLoadMore);
		out.putParcelable("activeCategory", m_activeCategory);
		out.putInt("apiLevel", m_apiLevel);
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

		item = menu.findItem(R.id.show_all_articles);
		
		if (getUnreadArticlesOnly()) {
			item.setTitle(R.string.show_all_articles);
		} else {
			item.setTitle(R.string.show_unread_articles);
		}

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
        		if (m_selectedArticle != null) {
        			closeArticle();
        		} else if (m_activeFeed != null) {
        			if (m_compatMode) {
        				findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_right));
        			}
        			
        			if (m_activeFeed.is_cat) {
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
		HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);

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
		case R.id.close_article:
			closeArticle();
			return true;
		case R.id.back_to_categories:
			closeCategory();
			return true;
		case R.id.headlines_select_all:
			if (hf != null) hf.setSelection(HeadlinesFragment.ArticlesSelection.ALL);
			return true;
		case R.id.headlines_select_none:
			if (hf != null) hf.setSelection(HeadlinesFragment.ArticlesSelection.NONE);
			return true;
		case R.id.headlines_select_unread:
			if (hf != null) hf.setSelection(HeadlinesFragment.ArticlesSelection.UNREAD);
			return true;
		case R.id.catchup_and_load:
			if (hf != null) {
				final ArticleList articles = hf.getUnreadArticles();
				
				ApiRequest req = new ApiRequest(getApplicationContext()) {
					@Override
					protected void onPostExecute(JsonElement result) {
						if (result != null) {
							for (Article a : articles)
								a.unread = false;
								
							viewFeed(m_activeFeed, true);
						}
					}
				};
					
				@SuppressWarnings("serial")
				HashMap<String,String> map = new HashMap<String,String>() {
					{
						put("sid", m_sessionId);
						put("op", "updateArticle");
						put("article_ids", articlesToIdString(articles));
						put("mode", "0");
						put("field", "2");
					}			 
				};

				req.execute(map);

			}
			return true;
		case R.id.load_more_articles:
			viewFeed(m_activeFeed, true);
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
		case R.id.toggle_published:
			if (m_selectedArticle != null) {
				m_selectedArticle.published = !m_selectedArticle.published;
				saveArticlePublished(m_selectedArticle);
				updateHeadlines();
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
		case R.id.show_all_articles:
			setUnreadArticlesOnly(!getUnreadArticlesOnly());
	
			if (getUnreadArticlesOnly()) {
				item.setTitle(R.string.show_all_articles);
			} else {
				item.setTitle(R.string.show_unread_articles);
			}
			
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void shareArticle(Article article) {
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

		if (m_smallScreenMode) {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);	
		} else {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);	
			findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
		}

		m_selectedArticle = null;

		initMainMenu();
		refreshFeeds();

	}

	public void setCanLoadMore(boolean canLoadMore) {
		m_canLoadMore = canLoadMore;
	}
	
	public void initMainMenu() {
		if (m_menu != null) {
			if (m_sessionId != null) {
				
				m_menu.setGroupVisible(R.id.menu_group_logged_in, true);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
				
				if (m_activeFeed != null) {
					m_menu.findItem(R.id.load_more_articles).setVisible(m_canLoadMore);
					m_menu.findItem(R.id.show_all_articles).setVisible(true);
				} else {
					m_menu.setGroupVisible(R.id.menu_group_headlines, false); 
				}
				
				if (m_selectedArticle != null) {
					m_menu.setGroupVisible(R.id.menu_group_article, true);
					
					m_menu.findItem(R.id.update_feeds).setVisible(false);
					m_menu.findItem(R.id.show_feeds).setVisible(false);
					m_menu.findItem(R.id.back_to_categories).setVisible(false);
					
					if (m_smallScreenMode) {
						m_menu.setGroupVisible(R.id.menu_group_headlines, false); 
					} else {
						m_menu.setGroupVisible(R.id.menu_group_headlines, true); 
					}
				
				} else {
					m_menu.setGroupVisible(R.id.menu_group_article, false);
					
					if (!m_smallScreenMode || m_activeFeed == null) {
						m_menu.findItem(R.id.show_feeds).setVisible(true);
						m_menu.findItem(R.id.update_feeds).setVisible(true);
					} else {
						m_menu.findItem(R.id.show_feeds).setVisible(false);
						m_menu.findItem(R.id.update_feeds).setVisible(false);
					}
					
					m_menu.findItem(R.id.back_to_categories).setVisible(m_activeCategory != null);
					
					if (m_activeFeed != null) {
						m_menu.setGroupVisible(R.id.menu_group_headlines, true); 
					}
				}

			} else {
				m_menu.setGroupVisible(R.id.menu_group_logged_in, false);
				m_menu.setGroupVisible(R.id.menu_group_feeds, false);
				m_menu.setGroupVisible(R.id.menu_group_headlines, false);
				m_menu.setGroupVisible(R.id.menu_group_article, false);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, true);
			}
			
			/* if (m_sessionId != null) {
				m_menu.findItem(R.id.login).setVisible(false);
			
				m_menu.findItem(R.id.logout).setVisible(m_activeFeed == null && m_selectedArticle == null);
			
				if (m_selectedArticle != null) {
					m_menu.findItem(R.id.close_article).setVisible(true);
					m_menu.findItem(R.id.share_article).setVisible(true);
					m_menu.findItem(R.id.toggle_marked).setVisible(true);
					m_menu.findItem(R.id.toggle_published).setVisible(true);
					m_menu.findItem(R.id.set_unread).setVisible(true);
					
					m_menu.findItem(R.id.update_feeds).setVisible(false);
					m_menu.findItem(R.id.show_feeds).setVisible(false);
					m_menu.findItem(R.id.back_to_categories).setVisible(false);
				} else {
					m_menu.findItem(R.id.close_article).setVisible(false);
					m_menu.findItem(R.id.share_article).setVisible(false);
					m_menu.findItem(R.id.toggle_marked).setVisible(false);
					m_menu.findItem(R.id.toggle_published).setVisible(false);
					m_menu.findItem(R.id.set_unread).setVisible(false);
					
					if (!m_smallScreenMode || m_activeFeed == null) {
						m_menu.findItem(R.id.show_feeds).setVisible(true);
						m_menu.findItem(R.id.update_feeds).setVisible(true);
					} else {
						m_menu.findItem(R.id.show_feeds).setVisible(false);
						m_menu.findItem(R.id.update_feeds).setVisible(false);
					}
					
					m_menu.findItem(R.id.back_to_categories).setVisible(m_activeCategory != null);
				}

				if (!m_smallScreenMode) {
					m_menu.findItem(R.id.load_more_articles).setVisible(m_activeFeed != null && m_canLoadMore);
					m_menu.findItem(R.id.show_all_articles).setVisible(m_activeFeed != null);
				} else {
					m_menu.findItem(R.id.load_more_articles).setVisible(m_activeFeed != null && m_selectedArticle == null && m_canLoadMore &&
							(!m_enableCats || m_activeCategory != null));
					m_menu.findItem(R.id.show_all_articles).setVisible(m_activeFeed != null && m_selectedArticle == null);
				}

				
				
			} else {
				m_menu.findItem(R.id.login).setVisible(true);
				
				m_menu.findItem(R.id.logout).setVisible(false);
				m_menu.findItem(R.id.close_article).setVisible(false);
				m_menu.findItem(R.id.share_article).setVisible(false);
				m_menu.findItem(R.id.load_more_articles).setVisible(false);
				m_menu.findItem(R.id.back_to_categories).setVisible(false);

				m_menu.findItem(R.id.update_feeds).setVisible(false);
				m_menu.findItem(R.id.show_feeds).setVisible(false);
			} */
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
	
	private void loginSuccess() {
		findViewById(R.id.loading_container).setVisibility(View.INVISIBLE);
		findViewById(R.id.main).setVisibility(View.VISIBLE);

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
								
								FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
								
								if (m_enableCats) {
									FeedCategoriesFragment frag = new FeedCategoriesFragment();
									ft.replace(R.id.cats_fragment, frag);
								} else {
									FeedsFragment frag = new FeedsFragment(); 
									ft.replace(R.id.feeds_fragment, frag);
								}

								ft.commit();

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

						
						loginSuccess();
						return;
					}
							
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}
	
			m_sessionId = null;

			setLoadingStatus(getErrorMessage(), false);
			m_menu.findItem(R.id.login).setVisible(true);
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
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		}
				
	}
	
	public Feed getActiveFeed() {
		return m_activeFeed;
	}

	public FeedCategory getActiveCategory() {
		return m_activeCategory;
	}
	
	public void logout() {
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

	@SuppressWarnings({ "unchecked", "serial" })
	public void login() {		

		logout();
		
		if (m_prefs.getString("ttrss_url", "").length() == 0) {

			setLoadingStatus(R.string.login_need_configure, false);
			
		} else {
		
			LoginRequest ar = new LoginRequest(getApplicationContext());
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", null));
					put("password", m_prefs.getString("password", null));
				}			 
			};
	
			ar.execute(map);
			
			setLoadingStatus(R.string.login_in_progress, true);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    
		Log.d(TAG, "onContextItemSelected=" + item.getItemId());

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
    		break;
    	case R.id.browse_feeds:
    		if (cf != null) {
    			FeedCategory cat = cf.getCategoryAtPosition(info.position);
    			if (cat != null) {
    				viewCategory(cat, false);
    				cf.setSelectedCategory(cat);
    			}
    		}
    		break;
    	case R.id.catchup_category:
    		if (cf != null) {
    			FeedCategory cat = cf.getCategoryAtPosition(info.position);
    			if (cat != null) {
    	    		catchupFeed(new Feed(cat.id, cat.title, true));
    			}
    		}
    		break;
    	case R.id.catchup_feed:
    		if (ff != null) {
    			Feed feed = ff.getFeedAtPosition(info.position);
    			if (feed != null) {
    				catchupFeed(feed);
    			}
    		}
    		break;
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
    	}
			
		return true;
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
	public void onCatSelected(FeedCategory cat) {
		Log.d(TAG, "onCatSelected");
		viewCategory(cat, m_prefs.getBoolean("browse_cats_like_feeds", false));
	}
}