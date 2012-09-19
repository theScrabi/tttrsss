package org.fox.ttrss;

import java.util.Date;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.util.AppRater;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

public class FeedsActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	protected long m_lastRefresh = 0; 
	
	@SuppressLint("NewApi")
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

		setContentView(R.layout.feeds);
		
		setSmallScreen(findViewById(R.id.headlines_fragment) == null); 
		
		Intent intent = getIntent();
		
		if (savedInstanceState == null) {
			
			if (intent.getParcelableExtra("feed") != null || intent.getParcelableExtra("category") != null || 
				intent.getParcelableExtra("article") != null) {
			
				if (!isCompatMode()) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
				}
				
				Feed feed = (Feed) intent.getParcelableExtra("feed");
				FeedCategory cat = (FeedCategory) intent.getParcelableExtra("category");
				Article article = (Article) intent.getParcelableExtra("article");
	
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
	
				if (article != null) {
					Article original = GlobalState.getInstance().m_loadedArticles.findById(article.id);
					
					ArticlePager ap = new ArticlePager(original != null ? original : article, feed);
					ft.replace(R.id.feeds_fragment, ap, FRAG_ARTICLE);
					
					ap.setSearchQuery(intent.getStringExtra("searchQuery"));
					
					setTitle(intent.getStringExtra("feedTitle"));
				} else {
					if (feed != null) {
						HeadlinesFragment hf = new HeadlinesFragment(feed);
						ft.replace(R.id.feeds_fragment, hf, FRAG_HEADLINES);
						
						setTitle(feed.title);
					}
					
					if (cat != null) {
						FeedsFragment ff = new FeedsFragment(cat);
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
				
				ft.commit();
				
				AppRater.appLaunched(this);
			}
		}
	}
	
	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null && m_sessionId != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null && af.isAdded());

			m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && hf.getSelectedArticles().size() == 0);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, hf != null && hf.isAdded() && hf.getSelectedArticles().size() != 0);
			
			if (isSmallScreen()) {
				m_menu.findItem(R.id.update_headlines).setVisible(hf != null && hf.isAdded());
			} else {
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

		if (isSmallScreen()) {
				
			Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
			intent.putExtra("sessionId", m_sessionId);
			intent.putExtra("apiLevel", m_apiLevel);
			intent.putExtra("feed", feed);
	 	   
			startActivityForResult(intent, 0);
			
			//HeadlinesFragment hf = new HeadlinesFragment(feed);
			//ft.replace(R.id.feeds_fragment, hf, FRAG_HEADLINES);
			//ft.addToBackStack(null);
		} else {
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			HeadlinesFragment hf = new HeadlinesFragment(feed);
			ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
			
			ft.commit();

			Date date = new Date();

			if (date.getTime() - m_lastRefresh > 10000) {
				m_lastRefresh = date.getTime();
				refresh(false);
			}
		}
	}
	
	public void onCatSelected(FeedCategory cat, boolean openAsFeed) {

		if (!openAsFeed) {
			
			if (isSmallScreen()) {
			
				Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
				intent.putExtra("sessionId", m_sessionId);
				intent.putExtra("apiLevel", m_apiLevel);				
				intent.putExtra("category", cat);
		 	   
				startActivityForResult(intent, 0);
				
			} else {
				FragmentTransaction ft = getSupportFragmentManager()
						.beginTransaction();

				FeedsFragment ff = new FeedsFragment(cat);
				ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);

				ft.addToBackStack(null);
				ft.commit();
			}
		} else {
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
		case R.id.show_feeds:
			m_unreadOnly = !m_unreadOnly;
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
	protected void loginSuccess() {
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		initMenu();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
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

	public void onArticleSelected(Article article, boolean open) {
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}
		
		if (open) {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			if (isSmallScreen()) {

				Intent intent = new Intent(FeedsActivity.this, FeedsActivity.class);
				intent.putExtra("sessionId", m_sessionId);
				intent.putExtra("apiLevel", m_apiLevel);
				
				intent.putExtra("feedTitle", hf.getFeed().title);
				intent.putExtra("feed", hf.getFeed());
				intent.putExtra("article", article);
				intent.putExtra("searchQuery", hf.getSearchQuery());
		 	   
				startActivityForResult(intent, 0);
				
				
			} else {
				Intent intent = new Intent(FeedsActivity.this, HeadlinesActivity.class);
				intent.putExtra("sessionId", m_sessionId);
				intent.putExtra("apiLevel", m_apiLevel);
				
				intent.putExtra("feed", hf.getFeed());
				intent.putExtra("article", article);
				intent.putExtra("searchQuery", hf.getSearchQuery());
		 	   
				startActivityForResult(intent, 0);
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
}
