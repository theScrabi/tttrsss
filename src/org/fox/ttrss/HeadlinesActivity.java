package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

public class HeadlinesActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	
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

		setContentView(R.layout.headlines);
		
		if (!isCompatMode()) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
		
		setSmallScreen(findViewById(R.id.headlines_fragment) == null); 
		
		GlobalState.getInstance().load(savedInstanceState);

		if (isPortrait()) {
			findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
		}
		
		if (savedInstanceState == null) {
			Intent i = getIntent();
			
			if (i.getExtras() != null) {
				final Feed feed = i.getParcelableExtra("feed");
				final Article article = i.getParcelableExtra("article");
				final String searchQuery = i.getStringExtra("searchQuery");
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				ft.replace(R.id.headlines_fragment, new LoadingFragment(), null);
				ft.replace(R.id.article_fragment, new LoadingFragment(), null);
				
				ft.commit();
				
				setTitle(feed.title);

				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

						HeadlinesFragment hf = new HeadlinesFragment(feed, article);
						hf.setSearchQuery(searchQuery);

						ArticlePager af = new ArticlePager(article != null ? hf.getArticleById(article.id) : new Article(), feed);
						af.setSearchQuery(searchQuery);

						ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
						ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
						
						ft.commit();
					}
				}, 25);
				
			}
		}
		
		/* if (!isCompatMode()) {
			((ViewGroup)findViewById(R.id.headlines_fragment)).setLayoutTransition(new LayoutTransition());
			((ViewGroup)findViewById(R.id.article_fragment)).setLayoutTransition(new LayoutTransition());
		} */
	}
	
	@Override
	protected void refresh() {
		super.refresh();
		
		
	}
	
	@Override
	protected void loginSuccess(boolean refresh) {
		Log.d(TAG, "loginSuccess");
		
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		
		initMenu();
		
		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		GlobalState.getInstance().save(out);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			overridePendingTransition(0, R.anim.right_slide_out);
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	protected void initMenu() {
		super.initMenu();

		if (m_menu != null && getSessionId() != null) {
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);

			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait()&& hf != null && hf.getSelectedArticles().size() == 0);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, !isPortrait() && hf != null && hf.getSelectedArticles().size() != 0);
			
			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null);
			
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
			
			m_menu.findItem(R.id.search).setVisible(false);
		}		
	}
	
	@Override
	public void onArticleListSelectionChange(ArticleList m_selectedArticles) {
		initMenu();
	}

	@Override
	public void onArticleSelected(Article article) {
		onArticleSelected(article, true);
	}

	@Override
	public void onArticleSelected(Article article, boolean open) {
		
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}

		if (open) {
			
			final Article fArticle = article;
			
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
					
					if (af != null) {
						af.setActiveArticle(fArticle);
					}
				}
			}, 10);			

		} else {
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			hf.setActiveArticle(article);
		}

		GlobalState.getInstance().m_activeArticle = article;
		
		initMenu();
		
	}

	@Override
	public void onHeadlinesLoaded(boolean appended) {
		HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		
		if (hf != null) {
			Article article = hf.getActiveArticle();
						
			if (article == null) {
				article = hf.getAllArticles().get(0);

				hf.setActiveArticle(article);

				FragmentTransaction ft = getSupportFragmentManager()
						.beginTransaction();

				ft.replace(R.id.article_fragment, new LoadingFragment(), null);
				
				ft.commit();
				
				final Article fArticle = article;
				final Feed fFeed = hf.getFeed();
				
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						FragmentTransaction ft = getSupportFragmentManager()
								.beginTransaction();

						ArticlePager af = new ArticlePager(fArticle, fFeed);

						ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
						ft.commit();
					}
				}, 10);				
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		overridePendingTransition(0, R.anim.right_slide_out);
	}
}
