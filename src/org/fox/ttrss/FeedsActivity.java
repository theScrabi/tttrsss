package org.fox.ttrss;


import java.util.Date;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.util.AppRater;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.widget.LinearLayout;

import com.actionbarsherlock.view.MenuItem;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

public class FeedsActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	private static final int HEADLINES_REQUEST = 1;
	
	protected SharedPreferences m_prefs;
	protected long m_lastRefresh = 0;
	
	private boolean m_actionbarUpEnabled = false;
	private int m_actionbarRevertDepth = 0;
	private SlidingMenu m_slidingMenu;
	private boolean m_feedIsSelected = false;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.headlines);		
		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null && 
				findViewById(R.id.sw600dp_port_anchor) == null);
		
		GlobalState.getInstance().load(savedInstanceState);
		
		if (isSmallScreen() || findViewById(R.id.sw600dp_port_anchor) != null) {
			m_slidingMenu = new SlidingMenu(this);
			
			if (findViewById(R.id.sw600dp_port_anchor) != null) {
				m_slidingMenu.setBehindWidth(getScreenWidthInPixel() * 2/3);
			}
			
			m_slidingMenu.setMode(SlidingMenu.LEFT);
			m_slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
			m_slidingMenu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
			m_slidingMenu.setMenu(R.layout.feeds);
			m_slidingMenu.setSlidingEnabled(true);
			m_slidingMenu.setOnOpenedListener(new SlidingMenu.OnOpenedListener() {
					
				@Override
				public void onOpened() {
					if (m_actionbarRevertDepth == 0) {
						m_actionbarUpEnabled = false;
						getSupportActionBar().setDisplayHomeAsUpEnabled(false);
						refresh(false);
					}
					
					m_feedIsSelected = false;
					initMenu();
				}
			});
		}

		if (savedInstanceState == null) {
			if (m_slidingMenu != null)
				m_slidingMenu.showMenu();

			Intent i = getIntent();
			boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);
			
			Log.d(TAG, "is_shortcut_mode: " + shortcutMode);

			if (shortcutMode) {
				int feedId = i.getIntExtra("feed_id", 0);
				boolean isCat = i.getBooleanExtra("feed_is_cat", false);
				String feedTitle = i.getStringExtra("feed_title");
				
				Feed tmpFeed = new Feed(feedId, feedTitle, isCat);
				
				onFeedSelected(tmpFeed);
			}
			
			m_pullToRefreshAttacher.setRefreshing(true);

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
							
			if (m_prefs.getBoolean("enable_cats", false)) {
				ft.replace(R.id.feeds_fragment, new FeedCategoriesFragment(), FRAG_CATS);				
			} else {
				ft.replace(R.id.feeds_fragment, new FeedsFragment(), FRAG_FEEDS);
			}

			ft.commit();
				
			AppRater.appLaunched(this);
			checkTrial(true);

		} else { // savedInstanceState != null
			m_actionbarUpEnabled = savedInstanceState.getBoolean("actionbarUpEnabled");
			m_actionbarRevertDepth = savedInstanceState.getInt("actionbarRevertDepth");
			m_feedIsSelected = savedInstanceState.getBoolean("feedIsSelected");

			if (m_slidingMenu != null && m_feedIsSelected == false) {
				m_slidingMenu.showMenu();
			} else if (m_slidingMenu != null) {
				m_actionbarUpEnabled = true;
			} else {
				m_actionbarUpEnabled = m_actionbarRevertDepth > 0;
			}

			if (m_actionbarUpEnabled) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}

			if (!isSmallScreen()) {
				// temporary hack because FeedsActivity doesn't track whether active feed is open
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				
				if (container != null)
					container.setWeightSum(3f);
			}
		}
		
		/* if (!isCompatMode() && !isSmallScreen()) {
			((ViewGroup)findViewById(R.id.headlines_fragment)).setLayoutTransition(new LayoutTransition());
			((ViewGroup)findViewById(R.id.feeds_fragment)).setLayoutTransition(new LayoutTransition());
		} */

	}
	
	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null && getSessionId() != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			if (m_slidingMenu != null) {
				m_menu.setGroupVisible(R.id.menu_group_feeds, m_slidingMenu.isMenuShowing());
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && !m_slidingMenu.isMenuShowing());
			} else {
				m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());
				
				m_menu.findItem(R.id.update_headlines).setVisible(false);
			}
			
			MenuItem item = m_menu.findItem(R.id.show_feeds);

			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_all_feeds);
			} else {
				item.setTitle(R.string.menu_unread_feeds);
			}
		}		
	}
	
	public void onFeedSelected(Feed feed) {
		GlobalState.getInstance().m_loadedArticles.clear();
		m_pullToRefreshAttacher.setRefreshing(true);

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			ft.replace(R.id.headlines_fragment, new LoadingFragment(), null);
			ft.commit();

			if (!isCompatMode() && !isSmallScreen()) {
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				if (container != null) {
					float wSum = container.getWeightSum();
					if (wSum <= 2.0f) {
						ObjectAnimator anim = ObjectAnimator.ofFloat(container, "weightSum", wSum, 3.0f);
						anim.setDuration(200);
						anim.start();
					}
				}
			}

			final Feed fFeed = feed;
			
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					FragmentTransaction ft = getSupportFragmentManager()
							.beginTransaction();

					HeadlinesFragment hf = new HeadlinesFragment();
					hf.initialize(fFeed);
					ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
					
					ft.commit();

					m_feedIsSelected = true;
					
					if (m_slidingMenu != null) { 
						m_slidingMenu.showContent();
						getSupportActionBar().setDisplayHomeAsUpEnabled(true);
						m_actionbarUpEnabled = true;
					}
				}
			}, 10);
			

			Date date = new Date();

			if (date.getTime() - m_lastRefresh > 10000) {
				m_lastRefresh = date.getTime();
				refresh(false);
			}
	}
	
	public void onCatSelected(FeedCategory cat, boolean openAsFeed) {
		FeedCategoriesFragment fc = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		m_pullToRefreshAttacher.setRefreshing(true);
		
		if (!openAsFeed) {
			
			if (fc != null) {
				fc.setSelectedCategory(null);
			}

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			FeedsFragment ff = new FeedsFragment();
			ff.initialize(cat);
			ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);

			ft.addToBackStack(null);
			ft.commit();
			
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			m_actionbarUpEnabled = true;
			m_actionbarRevertDepth = m_actionbarRevertDepth + 1;

		} else {
			
			if (fc != null) {
				fc.setSelectedCategory(cat);
			}

			Feed feed = new Feed(cat.id, cat.title, true);
			onFeedSelected(feed);
		}
	}
	
	public void onCatSelected(FeedCategory cat) {
		onCatSelected(cat, m_prefs.getBoolean("browse_cats_like_feeds", false));		
	}
	
	@Override
	public void onBackPressed() {
		if (m_actionbarRevertDepth > 0) {
			
			if (m_feedIsSelected && m_slidingMenu != null && !m_slidingMenu.isMenuShowing()) {
				m_slidingMenu.showMenu();
			} else {			
				m_actionbarRevertDepth = m_actionbarRevertDepth - 1;
				m_actionbarUpEnabled = m_actionbarRevertDepth > 0;
				getSupportActionBar().setDisplayHomeAsUpEnabled(m_actionbarUpEnabled);
			
				onBackPressed();
			}
		} else if (m_slidingMenu != null && !m_slidingMenu.isMenuShowing()) {
			m_slidingMenu.showMenu();
		} else {
			super.onBackPressed();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (m_actionbarUpEnabled)
				onBackPressed();
			return true;
		case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());
			initMenu();
			refresh();
			return true;
		case R.id.update_feeds:
			m_pullToRefreshAttacher.setRefreshing(true);
			refresh();
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void loginSuccess(boolean refresh) {
		setLoadingStatus(R.string.blank, false);
		//findViewById(R.id.loading_container).setVisibility(View.GONE);
		initMenu();
		
		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);	
		
		out.putBoolean("actionbarUpEnabled", m_actionbarUpEnabled);
		out.putInt("actionbarRevertDepth", m_actionbarRevertDepth);
		out.putBoolean("feedIsSelected", m_feedIsSelected);
		
		//if (m_slidingMenu != null )
		//	out.putBoolean("slidingMenuVisible", m_slidingMenu.isMenuShowing());
		
		GlobalState.getInstance().save(out);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		initMenu();
	}

	@Override
	public void onArticleListSelectionChange(ArticleList m_selectedArticles) {
		initMenu();		
	}

	public void openFeedArticles(Feed feed) {
		GlobalState.getInstance().m_loadedArticles.clear();
		
		Intent intent = new Intent(FeedsActivity.this, HeadlinesActivity.class);
		intent.putExtra("feed", feed);
		intent.putExtra("article", (Article)null);
		intent.putExtra("searchQuery", (String)null);
 	   
		startActivityForResult(intent, HEADLINES_REQUEST);
		overridePendingTransition(R.anim.right_slide_in, 0);
	}
	
	public void onArticleSelected(Article article, boolean open) {
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}
		
		if (open) {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			Intent intent = new Intent(FeedsActivity.this, HeadlinesActivity.class);
			intent.putExtra("feed", hf.getFeed());
			intent.putExtra("article", article);
			intent.putExtra("searchQuery", hf.getSearchQuery());
	 	   
			startActivityForResult(intent, HEADLINES_REQUEST);
			overridePendingTransition(R.anim.right_slide_in, 0);

		} else {
			initMenu();
		}
	}

	@Override
	public void onArticleSelected(Article article) {
		onArticleSelected(article, true);		
	}

	public void catchupFeed(final Feed feed) {
		super.catchupFeed(feed);
		refresh();
	}

	@Override
	public void onHeadlinesLoaded(boolean appended) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == HEADLINES_REQUEST) {
			GlobalState.getInstance().m_activeArticle = null;			
		}		
	}

	public void createFeedShortcut(Feed feed) {
		final Intent shortcutIntent = new Intent(this, FeedsActivity.class);
		shortcutIntent.putExtra("feed_id", feed.id);
		shortcutIntent.putExtra("feed_is_cat", feed.is_cat);
		shortcutIntent.putExtra("feed_title", feed.title);
		shortcutIntent.putExtra("shortcut_mode", true);
		
		Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
		
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, feed.title);
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.icon));  
		intent.putExtra("duplicate", false);
		
		sendBroadcast(intent);
		
		toast(R.string.shortcut_has_been_placed_on_the_home_screen);
	}
	
	public void createCategoryShortcut(FeedCategory cat) {
		createFeedShortcut(new Feed(cat.id, cat.title, true));
	}
}
