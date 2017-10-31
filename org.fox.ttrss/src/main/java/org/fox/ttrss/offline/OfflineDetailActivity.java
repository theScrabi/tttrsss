package org.fox.ttrss.offline;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import org.fox.ttrss.Application;
import org.fox.ttrss.R;

public class OfflineDetailActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;

    private ActionBarDrawerToggle m_drawerToggle;
    private DrawerLayout m_drawerLayout;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);
		
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_detail);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

        m_drawerLayout = (DrawerLayout) findViewById(R.id.headlines_drawer);

        if (m_drawerLayout != null) {

            m_drawerToggle = new ActionBarDrawerToggle(this, m_drawerLayout, R.string.blank, R.string.blank) {
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);

                    getSupportActionBar().show();

                    invalidateOptionsMenu();
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);

                    invalidateOptionsMenu();
                }
            };

            m_drawerLayout.setDrawerListener(m_drawerToggle);
            m_drawerToggle.setDrawerIndicatorEnabled(true);

        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);

		if (isPortrait() && !isSmallScreen()) {
			findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
		}

		if (savedInstanceState == null) {
			Intent i = getIntent();
			
			if (i.getExtras() != null) {
				int feedId = i.getIntExtra("feed", 0);
				boolean isCat = i.getBooleanExtra("isCat", false);
				int articleId = i.getIntExtra("article", 0);
				String searchQuery = i.getStringExtra("searchQuery");
				
				OfflineHeadlinesFragment hf = new OfflineHeadlinesFragment();
				hf.initialize(feedId, isCat, true);
				
				OfflineArticlePager af = new OfflineArticlePager();
				af.initialize(articleId, feedId, isCat);

				hf.setActiveArticleId(articleId);
				
				hf.setSearchQuery(searchQuery);
				af.setSearchQuery(searchQuery);
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
				ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
				
				ft.commit();

				Cursor c;
				
				if (isCat) {
					c = getCatById(feedId);					
				} else {
					c = getFeedById(feedId);
				}
				
				if (c != null) {
					setTitle(c.getString(c.getColumnIndex("title")));
					c.close();
				}

			}
		} 
	}

    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (m_drawerToggle != null) m_drawerToggle.syncState();
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        if (m_drawerToggle != null && m_drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onArticleSelected(int articleId, boolean open) {
		
		if (!open) {
			SQLiteStatement stmt = getDatabase().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
							+ " = ?");
		
			stmt.bindLong(1, articleId);
			stmt.execute();
			stmt.close();
		}
		
		if (open) {
            if (m_drawerLayout != null) {
                m_drawerLayout.closeDrawers();
            }

            OfflineArticlePager af = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			af.setArticleId(articleId);
		} else {
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			hf.setActiveArticleId(articleId);
		}
		
		Application.getInstance().m_selectedArticleId = articleId;
		
		invalidateOptionsMenu();
		refresh();
	}
	
	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null) {
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);

			//OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait() && !isSmallScreen());
			//m_menu.findItem(R.id.headlines_toggle_sidebar).setVisible(!isPortrait() && !isSmallScreen());
			
			Fragment af = getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null);
			
			m_menu.findItem(R.id.search).setVisible(false);
		}		
	}

	@Override
	public void onArticleSelected(int articleId) {
		onArticleSelected(articleId, true);		
	}

	@Override
	public void onBackPressed() {
		try {
			super.onBackPressed();
		} catch (IllegalStateException e) {
			// java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
			e.printStackTrace();
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		if (isFinishing()) {
			overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
		}

	}

	public void showSidebar(boolean show) {
		if (!isSmallScreen() && !isPortrait()) {
			findViewById(R.id.headlines_fragment).setVisibility(show ? View.VISIBLE : View.GONE);
			invalidateOptionsMenu();
		}
	}

}
