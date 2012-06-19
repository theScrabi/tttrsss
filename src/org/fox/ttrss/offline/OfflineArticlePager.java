package org.fox.ttrss.offline;

import org.fox.ttrss.R;
import org.fox.ttrss.R.id;
import org.fox.ttrss.R.layout;
import org.fox.ttrss.util.FragmentStatePagerAdapter;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class OfflineArticlePager extends Fragment {

	private PagerAdapter m_adapter;
	private OfflineServices m_offlineServices;
	private OfflineHeadlinesFragment m_hf;
	private int m_articleId;
	
	private class PagerAdapter extends FragmentStatePagerAdapter {
		
		public PagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			int articleId = m_hf.getArticleIdAtPosition(position);
			
			if (articleId != 0) {
				return new OfflineArticleFragment(articleId);
			} 
			
			return null; 
		}

		@Override
		public int getCount() {
			return m_hf.getArticleCount();
		}
		
	}
	
	public OfflineArticlePager() {
		super();
	}
	
	public OfflineArticlePager(int articleId) {
		super();

		m_articleId = articleId;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.article_pager, container, false);
	
		m_adapter = new PagerAdapter(getActivity().getFragmentManager());
		
		ViewPager pager = (ViewPager) view.findViewById(R.id.article_pager);
		
		int position = m_hf.getArticleIdPosition(m_articleId);
		
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
				int articleId = m_hf.getArticleIdAtPosition(position);
				
				if (articleId != 0) {
					m_offlineServices.setSelectedArticleId(articleId);
					
					SQLiteStatement stmt = m_offlineServices.getWritableDb().compileStatement(
							"UPDATE articles SET unread = 0 " + "WHERE " + BaseColumns._ID
									+ " = ?");
	
					stmt.bindLong(1, articleId);
					stmt.execute();
					stmt.close();
				}
			}
		});
	
		return view;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_hf = (OfflineHeadlinesFragment) getActivity().getFragmentManager().findFragmentById(R.id.headlines_fragment);
		m_offlineServices = (OfflineServices)activity;
	}

}
