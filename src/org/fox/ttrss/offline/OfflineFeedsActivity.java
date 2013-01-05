package org.fox.ttrss.offline;

import org.fox.ttrss.GlobalState;
import org.fox.ttrss.R;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class OfflineFeedsActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	private boolean m_actionbarUpEnabled = false;
	
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
		
		GlobalState.getInstance().load(savedInstanceState);
		
		if (savedInstanceState != null) {
			
			m_actionbarUpEnabled = savedInstanceState.getBoolean("actionbarUpEnabled");
			
			if (!isCompatMode() && m_actionbarUpEnabled) {
				getActionBar().setDisplayHomeAsUpEnabled(true);
			}
			
		} else {
			Intent intent = getIntent();
			
			if (intent.getIntExtra("feed", -10000) != -10000 || intent.getIntExtra("category", -10000) != -10000 ||
					intent.getIntExtra("article", -10000) != -10000) {
				
				if (!isCompatMode()) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
					m_actionbarUpEnabled = true;
				}
				
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

		/* if (!isSmallScreen()) {
			LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
			container.setWeightSum(3f);
		} */
		
		if (!isCompatMode() && !isSmallScreen()) {
			((ViewGroup)findViewById(R.id.headlines_fragment)).setLayoutTransition(new LayoutTransition());
			((ViewGroup)findViewById(R.id.feeds_fragment)).setLayoutTransition(new LayoutTransition());
		}
	}

	public void openFeedArticles(int feedId, boolean isCat) {
		if (isSmallScreen()) {
			Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);
			
			intent.putExtra("feed", feedId);
			intent.putExtra("isCat", isCat);
			intent.putExtra("article", 0);
			startActivityForResult(intent, 0);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());
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

		out.putBoolean("actionbarUpEnabled", m_actionbarUpEnabled);
		
		GlobalState.getInstance().save(out);
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
		if (openAsFeed) {
			onFeedSelected(catId, true, true);
		} else {
			if (isSmallScreen()) {
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);		
				intent.putExtra("category", catId);

				startActivityForResult(intent, 0);		
			} else {
				FragmentTransaction ft = getSupportFragmentManager()
						.beginTransaction();

				OfflineFeedsFragment ff = new OfflineFeedsFragment(catId);

				ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);
				ft.addToBackStack(null);
				
				ft.commit();
			}
		}
	}
	
	public void onFeedSelected(int feedId) {
		onFeedSelected(feedId, false, true);		
	}

	public void onFeedSelected(final int feedId, final boolean isCat, boolean open) {
		
		if (open) {
			if (isSmallScreen()) {
				
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineFeedsActivity.class);		
				intent.putExtra("feed", feedId);
				intent.putExtra("isCat", isCat);
		 	   
				startActivityForResult(intent, 0);		
				
			} else {
				/* if (!isCompatMode()) {
					LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
					float wSum = container.getWeightSum();
					if (wSum <= 2.0f) {
						ObjectAnimator anim = ObjectAnimator.ofFloat(container, "weightSum", wSum, 3.0f);
						anim.setDuration(200);
						anim.start();
					}
				} */
				
				// ^ no idea why the animation hangs half the time :(
				
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				container.setWeightSum(3f);
				
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						FragmentTransaction ft = getSupportFragmentManager()
								.beginTransaction();
						
						OfflineHeadlinesFragment hf = new OfflineHeadlinesFragment(feedId, isCat);
						ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
						
						ft.commit();
					}
				}, 10);
				
			}
		}		
	}

	public void catchupFeed(int feedId, boolean isCat) {
		if (isCat) {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id IN (SELECT "+
						BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();
		} else {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id = ?");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();			
		}
		
		refresh();
	}

	@Override
	public void onArticleSelected(int articleId, boolean open) {
		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
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

				OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				
				Intent intent = new Intent(OfflineFeedsActivity.this, OfflineHeadlinesActivity.class);		
				intent.putExtra("feed", hf.getFeedId());
				intent.putExtra("isCat", hf.getFeedIsCat());
				intent.putExtra("article", articleId);
		 	   
				startActivityForResult(intent, 0);	
			}			
		} else {
			refresh();
		}
		
		initMenu();

	}

	@Override
	public void onArticleSelected(int articleId) {
		onArticleSelected(articleId, true);
	}
}
