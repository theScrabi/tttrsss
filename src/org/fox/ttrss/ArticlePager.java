package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ArticlePager extends Fragment {

	private final String TAG = "ArticlePager";
	private PagerAdapter m_adapter;
	private HeadlinesEventListener m_onlineServices;
	private Article m_article;
	private ArticleList m_articles;
	
	private class PagerAdapter extends FragmentStatePagerAdapter {
		
		public PagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			Article article = m_articles.get(position);
			
			if (article != null) {
				ArticleFragment af = new ArticleFragment(article);
				return af;
			}
			return null;
		}

		@Override
		public int getCount() {
			return m_articles.size();
		}
		
	}
	
	public ArticlePager() {
		super();
	}
	
	public ArticlePager(Article article) {
		super();
		
		m_article = article;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.article_pager, container, false);
	
		if (savedInstanceState != null) {
			m_articles = savedInstanceState.getParcelable("articles");
			m_article = savedInstanceState.getParcelable("article");
		}
		
		m_adapter = new PagerAdapter(getActivity().getSupportFragmentManager());
		
		ViewPager pager = (ViewPager) view.findViewById(R.id.article_pager);
		
		int position = m_articles.indexOf(m_article);
		
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
				Article article = m_articles.get(position);
				
				if (article != null) {
					m_article = article;
					
					if (article.unread) {
						article.unread = false;
						m_onlineServices.saveArticleUnread(article);
					}
					m_onlineServices.onArticleSelected(article, false);
					
					//Log.d(TAG, "Page #" + position + "/" + m_adapter.getCount());
					
					if (position == m_adapter.getCount() - 5) {
						// FIXME load more articles somehow
						//m_hf.refresh(true);
						m_adapter.notifyDataSetChanged();
					}
				}
			}
		});
	
		return view;
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putParcelable("articles", m_articles);
		out.putParcelable("article", m_article);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_onlineServices = (HeadlinesEventListener)activity;
		
		m_articles = TinyApplication.getInstance().m_articles;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		((OnlineActivity)getActivity()).initMenu();
	}

	public Article getSelectedArticle() {
		return m_article;
	}

}
