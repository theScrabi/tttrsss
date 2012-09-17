package org.fox.ttrss.offline;

import org.fox.ttrss.HeadlinesFragment;
import org.fox.ttrss.R;

import android.content.Intent;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

public class OfflineFeedsActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
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
		
		if (savedInstanceState != null) {
			
		} else {
			Intent intent = getIntent();
			
			if (intent.getIntExtra("feed", -10000) != -10000 || intent.getIntExtra("category", -10000) != -10000 ||
					intent.getIntExtra("article", -10000) != -10000) {
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				int feedId = intent.getIntExtra("feed", -10000);
				int catId = intent.getIntExtra("category", -10000);
				int articleId = intent.getIntExtra("article", -10000);
				boolean isCat = intent.getBooleanExtra("isCat", false);
				
				if (articleId != -10000) {
					ft.replace(R.id.feeds_fragment, new OfflineArticlePager(articleId, feedId, isCat), FRAG_ARTICLE);
				} else {
					if (feedId != -10000) {
						ft.replace(R.id.feeds_fragment, new OfflineHeadlinesFragment(feedId, isCat), FRAG_HEADLINES);
					}

					if (catId != -10000) {
						ft.replace(R.id.feeds_fragment, new OfflineFeedsFragment(catId), FRAG_FEEDS);
					}
				}
				
				ft.commit();
			} else {			
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				if (m_prefs.getBoolean("enable_cats", false)) {
					ft.replace(R.id.feeds_fragment, new OfflineFeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.feeds_fragment, new OfflineFeedsFragment(), FRAG_FEEDS);
				}	
			
				ft.commit();
			}
		}
		
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		
		initMenu();
	}

	protected void refresh() {
		OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);

		if (ff != null) {
			ff.refresh();
		}

		OfflineFeedCategoriesFragment cf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		if (cf != null) {
			cf.refresh();
		}

		/* OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		if (ohf != null) {
			ohf.refresh();
		} */
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.show_feeds:
			m_unreadOnly = !m_unreadOnly;
			initMenu();
			refresh();
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

	}
	
	public void initMenu() {
		super.initMenu();
		
		if (m_menu != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			OfflineArticlePager af = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null && af.isAdded());

			m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && getSelectedArticleCount() == 0);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, hf != null && hf.isAdded() && getSelectedArticleCount() != 0);
			
			MenuItem item = m_menu.findItem(R.id.show_feeds);

			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_all_feeds);
			} else {
				item.setTitle(R.string.menu_unread_feeds);
			}
		}		
	}
	
	public void onCatSelected(int catId) {
		onCatSelected(catId, m_prefs.getBoolean("browse_cats_like_feeds", false));	
	}
	
	public void onCatSelected(int catId, boolean openAsFeed) {
		FragmentTransaction ft = getSupportFragmentManager()
				.beginTransaction();
		
		if (openAsFeed) {
			onFeedSelected(catId, true, true);
		} else {
			if (isSmallScreen()) {
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);		
				intent.putExtra("category", catId);

				startActivityForResult(intent, 0);		
			} else {
				OfflineFeedsFragment ff = new OfflineFeedsFragment(catId);
				
				ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);
			}
		}
		ft.addToBackStack(null);

		ft.commit();

	}
	
	public void onFeedSelected(int feedId) {
		onFeedSelected(feedId, false, true);		
	}

	public void onFeedSelected(int feedId, boolean isCat, boolean open) {
		
		if (open) {
			if (isSmallScreen()) {
				
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);		
				intent.putExtra("feed", feedId);
				intent.putExtra("isCat", isCat);
		 	   
				startActivityForResult(intent, 0);		
				
			} else {

				// TODO open OfflineHeadlinesFragment on R.id.headlines_fragment
				
			}
		}		
	}

	public void catchupFeed(int feedId, boolean isCat) {
		if (isCat) {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET unread = 0 WHERE feed_id IN (SELECT "+
						BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();
		} else {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET unread = 0 WHERE feed_id = ?");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();			
		}
		
		refresh();
	}

	@Override
	public void onArticleSelected(int articleId, boolean open) {
		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET unread = 0 " + "WHERE " + BaseColumns._ID
						+ " = ?");

		stmt.bindLong(1, articleId);
		stmt.execute();
		stmt.close();
		
		if (open) {
			if (isSmallScreen()) {

				OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
								
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);		
				intent.putExtra("feed", hf.getFeedId());
				intent.putExtra("isCat", hf.getFeedIsCat());
				intent.putExtra("article", articleId);
		 	   
				startActivityForResult(intent, 0);		
			
			} else {

				// TODO open OfflineHeadlinesActivity
				
				
			}			
		} else {
			refresh();
		}

	}

	@Override
	public void onArticleSelected(int articleId) {
		onArticleSelected(articleId, true);
	}
}
