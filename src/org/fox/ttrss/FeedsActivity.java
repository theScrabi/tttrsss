package org.fox.ttrss;

import java.util.Date;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.util.AppRater;

import com.actionbarsherlock.view.MenuItem;

import android.view.ViewGroup;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

public class FeedsActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	private static final int HEADLINES_REQUEST = 1;
	
	protected SharedPreferences m_prefs;
	protected long m_lastRefresh = 0;
	
	private boolean m_actionbarUpEnabled = false;
	private int m_actionbarRevertDepth = 0;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.feeds);
		
		setSmallScreen(findViewById(R.id.headlines_fragment) == null); 

		GlobalState.getInstance().load(savedInstanceState);

		Intent intent = getIntent();
		
		if (savedInstanceState == null) {
			
			if (intent.getParcelableExtra("feed") != null || intent.getParcelableExtra("category") != null || 
				intent.getParcelableExtra("article") != null) {
			
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
				m_actionbarUpEnabled = true;
				
				Feed feed = (Feed) intent.getParcelableExtra("feed");
				FeedCategory cat = (FeedCategory) intent.getParcelableExtra("category");
				Article article = (Article) intent.getParcelableExtra("article");
	
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
	
				if (article != null) {
					Article original = GlobalState.getInstance().m_loadedArticles.findById(article.id);
					
					ArticlePager ap = new ArticlePager();
					ap.initialize(original != null ? original : article, feed);
					
					ft.replace(R.id.feeds_fragment, ap, FRAG_ARTICLE);
					
					ap.setSearchQuery(intent.getStringExtra("searchQuery"));
					
					setTitle(feed.title);
				} else {
					if (feed != null) {
						HeadlinesFragment hf = new HeadlinesFragment();
						hf.initialize(feed);
						ft.replace(R.id.feeds_fragment, hf, FRAG_HEADLINES);
						
						setTitle(feed.title);
					}
					
					if (cat != null) {
						FeedsFragment ff = new FeedsFragment();
						ff.initialize(cat);
						ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);
						
						setTitle(cat.title);
					}
				}
	
				ft.commit();

			} else  {
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				
				if (m_prefs.getBoolean("enable_cats", false)) {
					ft.replace(R.id.feeds_fragment, new FeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.feeds_fragment, new FeedsFragment(), FRAG_FEEDS);
				}
				
				/* if (!isSmallScreen()) {
					ft.replace(R.id.headlines_fragment, new HeadlinesFragment(new Feed(-3, "Fresh articles", false)));
				} */
				
				ft.commit();
				
				AppRater.appLaunched(this);
				checkTrial(true);
			}
		} else { // savedInstanceState != null
			m_actionbarUpEnabled = savedInstanceState.getBoolean("actionbarUpEnabled");
			m_actionbarRevertDepth = savedInstanceState.getInt("actionbarRevertDepth");

			if (!isSmallScreen()) {
				// temporary hack because FeedsActivity doesn't track whether active feed is open
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				container.setWeightSum(3f);
			}

			if (m_actionbarUpEnabled) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			}
		}
		
		if (!isCompatMode() && !isSmallScreen()) {
			((ViewGroup)findViewById(R.id.headlines_fragment)).setLayoutTransition(new LayoutTransition());
			((ViewGroup)findViewById(R.id.feeds_fragment)).setLayoutTransition(new LayoutTransition());
		}

	}
	
	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null && getSessionId() != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null && af.isAdded());

			m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());
						
			if (isSmallScreen()) {
				m_menu.findItem(R.id.update_headlines).setVisible(hf != null && hf.isAdded());
			} else {
				m_menu.findItem(R.id.update_headlines).setVisible(false);
			}
			
			if (af != null) {
				if (af.getSelectedArticle() != null && af.getSelectedArticle().attachments != null && af.getSelectedArticle().attachments.size() > 0) {
					if (!isCompatMode()) {
						m_menu.findItem(R.id.toggle_attachments).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
					}
					m_menu.findItem(R.id.toggle_attachments).setVisible(true);
				} else {
					if (!isCompatMode()) {
						m_menu.findItem(R.id.toggle_attachments).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
					}
					m_menu.findItem(R.id.toggle_attachments).setVisible(false);
				}
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

		if (isSmallScreen()) {
			Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
			intent.putExtra("feed", feed);

			startActivityForResult(intent, 0);
		} else {
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			ft.replace(R.id.headlines_fragment, new LoadingFragment(), null);
			ft.commit();

			if (!isCompatMode()) {
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				float wSum = container.getWeightSum();
				if (wSum <= 2.0f) {
					ObjectAnimator anim = ObjectAnimator.ofFloat(container, "weightSum", wSum, 3.0f);
					anim.setDuration(200);
					anim.start();
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
				}
			}, 10);
			

			Date date = new Date();

			if (date.getTime() - m_lastRefresh > 10000) {
				m_lastRefresh = date.getTime();
				refresh(false);
			}
		}
	}
	
	public void onCatSelected(FeedCategory cat, boolean openAsFeed) {
		FeedCategoriesFragment fc = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		if (!openAsFeed) {
			
			if (isSmallScreen()) {
			
				Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
				intent.putExtra("category", cat);
		 	   
				startActivityForResult(intent, 0);
				
			} else {
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
			}
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (m_actionbarRevertDepth > 0) {
				
				m_actionbarRevertDepth = m_actionbarRevertDepth - 1;
				m_actionbarUpEnabled = m_actionbarRevertDepth > 0;
				getSupportActionBar().setDisplayHomeAsUpEnabled(m_actionbarUpEnabled);
				
				onBackPressed();
			} else {
				finish();				
			}
			return true;
		case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());
			initMenu();
			refresh();
			return true;
		case R.id.update_feeds:
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
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		initMenu();
		
		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);	
		
		out.putBoolean("actionbarUpEnabled", m_actionbarUpEnabled);
		out.putInt("actionbarRevertDepth", m_actionbarRevertDepth);
		
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
		if (isSmallScreen()) {
			Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
			
			GlobalState.getInstance().m_activeFeed = feed;
			GlobalState.getInstance().m_loadedArticles.clear();

			intent.putExtra("feed", feed);
			intent.putExtra("article", new Article());
			startActivityForResult(intent, 0);
		} else {
			GlobalState.getInstance().m_loadedArticles.clear();
			
			Intent intent = new Intent(FeedsActivity.this, HeadlinesActivity.class);
			intent.putExtra("feed", feed);
			intent.putExtra("article", (Article)null);
			intent.putExtra("searchQuery", (String)null);
	 	   
			startActivityForResult(intent, HEADLINES_REQUEST);
			overridePendingTransition(R.anim.right_slide_in, 0);
		}
	}
	
	public void onArticleSelected(Article article, boolean open) {
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}
		
		if (open) {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			if (isSmallScreen()) {

				//GlobalState.getInstance().m_loadedArticles.clear();
				
				Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
				intent.putExtra("feed", hf.getFeed());
				intent.putExtra("article", article);
				intent.putExtra("searchQuery", hf.getSearchQuery());
		 	   
				startActivityForResult(intent, 0);
				
				
			} else {
				Intent intent = new Intent(FeedsActivity.this, HeadlinesActivity.class);
				intent.putExtra("feed", hf.getFeed());
				intent.putExtra("article", article);
				intent.putExtra("searchQuery", hf.getSearchQuery());
		 	   
				startActivityForResult(intent, HEADLINES_REQUEST);
				overridePendingTransition(R.anim.right_slide_in, 0);
			}
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
}
