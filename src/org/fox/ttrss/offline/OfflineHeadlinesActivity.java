package org.fox.ttrss.offline;

import org.fox.ttrss.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;

public class OfflineHeadlinesActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	
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
		
		if (savedInstanceState == null) {
			Intent i = getIntent();
			
			if (i.getExtras() != null) {
				int feedId = i.getIntExtra("feed", 0);
				boolean isCat = i.getBooleanExtra("isCat", false);
				int articleId = i.getIntExtra("article", 0);
				String searchQuery = i.getStringExtra("searchQuery");
				String title = i.getStringExtra("title");
				
				OfflineHeadlinesFragment hf = new OfflineHeadlinesFragment(feedId, isCat);				
				OfflineArticlePager af = new OfflineArticlePager(articleId, feedId, isCat);

				hf.setActiveArticleId(articleId);
				
				hf.setSearchQuery(searchQuery);
				af.setSearchQuery(searchQuery);
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
				ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
				
				ft.commit();
				
				setTitle(title);
			}
		} 
		
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		
		initMenu();
	}

	@Override
	public void onArticleSelected(int articleId, boolean open) {

		
	}

	@Override
	public void onArticleSelected(int articleId) {
		onArticleSelected(articleId, true);		
	}
}
