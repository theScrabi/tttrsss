package org.fox.ttrss.offline;

import org.fox.ttrss.GlobalState;
import org.fox.ttrss.R;

import com.actionbarsherlock.view.MenuItem;

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
import android.util.Log;
import android.view.View;

public class OfflineHeadlinesActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);
		
		super.onCreate(savedInstanceState);

		setContentView(R.layout.headlines_articles);
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null); 
		
		if (isPortrait()) {
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
				hf.initialize(feedId, isCat);
				
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
		
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		
		initMenu();
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
	public void onArticleSelected(int articleId, boolean open) {
		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
						+ " = ?");

		stmt.bindLong(1, articleId);
		stmt.execute();
		stmt.close();
		
		if (open) {
			OfflineArticlePager af = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			af.setArticleId(articleId);
		} else {
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			hf.setActiveArticleId(articleId);
		}
		
		GlobalState.getInstance().m_selectedArticleId = articleId;
		
		initMenu();
		refresh();
	}
	
	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null) {
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);

			//OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait() && !isSmallScreen());			
			
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
		super.onBackPressed();
		overridePendingTransition(0, R.anim.right_slide_out);
	}
}
