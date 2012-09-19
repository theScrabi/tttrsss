package org.fox.ttrss;

import java.util.HashMap;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesRequest;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.JsonElement;

public class ArticlePager extends Fragment {

	private final String TAG = "ArticlePager";
	private PagerAdapter m_adapter;
	private HeadlinesEventListener m_listener;
	private Article m_article;
	private ArticleList m_articles = GlobalState.getInstance().m_loadedArticles;
	private OnlineActivity m_activity;
	private String m_searchQuery = "";
	private Feed m_feed;
	
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
	
	public ArticlePager(Article article, Feed feed) {
		super();
				
		m_article = article;
		m_feed = feed;
	}

	public void setSearchQuery(String searchQuery) {
		m_searchQuery = searchQuery;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.article_pager, container, false);
	
		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
			m_feed = savedInstanceState.getParcelable("feed");
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
						m_activity.saveArticleUnread(article);
					}
					m_listener.onArticleSelected(article, false);
					
					//Log.d(TAG, "Page #" + position + "/" + m_adapter.getCount());
					
					if (m_activity.isSmallScreen() && position == m_adapter.getCount() - 5) {
						Log.d(TAG, "loading more articles...");
						refresh(true);
					}
				}
			}
		});
	
		return view;
	}
	
	@SuppressWarnings({ "unchecked", "serial" })
	private void refresh(boolean append) {
		m_activity.setLoadingStatus(R.string.blank, true);
		
		if (!m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			append = false;
		}
		
		HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity) {
			protected void onPostExecute(JsonElement result) {
				super.onPostExecute(result);
				
				if (result != null) {				
					m_adapter.notifyDataSetChanged();
				} else {
					if (m_lastError == ApiError.LOGIN_FAILED) {
						m_activity.login();
					} else {
						m_activity.toast(getErrorMessage());
						//setLoadingStatus(getErrorMessage(), false);
					}	
				}
			}
		};
		
		final Feed feed = m_feed;
		
		final String sessionId = m_activity.getSessionId();
		final boolean showUnread = m_activity.getUnreadArticlesOnly();
		int skip = 0;
		
		if (append) {
			for (Article a : m_articles) {
				if (a.unread) ++skip;
			}
			
			if (skip == 0) skip = m_articles.size();
		}
		
		final int fskip = skip;
		
		req.setOffset(skip);
		
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", sessionId);
				put("feed_id", String.valueOf(feed.id));
				put("show_content", "true");
				put("include_attachments", "true");
				put("limit", String.valueOf(HeadlinesFragment.HEADLINES_REQUEST_SIZE));
				put("offset", String.valueOf(0));
				put("view_mode", showUnread ? "adaptive" : "all_articles");
				put("skip", String.valueOf(fskip));
				put("include_nested", "true");
				
				if (feed.is_cat) put("is_cat", "true");
				
				if (m_searchQuery != null && m_searchQuery.length() != 0) {
					put("search", m_searchQuery);
					put("search_mode", "");
					put("match_on", "both");
				}
			}			 
		};

		req.execute(map);
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putParcelable("article", m_article);
		out.putParcelable("feed", m_feed);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_listener = (HeadlinesEventListener)activity;
		m_activity = (OnlineActivity)activity;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (m_articles.size() == 0 || !m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			refresh(false);
			GlobalState.getInstance().m_activeFeed = m_feed;
		} else {
			m_adapter.notifyDataSetChanged();
		}
		
		m_activity.initMenu();
	}

	public Article getSelectedArticle() {
		return m_article;
	}

	public void setActiveArticle(Article article) {
		m_article = article;

		int position = m_articles.indexOf(m_article);

		ViewPager pager = (ViewPager) getView().findViewById(R.id.article_pager);
		
		pager.setCurrentItem(position);
	}
}
