package org.fox.ttrss;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesRequest;
import org.jsoup.Jsoup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.JsonElement;

public class HeadlinesFragment extends Fragment implements OnItemClickListener, OnScrollListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;
	
	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private Article m_activeArticle;
	private String m_searchQuery = "";
	private boolean m_refreshInProgress = false;
	
	private SharedPreferences m_prefs;
	
	private ArticleListAdapter m_adapter;
	private ArticleList m_articles = GlobalState.getInstance().m_loadedArticles;
	private ArticleList m_selectedArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	
	private ImageGetter m_dummyGetter = new ImageGetter() {

		@SuppressWarnings("deprecation")
		@Override
		public Drawable getDrawable(String source) {
			return new BitmapDrawable();
		}
		
	};
	public ArticleList getSelectedArticles() {
		return m_selectedArticles;
	}
	
	public HeadlinesFragment(Feed feed) {
		m_feed = feed;
	}

	public HeadlinesFragment(Feed feed, Article activeArticle) {
		m_feed = feed;
		
		if (activeArticle != null) {
			m_activeArticle = getArticleById(activeArticle.id);
		}
	}

	public HeadlinesFragment() {
		//
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		
		switch (item.getItemId()) {
		case R.id.set_labels:
			if (true) {
				Article article = getArticleAtPosition(info.position);
			
				if (article != null) {
					m_activity.editArticleLabels(article);
				}
			}
			return true;
		case R.id.article_set_note:
			if (true) {
				Article article = getArticleAtPosition(info.position);
			
				if (article != null) {
					m_activity.editArticleNote(article);				
				}
			}
			return true;

		case R.id.headlines_article_link_copy:
			if (true) {
				Article article = getArticleAtPosition(info.position);
			
				if (article != null) {
					m_activity.copyToClipboard(article.link);
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (true) {
				ArticleList selected = getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.marked = !a.marked;

					m_activity.toggleArticlesMarked(selected);
					//updateHeadlines();
				} else {
					Article article = getArticleAtPosition(info.position);
					if (article != null) {
						article.marked = !article.marked;
						m_activity.saveArticleMarked(article);
						//updateHeadlines();
					}
				}
				m_adapter.notifyDataSetChanged();
			}
			return true;
		case R.id.selection_toggle_published:
			if (true) {
				ArticleList selected = getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.published = !a.published;

					m_activity.toggleArticlesPublished(selected);
					//updateHeadlines();
				} else {
					Article article = getArticleAtPosition(info.position);
					if (article != null) {
						article.published = !article.published;
						m_activity.saveArticlePublished(article);
						//updateHeadlines();
					}
				}
				m_adapter.notifyDataSetChanged();
			}
			return true;
		case R.id.selection_toggle_unread:
			if (true) {
				ArticleList selected = getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.unread = !a.unread;

					m_activity.toggleArticlesUnread(selected);
					//updateHeadlines();
				} else {
					Article article = getArticleAtPosition(info.position);
					if (article != null) {
						article.unread = !article.unread;
						m_activity.saveArticleUnread(article);
						//updateHeadlines();
					}
				}
				m_adapter.notifyDataSetChanged();
			}
			return true;
		case R.id.headlines_share_article:
			if (true) {
				Article article = getArticleAtPosition(info.position);
				if (article != null)
					m_activity.shareArticle(article);
			}
			return true;
		case R.id.catchup_above:
			if (true) {
				Article article = getArticleAtPosition(info.position);
				if (article != null) {
					ArticleList articles = getAllArticles();
					ArticleList tmp = new ArticleList();
					for (Article a : articles) {
						a.unread = false;
						tmp.add(a);
						if (article.id == a.id)
							break;
					}
					if (tmp.size() > 0) {
						m_activity.toggleArticlesUnread(tmp);
						//updateHeadlines();
					}
				}
				m_adapter.notifyDataSetChanged();
			}
			return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}

	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_context_menu, menu);
		
		if (m_selectedArticles.size() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			Article article = getArticleAtPosition(info.position);
			menu.setHeaderTitle(article.title);
			menu.setGroupVisible(R.id.menu_group_single_article, true);
		}
		
		menu.findItem(R.id.set_labels).setEnabled(m_activity.getApiLevel() >= 1);
		menu.findItem(R.id.article_set_note).setEnabled(m_activity.getApiLevel() >= 1);

		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feed = savedInstanceState.getParcelable("feed");
			//m_articles = savedInstanceState.getParcelable("articles");
			m_activeArticle = savedInstanceState.getParcelable("activeArticle");
			m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		ListView list = (ListView)view.findViewById(R.id.headlines);		
		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		list.setOnScrollListener(this);
		//list.setEmptyView(view.findViewById(R.id.no_headlines));
		registerForContextMenu(list);

		if (m_activity.isSmallScreen() || m_activity.isPortrait())
			view.findViewById(R.id.headlines_fragment).setPadding(0, 0, 0, 0);

		Log.d(TAG, "onCreateView, feed=" + m_feed);
		
		return view;    	
	}

	@Override
	public void onResume() {
		super.onResume();

		if (GlobalState.getInstance().m_activeArticle != null) {
			m_activeArticle = GlobalState.getInstance().m_activeArticle;
			GlobalState.getInstance().m_activeArticle = null;
		}

		if (m_activeArticle != null) {
			setActiveArticle(m_activeArticle);
		}

		if (m_articles.size() == 0 || !m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			if (m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE) == null) {
				refresh(false);
				GlobalState.getInstance().m_activeFeed = m_feed;
			}			
		} else {
			notifyUpdated();
		}
		
		m_activity.initMenu();
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_activity = (OnlineActivity) activity;
		m_listener = (HeadlinesEventListener) activity;
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {
			Article article = (Article)list.getItemAtPosition(position);
			if (article.id >= 0) {
				m_listener.onArticleSelected(article);
				
				// only set active article when it makes sense (in HeadlinesActivity)
				if (getActivity().findViewById(R.id.article_fragment) != null) {
					m_activeArticle = article;
				}
				
				m_adapter.notifyDataSetChanged();
			}
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean append) {
		if (m_activity != null) {
			m_refreshInProgress = true;

			m_activity.setProgressBarVisibility(true);
			
			if (!m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
				append = false;
			}

			final boolean fappend = append;
			final String sessionId = m_activity.getSessionId();
			final boolean showUnread = m_activity.getUnreadArticlesOnly();
			final boolean isCat = m_feed.is_cat;
			
			HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity) {
				@Override
				protected void onProgressUpdate(Integer... progress) {
					m_activity.setProgress(Math.round((((float)progress[0] / (float)progress[1]) * 10000)));
				}

				@Override
				protected void onPostExecute(JsonElement result) {
					if (isDetached()) return;
					
					if (getView() != null) {
						ListView list = (ListView)getView().findViewById(R.id.headlines);
					
						if (list != null) {
							list.setEmptyView(getView().findViewById(R.id.no_headlines));
						}
					}
					
					m_activity.setProgressBarVisibility(false);
					
					super.onPostExecute(result);	
					
					if (result != null) {
						m_refreshInProgress = false;
						
						if (m_articles.indexOf(m_activeArticle) == -1)
							m_activeArticle = null;
						
						m_adapter.notifyDataSetChanged();
						m_listener.onHeadlinesLoaded(fappend);
					} else {
						if (m_lastError == ApiError.LOGIN_FAILED) {
							m_activity.login();
						} else {
							m_activity.setLoadingStatus(getErrorMessage(), false);
						}
					}
				}
			};
			
			int skip = 0;
			
			if (append) {
				for (Article a : m_articles) {
					if (a.unread) ++skip;
				}
				
				if (skip == 0) skip = m_articles.size();
			} else {
				m_activity.setLoadingStatus(R.string.blank, true);
			}
			
			final int fskip = skip;
			
			req.setOffset(skip);
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getHeadlines");
					put("sid", sessionId);
					put("feed_id", String.valueOf(m_feed.id));
					put("show_content", "true");
					put("include_attachments", "true");
					put("limit", String.valueOf(HEADLINES_REQUEST_SIZE));
					put("offset", String.valueOf(0));
					put("view_mode", showUnread ? "adaptive" : "all_articles");
					put("skip", String.valueOf(fskip));
					put("include_nested", "true");
					
					if (isCat) put("is_cat", "true");
					
					if (m_searchQuery != null && m_searchQuery.length() != 0) {
						put("search", m_searchQuery);
						put("search_mode", "");
						put("match_on", "both");
					}
				}			 
			};
	
			req.execute(map);
		}
	}		

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putParcelable("feed", m_feed);
		//out.putParcelable("articles", m_articles);
		out.putParcelable("activeArticle", m_activeArticle);
		out.putParcelable("selectedArticles", m_selectedArticles);
		out.putCharSequence("searchQuery", m_searchQuery);
	}

	/* private void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}
		}
		
		if (getActivity() != null)
			getActivity().setProgressBarIndeterminateVisibility(showProgress);
	} */
	
	/* private class HeadlinesRequest extends ApiRequest {
		int m_offset = 0;
		
		public HeadlinesRequest(Context context) {
			super(context);
		}
		
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonArray content = result.getAsJsonArray();
					if (content != null) {
						Type listType = new TypeToken<List<Article>>() {}.getType();
						final List<Article> articles = new Gson().fromJson(content, listType);
						
						while (m_articles.size() > HEADLINES_BUFFER_MAX)
							m_articles.remove(0);
						
						if (m_offset == 0)
							m_articles.clear();
						else
							m_articles.remove(m_articles.size()-1); // remove previous placeholder
						
						for (Article f : articles) 
							m_articles.add(f);

						if (articles.size() == HEADLINES_REQUEST_SIZE) {
							Article placeholder = new Article(-1);
							m_articles.add(placeholder);
							
							m_canLoadMore = true;
						} else {
							m_canLoadMore = false;
						}

						m_adapter.notifyDataSetChanged();

						if (m_articles.size() == 0)
							setLoadingStatus(R.string.no_headlines_to_display, false);
						else
							setLoadingStatus(R.string.blank, false);

						m_refreshInProgress = false;
						
						return;
					}
							
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}

			if (m_lastError == ApiError.LOGIN_FAILED) {
				m_activity.login();
			} else {
				setLoadingStatus(getErrorMessage(), false);
			}
			m_refreshInProgress = false;
	    }

		public void setOffset(int skip) {
			m_offset = skip;			
		}
	} */
	
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_LOADMORE = 3;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}
		
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);
			
			if (a.id == -1) {
				return VIEW_LOADMORE;
			} else if (m_activeArticle != null && a.id == m_activeArticle.id) {
				return VIEW_SELECTED;
			} else if (a.unread) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			final Article article = items.get(position);
			
			if (v == null) {
				int layoutId = R.layout.headlines_row;
				
				switch (getItemViewType(position)) {
				case VIEW_LOADMORE:
					layoutId = R.layout.headlines_row_loadmore;
					break;
				case VIEW_UNREAD:
					layoutId = R.layout.headlines_row_unread;
					break;
				case VIEW_SELECTED:
					layoutId = R.layout.headlines_row_selected;
					break;
				}
				
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutId, null);
				
				// http://code.google.com/p/android/issues/detail?id=3414
				((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			}

			TextView tt = (TextView)v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(Html.fromHtml(article.title));
			}

			TextView ft = (TextView)v.findViewById(R.id.feed_title);
			
			if (ft != null) {
				if (article.feed_title != null && (m_feed.is_cat || m_feed.id < 0)) {
					
					/* if (article.feed_title.length() > 20)
						ft.setText(article.feed_title.substring(0, 20) + "...");
					else */
					
					ft.setText(article.feed_title);
					
				} else {
					ft.setVisibility(View.GONE);
				}
				
			}
			
			ImageView marked = (ImageView)v.findViewById(R.id.marked);
			
			if (marked != null) {
				marked.setImageResource(article.marked ? android.R.drawable.star_on : android.R.drawable.star_off);
				
				marked.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.marked = !article.marked;
						m_adapter.notifyDataSetChanged();
						
						m_activity.saveArticleMarked(article);
					}
				});
			}
			
			ImageView published = (ImageView)v.findViewById(R.id.published);
			
			if (published != null) {
				published.setImageResource(article.published ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
				
				published.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.published = !article.published;
						m_adapter.notifyDataSetChanged();
						
						m_activity.saveArticlePublished(article);
					}
				});
			}
			
			TextView te = (TextView)v.findViewById(R.id.excerpt);

			String articleContent = article.content != null ? article.content : "";

			if (te != null) {
				String excerpt = Jsoup.parse(articleContent).text(); 
				
				if (excerpt.length() > 200)
					excerpt = excerpt.substring(0, 200) + "...";
				
				te.setText(excerpt);
			}       	

			/* ImageView separator = (ImageView)v.findViewById(R.id.headlines_separator);
			
			if (separator != null && m_onlineServices.isSmallScreen()) {
				separator.setVisibility(View.GONE);
			} */
			
			TextView dv = (TextView) v.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date((long)article.updated * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				dv.setText(df.format(d));
			}
			
			CheckBox cb = (CheckBox) v.findViewById(R.id.selected);

			if (cb != null) {
				cb.setChecked(m_selectedArticles.contains(article));
				cb.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View view) {
						CheckBox cb = (CheckBox)view;
						
						if (cb.isChecked()) {
							if (!m_selectedArticles.contains(article))
								m_selectedArticles.add(article);
						} else {
							m_selectedArticles.remove(article);
						}
						
						m_listener.onArticleListSelectionChange(m_selectedArticles);
						
						Log.d(TAG, "num selected: " + m_selectedArticles.size());
					}
				});
			}
			
			/* ImageButton ib = (ImageButton) v.findViewById(R.id.article_menu_button);
			
			if (ib != null) {
				ib.setVisibility(android.os.Build.VERSION.SDK_INT >= 10 ? View.VISIBLE : View.GONE);
				ib.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						getActivity().openContextMenu(v);
					}
				});								
			} */
			
			return v;
		}
	}



	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public ArticleList getAllArticles() {
		return m_articles;
	}

	public void setActiveArticle(Article article) {
		if (article != m_activeArticle) {
			m_activeArticle = article;
			m_adapter.notifyDataSetChanged();
		
			ListView list = (ListView)getView().findViewById(R.id.headlines);
		
			if (list != null && article != null) {
				int position = m_adapter.getPosition(article);
				list.setSelection(position);
			}
		}
	}

	public void setSelection(ArticlesSelection select) {
		m_selectedArticles.clear();
		
		if (select != ArticlesSelection.NONE) {
			for (Article a : m_articles) {
				if (select == ArticlesSelection.ALL || select == ArticlesSelection.UNREAD && a.unread) {
					m_selectedArticles.add(a);
				}
			}
		}
		
		m_adapter.notifyDataSetChanged();
	}

	public Article getArticleAtPosition(int position) {
		try {
			return m_adapter.getItem(position);
		} catch (IndexOutOfBoundsException e) {
			return null;
		} catch (NullPointerException e) {
			return null;
		}
	}
	
	public Article getArticleById(int id) {
		for (Article a : m_articles) {
			if (a.id == id)
				return a;
		}
		return null;
	}

	public ArticleList getUnreadArticles() {
		ArticleList tmp = new ArticleList();
		for (Article a : m_articles) {
			if (a.unread) tmp.add(a);
		}
		return tmp;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (!m_refreshInProgress && m_articles.findById(-1) != null && firstVisibleItem + visibleItemCount == m_articles.size()) {
			refresh(true);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// no-op
	}

	public Article getActiveArticle() {
		return m_activeArticle;
	}

	public int getArticlePosition(Article article) {
		try {
			return m_adapter.getPosition(article);
		} catch (NullPointerException e) {
			return -1;
		}
	}

	public String getSearchQuery() {
		return m_searchQuery;
	}
	
	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;
			refresh(false);
		}
	}

	public Feed getFeed() {
		return m_feed;
	}

	
}
