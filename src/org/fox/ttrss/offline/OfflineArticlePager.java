package org.fox.ttrss.offline;

import org.fox.ttrss.R;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
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
import android.view.WindowManager;

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
		
		String orderBy = (m_prefs.getBoolean("offline_oldest_first", false)) ? "updated" : "updated DESC";
		
		if (m_searchQuery == null || m_searchQuery.equals("")) {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles."+BaseColumns._ID, "feeds.title AS feed_title" }, feedClause, 
					new String[] { String.valueOf(m_feedId) }, null, null, orderBy);
		} else {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
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

		@Override
		public Fragment getItem(int position) {
			Log.d(TAG, "getItem: " + position);
			
			if (m_cursor.moveToPosition(position)) {
				
				if (m_prefs.getBoolean("dim_status_bar", false) && getView() != null && !m_activity.isCompatMode()) {
					getView().setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
				}
				
				return new OfflineArticleFragment(m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID)));
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
		
		if (!m_activity.isCompatMode() && m_prefs.getBoolean("dim_status_bar", false)) {
			getView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
		}
		
		if (m_prefs.getBoolean("full_screen_mode", false)) {
			m_activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
			
			/* if (!m_activity.isCompatMode()) {
	            m_activity.getActionBar().hide();
	         } */
		}
	}
	
	public OfflineArticlePager() {
		super();
	}
	
	public OfflineArticlePager(int articleId, int feedId, boolean isCat) {
		super();

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
			}
		} else {
			if (m_cursor.moveToFirst()) {
				m_articleId = m_cursor.getInt(m_cursor.getColumnIndex(BaseColumns._ID));
				m_listener.onArticleSelected(m_articleId, false);
				
				Log.d(TAG, "maId=" + m_articleId);
			}
		}
		
		
		ViewPager pager = (ViewPager) view.findViewById(R.id.article_pager);
		
		pager.setAdapter(m_adapter);
		pager.setCurrentItem(position);
		pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

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

					m_listener.onArticleSelected(articleId, false);
					
					m_articleId = articleId;
					
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
		
		m_cursor = createCursor();
			
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
		
		ViewPager pager = (ViewPager) getView().findViewById(R.id.article_pager);
		
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
