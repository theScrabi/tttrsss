package org.fox.ttrss.offline;

import org.fox.ttrss.GlobalState;
import org.fox.ttrss.R;

import com.actionbarsherlock.view.MenuItem;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class OfflineFeedsActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
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
			m_slidingMenu.setSlidingEnabled(true);
			m_slidingMenu.setMenu(R.layout.feeds);
			m_slidingMenu.setOnOpenedListener(new SlidingMenu.OnOpenedListener() {
					
				@Override
				public void onOpened() {
					if (m_actionbarRevertDepth == 0) {
						m_actionbarUpEnabled = false;
						m_feedIsSelected = false;
						getSupportActionBar().setDisplayHomeAsUpEnabled(false);
						initMenu();
						refresh();
					}
				}
			});
		}

		if (savedInstanceState != null) {
			
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
			
		} else {
			if (m_slidingMenu != null)
				m_slidingMenu.showMenu();
			
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (m_prefs.getBoolean("enable_cats", false)) {
				ft.replace(R.id.feeds_fragment, new OfflineFeedCategoriesFragment(), FRAG_CATS);				
			} else {
				ft.replace(R.id.feeds_fragment, new OfflineFeedsFragment(), FRAG_FEEDS);
			}	
		
			ft.commit();
		}
		
		setLoadingStatus(R.string.blank, false);
		
		initMenu();

		if (!isCompatMode() && !isSmallScreen()) {
			((ViewGroup)findViewById(R.id.headlines_fragment)).setLayoutTransition(new LayoutTransition());
			((ViewGroup)findViewById(R.id.feeds_fragment)).setLayoutTransition(new LayoutTransition());
		}
	}

	public void openFeedArticles(int feedId, boolean isCat) {
		if (isSmallScreen()) {
			Intent intent = new Intent(OfflineFeedsActivity.this, OfflineHeadlinesActivity.class);
			
			intent.putExtra("feed", feedId);
			intent.putExtra("isCat", isCat);
			intent.putExtra("article", 0);
			startActivityForResult(intent, 0);
		}
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
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
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
	
	public void initMenu() {
		super.initMenu();
		
		if (m_menu != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			if (m_slidingMenu != null) {
				m_menu.setGroupVisible(R.id.menu_group_feeds, m_slidingMenu.isMenuShowing());
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && !m_slidingMenu.isMenuShowing());
			} else {
				m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());				
			}
			
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
		OfflineFeedCategoriesFragment fc = (OfflineFeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		if (openAsFeed) {
			if (fc != null) {
				fc.setSelectedFeedId(catId);
			}

			onFeedSelected(catId, true, true);
		} else {
			if (fc != null) {
				fc.setSelectedFeedId(-1);
			}
			
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			OfflineFeedsFragment ff = new OfflineFeedsFragment();
			ff.initialize(catId);

			ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);
			ft.addToBackStack(null);

			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			m_actionbarUpEnabled = true;
			m_actionbarRevertDepth = m_actionbarRevertDepth + 1;
			
			ft.commit();
		}
	}
	
	public void onFeedSelected(int feedId) {
		onFeedSelected(feedId, false, true);		
	}

	public void onFeedSelected(final int feedId, final boolean isCat, boolean open) {
		
		if (open) {
			if (!isSmallScreen()) {
				LinearLayout container = (LinearLayout) findViewById(R.id.fragment_container);
				if (container != null) {
					container.setWeightSum(3f);
				}
			}
			
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					FragmentTransaction ft = getSupportFragmentManager()
							.beginTransaction();
					
					OfflineHeadlinesFragment hf = new OfflineHeadlinesFragment();
					hf.initialize(feedId, isCat);
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
		}		
	}

	@Override
	public void onArticleSelected(int articleId, boolean open) {
		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
						+ " = ?");

		stmt.bindLong(1, articleId);
		stmt.execute();
		stmt.close();
		
		initMenu();
		
		if (open) {
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			Intent intent = new Intent(OfflineFeedsActivity.this, OfflineHeadlinesActivity.class);		
			intent.putExtra("feed", hf.getFeedId());
			intent.putExtra("isCat", hf.getFeedIsCat());
			intent.putExtra("article", articleId);
	 	   
			startActivityForResult(intent, 0);

			overridePendingTransition(R.anim.right_slide_in, 0);
			
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
