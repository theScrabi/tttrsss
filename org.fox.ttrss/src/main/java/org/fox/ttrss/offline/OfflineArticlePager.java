package org.fox.ttrss.offline;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.viewpagerindicator.UnderlinePageIndicator;

import org.fox.ttrss.R;

public class OfflineArticlePager extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	private PagerAdapter m_adapter;
	private OfflineActivity m_activity;
	private OfflineHeadlinesEventListener m_listener;
	private boolean m_isCat;
	private int m_feedId;
	private int m_articleId;
	private String m_searchQuery = "";
	private Cursor m_cursor;
	private SharedPreferences m_prefs;
	
	public int getFeedId() {
		return m_feedId;
	}
	
	public boolean getFeedIsCat() {
		return m_isCat;
	}
	
	public Cursor createCursor() {
		String feedClause = null;
		
		if (m_isCat) {
			feedClause = "feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)";
		} else {
			feedClause = "feed_id = ?";
		}
		
		String viewMode = m_activity.getViewMode();
		
		if ("adaptive".equals(viewMode)) {
			// TODO: implement adaptive			
		} else if ("marked".equals(viewMode)) {
			feedClause += "AND (marked = 1)";
		} else if ("published".equals(viewMode)) {
			feedClause += "AND (published = 1)";
		} else if ("unread".equals(viewMode)) {
			feedClause += "AND (unread = 1)";
		} else { // all_articles
			//
		}
		
		String orderBy = (m_prefs.getBoolean("offline_oldest_first", false)) ? "updated" : "updated DESC";
		
		if (m_searchQuery == null || m_searchQuery.equals("")) {
			return m_activity.getDatabase().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles."+BaseColumns._ID, "feeds.title AS feed_title" }, feedClause, 
					new String[] { String.valueOf(m_feedId) }, null, null, orderBy);
		} else {
			return m_activity.getDatabase().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles."+BaseColumns._ID },
					feedClause + " AND (articles.title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%')", 
					new String[] { String.valueOf(m_feedId), m_searchQuery, m_searchQuery }, null, null, orderBy);
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}
	
	private class PagerAdapter extends FragmentStatePagerAdapter {
		public PagerAdapter(FragmentManager fm) {
			super(fm);
		}

		// workaround for possible TransactionTooLarge exception on 8.0+
		// we don't need to save member state anyway, bridge takes care of it
		@Override
		public Parcelable saveState() {
			Bundle bundle = (Bundle) super.saveState();

			if (bundle != null)
				bundle.putParcelableArray("states", null); // Never maintain any states from the base class, just null it out

			return bundle;
		}

		@Override
		public Fragment getItem(int position) {
			Log.d(TAG, "getItem: " + position);
			
			if (m_cursor.moveToPosition(position)) {

				OfflineArticleFragment oaf = new OfflineArticleFragment();
				oaf.initialize(m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID)));
				
				return oaf;
			} 
			
			return null; 
		}
		
		@Override
		public int getCount() {
			return m_cursor.getCount();
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	public void initialize(int articleId, int feedId, boolean isCat) {
		m_feedId = feedId;
		m_isCat = isCat;
		m_articleId = articleId;

	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.article_pager, container, false);
	
		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId", 0);
			m_feedId = savedInstanceState.getInt("feedId", 0);
			m_isCat = savedInstanceState.getBoolean("isCat", false);
		}
		
		Log.d(TAG, "feed=" + m_feedId + "; iscat=" + m_isCat);
		
		m_cursor = createCursor();
		
		m_adapter = new PagerAdapter(getActivity().getSupportFragmentManager());
		
		int position = 0;
		
		Log.d(TAG, "maId=" + m_articleId);
		
		if (m_articleId != 0) {
			if (m_cursor.moveToFirst()) {
				
				while (!m_cursor.isAfterLast()) {
					if (m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID)) == m_articleId) {
						position = m_cursor.getPosition();
						break;
					}				
					m_cursor.moveToNext();
				}
				
				Log.d(TAG, "(1)maId=" + m_articleId);
				m_listener.onArticleSelected(m_articleId, false);
			}
		} else {
			if (m_cursor.moveToFirst()) {
				m_articleId = m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID));
				m_listener.onArticleSelected(m_articleId, false);
				
				Log.d(TAG, "(2)maId=" + m_articleId);
			}
		}
		
		
		ViewPager pager = view.findViewById(R.id.article_pager);
		
		pager.setAdapter(m_adapter);

        UnderlinePageIndicator indicator = view.findViewById(R.id.article_pager_indicator);
        indicator.setViewPager(pager);

        pager.setCurrentItem(position);

		indicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

			@Override
			public void onPageScrollStateChanged(int arg0) {
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageSelected(int position) {
				if (m_cursor.moveToPosition(position)) {
					int articleId = m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID));
					
					m_articleId = articleId;
					m_listener.onArticleSelected(articleId, false);
				}
			}
		});

		return view;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_activity = (OfflineActivity)activity;
		m_listener = (OfflineHeadlinesEventListener)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
			
	}
	
	public void refresh() {
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
		
		m_cursor = createCursor();
		
		if (m_cursor != null) {
			m_adapter.notifyDataSetChanged();
		}
	}

	public int getSelectedArticleId() {
		return m_articleId;
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("articleId", m_articleId);
		out.putInt("feedId", m_feedId);
		out.putBoolean("isCat", m_isCat);
		
	}

	public void setSearchQuery(String searchQuery) {
		m_searchQuery = searchQuery;
	}

	public void setArticleId(int articleId) {
		m_articleId = articleId;		
		
		int position = getArticleIdPosition(articleId);
		
		ViewPager pager = getView().findViewById(R.id.article_pager);
		
		pager.setCurrentItem(position);
		
	}
	
	public int getArticleIdPosition(int articleId) {
		m_cursor.moveToFirst();
		
		while (!m_cursor.isAfterLast()) {
			if (m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID)) == articleId) {
				return m_cursor.getPosition();
			}				
			m_cursor.moveToNext();
		}
		
		return -1;
	}

	public void selectArticle(boolean next) {
		int position = getArticleIdPosition(m_articleId);
		
		if (position != -1) {
			if (next) 
				position++;
			else
				position--;
	
			Log.d(TAG, "pos=" + position);
			
			if (m_cursor.moveToPosition(position)) {
				setArticleId(m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID)));			
			}
		}
	}
}
