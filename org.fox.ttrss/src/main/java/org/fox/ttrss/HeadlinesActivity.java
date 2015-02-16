package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

public class HeadlinesActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	protected ArticleList m_articles = new ArticleList();

	protected SharedPreferences m_prefs;
    private Article m_activeArticle;

    @SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

		setContentView(R.layout.headlines_articles);

        m_forceDisableActionMode = isPortrait() || isSmallScreen();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);
		
		GlobalState.getInstance().load(savedInstanceState);

        if (isPortrait() && !isSmallScreen()) {
            findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
        }

		if (savedInstanceState != null) {
            m_articles = savedInstanceState.getParcelable("articles");
        } else {
			Intent i = getIntent();
			
			if (i.getExtras() != null) {
				boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);
				
				Log.d(TAG, "is_shortcut_mode: " + shortcutMode);
				
				Feed tmpFeed;
				
				if (shortcutMode) {
					int feedId = i.getIntExtra("feed_id", 0);
					boolean isCat = i.getBooleanExtra("feed_is_cat", false);
					String feedTitle = i.getStringExtra("feed_title");
					
					tmpFeed = new Feed(feedId, feedTitle, isCat);

					//GlobalState.getInstance().m_loadedArticles.clear();
				} else {
					tmpFeed = i.getParcelableExtra("feed");
				}
				
				final Feed feed = tmpFeed;
				
				final Article article = i.getParcelableExtra("article");
				final String searchQuery = i.getStringExtra("searchQuery");

                ArticleList tmp = GlobalState.getInstance().tmpArticleList;

                if (tmp != null) {
                    m_articles.addAll(tmp);
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                final HeadlinesFragment hf = new HeadlinesFragment();
                hf.initialize(feed, article, true, m_articles);
                hf.setSearchQuery(searchQuery);

                ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
                ft.replace(R.id.article_fragment, new LoadingFragment(), null);

                ft.commit();
				
				setTitle(feed.title);

				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

						ArticlePager af = new ArticlePager();
						af.initialize(article != null ? hf.getArticleById(article.id) : new Article(), feed, m_articles);
						af.setSearchQuery(searchQuery);

						ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
						
						ft.commit();
					 }
				 }, 100);
			}
		}
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isSmallScreen()) {
            findViewById(R.id.headlines_fragment).setVisibility(isPortrait() ? View.GONE : View.VISIBLE);
        }

        m_forceDisableActionMode = isPortrait() || isSmallScreen();
        invalidateOptionsMenu();
    }

	@Override
	protected void refresh() {
		super.refresh();
		
		
	}
	
	@Override
	protected void loginSuccess(boolean refresh) {
		Log.d(TAG, "loginSuccess");

		invalidateOptionsMenu();
		
		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

        out.putParcelable("articles", m_articles);

		GlobalState.getInstance().save(out);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
		case android.R.id.home:
            onBackPressed();
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

			//HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait() && !isSmallScreen());
			//m_menu.findItem(R.id.headlines_toggle_sidebar).setVisible(!isPortrait() && !isSmallScreen());
			
			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null);
			
			if (af != null) {
				if (af.getSelectedArticle() != null && af.getSelectedArticle().attachments != null && af.getSelectedArticle().attachments.size() > 0) {
					/* if (!isCompatMode() && (isSmallScreen() || !isPortrait())) {
						m_menu.findItem(R.id.toggle_attachments).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
					} */
					m_menu.findItem(R.id.toggle_attachments).setVisible(true);
				} else {
					/* if (!isCompatMode()) {
						m_menu.findItem(R.id.toggle_attachments).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
					} */
					m_menu.findItem(R.id.toggle_attachments).setVisible(false);
				}
			}
			
			m_menu.findItem(R.id.search).setVisible(false);
		}		
	}
	
	@Override
	public void onArticleListSelectionChange(ArticleList m_selectedArticles) {
		invalidateOptionsMenu();
	}

	@Override
	public void onArticleSelected(Article article) {
		onArticleSelected(article, true);
	}

	@Override
	public void onArticleSelected(Article article, boolean open) {
		
		if (article == null) return;
		
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
			if (hf != null) {
				hf.setActiveArticle(article);
			}
		}

        m_activeArticle = article;

		//GlobalState.getInstance().m_activeArticle = article;
		
		invalidateOptionsMenu();
		
	}

    public void showSidebar(boolean show) {
        if (!isSmallScreen() && !isPortrait()) {
            findViewById(R.id.headlines_fragment).setVisibility(show ? View.VISIBLE : View.GONE);
            invalidateOptionsMenu();
        }
    }

	@Override
	public void onHeadlinesLoaded(boolean appended) {
		HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

        if (ap != null) {
            ap.notifyUpdated();
        }

		if (hf != null) {
			Article article = hf.getActiveArticle();
						
			if (article == null && hf.getAllArticles().size() > 0) {
				article = hf.getAllArticles().get(0);

				hf.setActiveArticle(article);

				FragmentTransaction ft = getSupportFragmentManager()
						.beginTransaction();

				ft.replace(R.id.article_fragment, new LoadingFragment(), null);
				
				ft.commitAllowingStateLoss();
				
				final Article fArticle = article;
				final Feed fFeed = hf.getFeed();
				
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						FragmentTransaction ft = getSupportFragmentManager()
								.beginTransaction();

						ArticlePager af = new ArticlePager();
						af.initialize(fArticle, fFeed, m_articles);

						ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
						ft.commitAllowingStateLoss();
					}
				}, 10);				
			}
		}
	}
	
	@Override
	public void onBackPressed() {
        Intent resultIntent = new Intent();

        GlobalState.getInstance().tmpArticleList = m_articles;
        resultIntent.putExtra("activeArticle", m_activeArticle);

        setResult(Activity.RESULT_OK, resultIntent);

        super.onBackPressed();
    }
}
