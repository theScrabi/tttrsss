package org.fox.ttrss;

import java.util.HashMap;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesRequest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

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
	private SharedPreferences m_prefs;
	
	private class PagerAdapter extends FragmentStatePagerAdapter {
		
		public PagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			Article article = m_articles.get(position);
			
			if (article != null) {
				ArticleFragment af = new ArticleFragment(article);

				if (m_prefs.getBoolean("dim_status_bar", false) && getView() != null && !m_activity.isCompatMode()) {
					getView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
				}
				
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
		
		m_listener.onArticleSelected(m_article, false);
		
		m_activity.setProgressBarVisibility(true);
		
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
					
					/* if (article.unread) {
						article.unread = false;
						m_activity.saveArticleUnread(article);
					} */
					
					m_listener.onArticleSelected(article, false);
					
					//Log.d(TAG, "Page #" + position + "/" + m_adapter.getCount());
					
					if ((m_activity.isSmallScreen() || m_activity.isPortrait()) && position == m_adapter.getCount() - 5) {
						Log.d(TAG, "loading more articles...");
						refresh(true);
					}
				}
			}
		});
	
		return view;
	}
	
	@SuppressWarnings({ "unchecked", "serial" }) 
	protected void refresh(boolean append) {
		m_activity.setLoadingStatus(R.string.blank, true);

		m_activity.setProgressBarVisibility(true);
		
		if (!m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			append = false;
		}
		
		HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity) {
			@Override
			protected void onProgressUpdate(Integer... progress) {
				m_activity.setProgress(progress[0] / progress[1] * 10000);
			}

			@Override
			protected void onPostExecute(JsonElement result) {
				if (isDetached()) return;
				
				m_activity.setProgressBarVisibility(false);

				super.onPostExecute(result);
				
				if (result != null) {
					try {
						m_adapter.notifyDataSetChanged();
					} catch (BadParcelableException e) {
						if (getActivity() != null) {							
							getActivity().finish();
							return;
						}
					}
					
					if (m_article.id == 0 || m_articles.indexOf(m_article) == -1) {
						if (m_articles.size() > 0) {
							m_article = m_articles.get(0);
							m_listener.onArticleSelected(m_article, false);
						}
					}
					
				} else {
					if (m_lastError == ApiError.LOGIN_FAILED) {
						m_activity.login(true);
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
				put("view_mode", m_activity.getViewMode());
				put("skip", String.valueOf(fskip));
				put("include_nested", "true");
				put("order_by", m_prefs.getBoolean("oldest_first", false) ? "date_reverse" : "");
				
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
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onResume() {
		super.onResume();
		
		if (m_articles.size() == 0 || !m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			refresh(false);
			GlobalState.getInstance().m_activeFeed = m_feed;
		}
		
		m_activity.initMenu();
		
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

	public Article getSelectedArticle() {
		return m_article;
	}

	public void setActiveArticle(Article article) {
		if (m_article != article) {
			m_article = article;

			int position = m_articles.indexOf(m_article);

			ViewPager pager = (ViewPager) getView().findViewById(R.id.article_pager);
		
			pager.setCurrentItem(position);
		}
	}

	public void selectArticle(boolean next) {
		if (m_article != null) {
			int position = m_articles.indexOf(m_article);
			
			if (next) 
				position++;
			else
				position--;
			
			try {
				Article tmp = m_articles.get(position);
				
				if (tmp != null) {
					setActiveArticle(tmp);
				}
				
			} catch (IndexOutOfBoundsException e) {
				// do nothing
			}
		}		
	}
}
