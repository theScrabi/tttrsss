package org.fox.ttrss;

import java.io.File;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.types.Feed;
import org.jsoup.Jsoup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class HeadlinesFragment extends Fragment implements OnItemClickListener, OnScrollListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;
	
	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private int m_activeArticleId;
	private boolean m_refreshInProgress = false;
	private boolean m_canLoadMore = false;
	private boolean m_combinedMode = true;
	private String m_searchQuery = "";
	
	private SharedPreferences m_prefs;
	
	private ArticleListAdapter m_adapter;
	private ArticleList m_articles = new ArticleList();
	private ArticleList m_selectedArticles = new ArticleList();
	
	private OnlineServices m_onlineServices;
	
	private ImageGetter m_dummyGetter = new ImageGetter() {

		@Override
		public Drawable getDrawable(String source) {
			return new BitmapDrawable();
		}
		
	};
	
	public ArticleList getSelectedArticles() {
		return m_selectedArticles;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_menu, menu);
		
		if (m_selectedArticles.size() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			Article article = getArticleAtPosition(info.position);
			menu.setHeaderTitle(article.title);
			menu.setGroupVisible(R.id.menu_group_single_article, true);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feed = savedInstanceState.getParcelable("feed");
			m_articles = savedInstanceState.getParcelable("articles");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
			m_canLoadMore = savedInstanceState.getBoolean("canLoadMore");			
			m_combinedMode = savedInstanceState.getBoolean("combinedMode");
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
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_onlineServices = (OnlineServices) activity;
		m_feed = m_onlineServices.getActiveFeed();

		m_combinedMode = m_prefs.getBoolean("combined_mode", false);
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {
			Article article = (Article)list.getItemAtPosition(position);
			if (article.id >= 0) {
				if (m_combinedMode) {
					article.unread = false;
					m_onlineServices.saveArticleUnread(article);
				} else {
					m_onlineServices.openArticle(article, 0);
				}
				
				m_activeArticleId = article.id;
				m_adapter.notifyDataSetChanged();
			}
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean append) {
		m_refreshInProgress = true;
		
		HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext());
		
		final String sessionId = m_onlineServices.getSessionId();
		final boolean showUnread = m_onlineServices.getUnreadArticlesOnly();
		final boolean isCat = m_feed.is_cat;
		int skip = 0;
		
		if (append) {
			for (Article a : m_articles) {
				if (a.unread) ++skip;
			}
			
			if (skip == 0) skip = m_articles.size();
		} else {
			setLoadingStatus(R.string.blank, true);
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
				
				if (isCat) put("is_cat", "true");
				
				if (m_searchQuery.length() != 0) {
					put("search", m_searchQuery);
					put("search_mode", "");
					put("match_on", "both");
				}
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
		out.putBoolean("canLoadMore", m_canLoadMore);
		out.putBoolean("combinedMode", m_combinedMode);
		out.putCharSequence("searchQuery", m_searchQuery);
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
				m_onlineServices.login();
			} else {
				setLoadingStatus(getErrorMessage(), false);
			}
			m_refreshInProgress = false;
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
			} else if (a.id == m_activeArticleId) {
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
				if (m_combinedMode) {
					tt.setMovementMethod(LinkMovementMethod.getInstance());
					tt.setText(Html.fromHtml("<a href=\""+article.link.trim().replace("\"", "\\\"")+"\">" + article.title + "</a>"));
				} else {
					tt.setText(Html.fromHtml(article.title));
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
						
						m_onlineServices.saveArticleMarked(article);
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
						
						m_onlineServices.saveArticlePublished(article);
					}
				});
			}
			
			TextView te = (TextView)v.findViewById(R.id.excerpt);

			String articleContent = article.content != null ? article.content : "";

			if (te != null) {
				if (!m_combinedMode) {			
					String excerpt = Jsoup.parse(articleContent).text(); 
				
					if (excerpt.length() > 100)
						excerpt = excerpt.substring(0, 100) + "...";
				
					te.setText(excerpt);
				} else {
					te.setVisibility(View.GONE);
				}
			}       	

			ImageView separator = (ImageView)v.findViewById(R.id.headlines_separator);
			
			if (separator != null && m_onlineServices.isSmallScreen()) {
				separator.setVisibility(View.GONE);
			}

			TextView content = (TextView)v.findViewById(R.id.content);
			
			if (content != null) {
				if (m_combinedMode) {
					content.setMovementMethod(LinkMovementMethod.getInstance());
					
					final Spinner spinner = (Spinner) v.findViewById(R.id.attachments);
					
					ArrayList<Attachment> spinnerArray = new ArrayList<Attachment>();
					
					ArrayAdapter<Attachment> spinnerArrayAdapter = new ArrayAdapter<Attachment>(
				            getActivity(), android.R.layout.simple_spinner_item, spinnerArray);
					
					spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					
					if (article.attachments != null && article.attachments.size() != 0) {
						for (Attachment a : article.attachments) {
							if (a.content_type != null && a.content_url != null) {
								
								try {
									URL url = new URL(a.content_url.trim());

									if (a.content_type.indexOf("image") != -1) {
										articleContent += "<br/><img src=\"" + url.toString().trim().replace("\"", "\\\"") + "\">";
									}
									
									spinnerArray.add(a);

								} catch (MalformedURLException e) {
									//
								} catch (Exception e) {
									e.printStackTrace();
								}								
							}					
						}
						
						spinner.setAdapter(spinnerArrayAdapter);
						
						Button attachmentsView = (Button) v.findViewById(R.id.attachment_view);
						
						attachmentsView.setOnClickListener(new OnClickListener() {
							
							@Override
							public void onClick(View v) {
								Attachment attachment = (Attachment) spinner.getSelectedItem();
								
								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(attachment.content_url));
								startActivity(browserIntent);
							}
						});
						
						Button attachmentsCopy = (Button) v.findViewById(R.id.attachment_copy);
						
						attachmentsCopy.setOnClickListener(new OnClickListener() {
							
							@Override
							public void onClick(View v) {
								Attachment attachment = (Attachment) spinner.getSelectedItem();

								if (attachment != null) {
									m_onlineServices.copyToClipboard(attachment.content_url);
								}
							}
						});
						
					} else {
						v.findViewById(R.id.attachments_holder).setVisibility(View.GONE);
					}
					
					//content.setText(Html.fromHtml(article.content, new URLImageGetter(content, getActivity()), null));
					content.setText(Html.fromHtml(articleContent, m_dummyGetter, null));

					switch (Integer.parseInt(m_prefs.getString("font_size", "0"))) {
					case 0:
						content.setTextSize(15F);
						break;
					case 1:
						content.setTextSize(18F);
						break;
					case 2:
						content.setTextSize(21F);
						break;		
					}

				} else {
					content.setVisibility(View.GONE);
					v.findViewById(R.id.attachments_holder).setVisibility(View.GONE);
				}
				
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
						
						m_onlineServices.initMainMenu();
						
						Log.d(TAG, "num selected: " + m_selectedArticles.size());
					}
				});
			}
			
			return v;
		}
	}



	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
		
		Article article = m_onlineServices.getSelectedArticle();
		if (article != null) {
			setActiveArticleId(article.id);
		}
	}

	public ArticleList getAllArticles() {
		return m_articles;
	}

	public void setActiveArticleId(int id) {
		m_activeArticleId = id;
		m_adapter.notifyDataSetChanged();
		
		ListView list = (ListView)getView().findViewById(R.id.headlines);
		
		if (list != null) {
			int position = m_adapter.getPosition(getArticleById(id));
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
		try {
			return m_adapter.getItem(position);
		} catch (IndexOutOfBoundsException e) {
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
		if (!m_refreshInProgress && m_canLoadMore && firstVisibleItem + visibleItemCount == m_articles.size()) {
			refresh(true);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// no-op
	}

	public int getActiveArticleId() {
		return m_activeArticleId;
	}

	public int getArticlePosition(Article article) {
		return m_adapter.getPosition(article);
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;
			refresh(false);
		}
	}

	
}
