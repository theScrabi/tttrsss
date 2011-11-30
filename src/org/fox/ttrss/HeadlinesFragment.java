package org.fox.ttrss;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import org.jsoup.Jsoup;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class HeadlinesFragment extends Fragment implements OnItemClickListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private int m_activeArticleId;
	
	private ArticleListAdapter m_adapter;
	private ArticleList m_articles = new ArticleList();
	private ArticleList m_selectedArticles = new ArticleList();
	
	private ArticleOps m_articleOps;
	private boolean m_smallScreenMode = false;
	private boolean m_portraitMode = false;
	
	public ArticleList getSelectedArticles() {
		return m_selectedArticles;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_menu, menu);
		
		if (m_selectedArticles.size() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
		} else {
			menu.setHeaderTitle(R.string.headline_context_single);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		m_portraitMode = getActivity().getWindowManager().getDefaultDisplay().getOrientation() % 2 == 0;

		if (savedInstanceState != null) {
			m_feed = savedInstanceState.getParcelable("feed");
			m_articles = savedInstanceState.getParcelable("articles");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
			m_smallScreenMode = savedInstanceState.getBoolean("smallScreenMode");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		ListView list = (ListView)view.findViewById(R.id.headlines);		
		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		registerForContextMenu(list);

		Log.d(TAG, "onCreateView, feed=" + m_feed);
		
		if (m_feed != null && (m_articles == null || m_articles.size() == 0)) 
			refresh(false);
		else
			view.findViewById(R.id.loading_progress).setVisibility(View.GONE);

		return view;    	
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_feed = ((MainActivity)activity).getActiveFeed();
		m_smallScreenMode = ((MainActivity)activity).getSmallScreenMode();
		m_articleOps = (ArticleOps) activity;
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Article article = (Article)list.getItemAtPosition(position);
			m_articleOps.openArticle(article, 0);
			
			m_activeArticleId = article.id;
			m_adapter.notifyDataSetChanged();
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean append) {
		HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext());
		
		final String sessionId = ((MainActivity)getActivity()).getSessionId();
		final boolean showUnread = ((MainActivity)getActivity()).getUnreadArticlesOnly();
		final boolean isCat = m_feed.is_cat;
		int skip = 0;
		
		if (append) {
			for (Article a : m_articles) {
				if (a.unread) ++skip;
			}
			
			if (skip == 0) skip = m_articles.size();
		}
		
		final int fskip = skip;
		
		req.setOffset(skip);
		
		setLoadingStatus(R.string.blank, true);
		
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", sessionId);
				put("feed_id", String.valueOf(m_feed.id));
				put("show_content", "true");
				put("limit", String.valueOf(30));
				put("offset", String.valueOf(0));
				put("view_mode", showUnread ? "adaptive" : "all_articles");
				put("skip", String.valueOf(fskip));
				
				if (isCat) put("is_cat", "true");
			}			 
		};

		req.execute(map);
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putParcelable("feed", m_feed);
		out.putParcelable("articles", m_articles);
		out.putInt("activeArticleId", m_activeArticleId);
		out.putParcelable("selectedArticles", m_selectedArticles);
		out.putBoolean("smallScreenMode", m_smallScreenMode);
	}

	public void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}
			
			View pb = getView().findViewById(R.id.loading_progress);
			
			if (pb != null) {
				pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
			}
		}
	}
	
	private class HeadlinesRequest extends ApiRequest {
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
						
						if (m_offset == 0)
							m_articles.clear();
						
						int last_position = m_articles.size();
						
						for (Article f : articles) 
							m_articles.add(f);
						
						m_adapter.notifyDataSetChanged();
						
						ListView list = (ListView)getView().findViewById(R.id.headlines);
						
						if (list != null && m_offset != 0) {
							list.setSelection(last_position-1);
						}
						
						MainActivity activity = (MainActivity)getActivity();
						activity.setCanLoadMore(articles.size() >= 30);
						activity.initMainMenu();
						
						if (m_articles.size() == 0)
							setLoadingStatus(R.string.no_headlines_to_display, false);
						else
							setLoadingStatus(R.string.blank, false);

						return;
					}
							
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}

			if (m_lastError == ApiError.LOGIN_FAILED) {
				MainActivity activity = (MainActivity)getActivity();							
				activity.login();
			} else {
				setLoadingStatus(getErrorMessage(), false);
			}
	    }

		public void setOffset(int skip) {
			m_offset = skip;			
		}
	}
	
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		
		public static final int VIEW_COUNT = VIEW_SELECTED+1;
		
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
			
			if (a.id == m_activeArticleId) {
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
				int layoutId = (m_smallScreenMode && m_portraitMode) ? R.layout.headlines_row_small : R.layout.headlines_row;
				
				switch (getItemViewType(position)) {
				case VIEW_UNREAD:
					layoutId = (m_smallScreenMode && m_portraitMode) ? R.layout.headlines_row_small_unread : R.layout.headlines_row_unread;
					break;
				case VIEW_SELECTED:
					layoutId = (m_smallScreenMode && m_portraitMode) ? R.layout.headlines_row_small_selected : R.layout.headlines_row_selected;
					break;
				}
				
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutId, null);
			}

			TextView tt = (TextView)v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(Html.fromHtml(article.title));
			}

			ImageView marked = (ImageView)v.findViewById(R.id.marked);
			
			if (marked != null) {
				marked.setImageResource(article.marked ? android.R.drawable.star_on : android.R.drawable.star_off);
				
				marked.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.marked = !article.marked;
						m_adapter.notifyDataSetChanged();
						
						m_articleOps.saveArticleMarked(article);
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
						
						m_articleOps.saveArticlePublished(article);
					}
				});
			}
			
			TextView te = (TextView)v.findViewById(R.id.excerpt);

			if (te != null) {
				String excerpt = Jsoup.parse(article.content).text(); 
				
				if (excerpt.length() > 100)
					excerpt = excerpt.substring(0, 100) + "...";
				
				te.setText(excerpt);
			}       	

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
						
						Log.d(TAG, "num selected: " + m_selectedArticles.size());
					}
				});
			}
			
			return v;
		}
	}



	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public ArticleList getAllArticles() {
		return m_articles;
	}

	public void setActiveArticleId(int id) {
		m_activeArticleId = id;
		m_adapter.notifyDataSetChanged();
		
		ListView list = (ListView)getView().findViewById(R.id.headlines);
		
		if (list != null) {
			int position = m_adapter.getPosition(m_articleOps.getSelectedArticle());
			list.setSelection(position);
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
		return m_adapter.getItem(position);
	}

	public ArticleList getUnreadArticles() {
		ArticleList tmp = new ArticleList();
		for (Article a : m_articles) {
			if (a.unread) tmp.add(a);
		}
		return tmp;
	}

}
