package org.fox.ttrss;

import java.net.MalformedURLException;
import java.net.URL;
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
import org.fox.ttrss.util.RecyclerArrayAdapter;
import org.fox.ttrss.util.TypefaceCache;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
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
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

public class HeadlinesFragment extends Fragment {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;
	
	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private Article m_activeArticle;
	private String m_searchQuery = "";
	private boolean m_refreshInProgress = false;
	private boolean m_autoCatchupDisabled = false;
	
	private SharedPreferences m_prefs;
	
	private ArticleListAdapter m_adapter;
	private ArticleList m_articles = GlobalState.getInstance().m_loadedArticles;
	private ArticleList m_selectedArticles = new ArticleList();
	private ArticleList m_readArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
	private int m_maxImageSize = 0;

	public ArticleList getSelectedArticles() {
		return m_selectedArticles;
	}
	
	public void initialize(Feed feed) {
		m_feed = feed;
	}

	public void initialize(Feed feed, Article activeArticle) {
		m_feed = feed;
		
		if (activeArticle != null) {
			m_activeArticle = getArticleById(activeArticle.id);
		}
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
					if (m_activity.getApiLevel() != 7) {
						m_activity.editArticleLabels(article);					
					} else {
						m_activity.toast(R.string.server_function_not_available);
					}				
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
		case R.id.headlines_article_link_open:
			if (true) {
				Article article = getArticleAtPosition(info.position);
			
				if (article != null) {
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.link));
					startActivity(browserIntent);

					if (article.unread) {
						article.unread = false;
						m_activity.saveArticleUnread(article);
					}
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
						if (article.id == a.id)
							break;

						if (a.unread) {
							a.unread = false;
							tmp.add(a);
						}
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
			menu.setHeaderTitle(Html.fromHtml(article.title));
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

		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
		m_maxImageSize = (int) (128 * metrics.density + 0.5);

		Log.d(TAG, "maxImageSize=" + m_maxImageSize);
		
		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.headlines_swipe_container);

	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh(false, true);
			}
		});

	    if (!m_activity.isCompatMode()) {
	    	m_swipeLayout.setColorScheme(android.R.color.holo_green_dark, 
	    		android.R.color.holo_red_dark, 
	            android.R.color.holo_blue_dark, 
	            android.R.color.holo_orange_dark);
	    }

		
		final RecyclerView list = (RecyclerView)view.findViewById(R.id.headlines);

        m_adapter = new ArticleListAdapter(m_articles);

        list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(m_adapter);
		registerForContextMenu(list);

        list.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
                    Log.d(TAG, "scroll ended!");

                    if (!m_readArticles.isEmpty()) {
                        m_activity.toggleArticlesUnread(m_readArticles);
                        m_activity.refresh(false);
                        m_readArticles.clear();
                    }
                }

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = layoutManager.findLastCompletelyVisibleItemPosition() - layoutManager.findFirstVisibleItemPosition() + 1;

                Log.d(TAG, "fvI= " + firstVisibleItem + " vIC=" + visibleItemCount);

                if (!m_refreshInProgress && m_articles.findById(-1) != null && firstVisibleItem + visibleItemCount == m_articles.size()) {
                    refresh(true);
                }

                if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > 0 && !m_autoCatchupDisabled) {
                    Article a = m_articles.get(firstVisibleItem - 1);

                    if (a != null && a.unread) {
                        a.unread = false;
                        m_readArticles.add(a);
                        m_feed.unread--;
                    }
                }

            }
        });

		//m_activity.m_pullToRefreshAttacher.addRefreshableView(list, this);

		//if (m_activity.isSmallScreen())
		//view.findViewById(R.id.headlines_fragment).setPadding(0, 0, 0, 0);

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

	/* @Override
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
	} */

	public void refresh(boolean append) {
		refresh(append, false);	
	}
	
	@SuppressWarnings({ "serial" })
	public void refresh(boolean append, boolean userInitiated) {
		if (m_activity != null && m_feed != null) {
			m_refreshInProgress = true;

			if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);
			m_activity.setProgressBarVisibility(true);
			
			if (!m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
				append = false;
			}

			// new stuff may appear on top, scroll back to show it
			if (!append) {
				if (getView() != null) {
					Log.d(TAG, "scroll hack");
					RecyclerView list = (RecyclerView) getView().findViewById(R.id.headlines);
					m_autoCatchupDisabled = true;
					//list.setSelection(0);
					m_autoCatchupDisabled = false;
					//list.setEmptyView(null);
					m_adapter.clear();
					m_adapter.notifyDataSetChanged();
				}
			}
			
			final boolean fappend = append;
			final String sessionId = m_activity.getSessionId();
			final boolean isCat = m_feed.is_cat;
			
			HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity, m_feed) {
				@Override
				protected void onProgressUpdate(Integer... progress) {
					m_activity.setProgress(Math.round((((float)progress[0] / (float)progress[1]) * 10000)));
				}

				@Override
				protected void onPostExecute(JsonElement result) {
					if (isDetached()) return;
					
					/* if (getView() != null) {
						ListView list = (ListView)getView().findViewById(R.id.headlines);
					
						if (list != null) {
							list.setEmptyView(getView().findViewById(R.id.no_headlines));
						}
					} */
					
					m_activity.setProgressBarVisibility(false);
					
					super.onPostExecute(result);	

					if (isAdded()) {
                        if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
					} 

					if (result != null) {
						m_refreshInProgress = false;
						
						if (m_articles.indexOf(m_activeArticle) == -1)
							m_activeArticle = null;
						
						m_adapter.notifyDataSetChanged();
						m_listener.onHeadlinesLoaded(fappend);
						
					} else {
						if (m_lastError == ApiError.LOGIN_FAILED) {
							m_activity.login(true);
						} else {
							m_activity.setLoadingStatus(getErrorMessage(), false);
						}
					}
				}
			};
			
			int skip = 0;
			
			if (append) {
				// adaptive, all_articles, marked, published, unread
				String viewMode = m_activity.getViewMode();
				int numUnread = 0;
				int numAll = m_articles.size();
				
				for (Article a : m_articles) {
					if (a.unread) ++numUnread;
				}
				
				if ("marked".equals(viewMode)) {
					skip = numAll;
				} else if ("published".equals(viewMode)) {
					skip = numAll;
				} else if ("unread".equals(viewMode)) {
					skip = numUnread;					
				} else if (m_searchQuery != null && m_searchQuery.length() > 0) {
					skip = numAll;
				} else if ("adaptive".equals(viewMode)) {
					skip = numUnread > 0 ? numUnread : numAll;
				} else {
					skip = numAll;
				}
				
			} else {
				m_activity.setLoadingStatus(R.string.blank, true);
			}
			
			final int fskip = skip;
			
			final boolean allowForceUpdate = m_activity.getApiLevel() >= 9 && 
					!m_feed.is_cat && m_feed.id > 0 && !append && userInitiated &&  
					skip == 0;

			Log.d(TAG, "allowForceUpdate=" + allowForceUpdate + " userInitiated=" + userInitiated);
					
					
			req.setOffset(skip);
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getHeadlines");
					put("sid", sessionId);
					put("feed_id", String.valueOf(m_feed.id));
					put("show_content", "true");
					put("include_attachments", "true");
					put("view_mode", m_activity.getViewMode());
					put("limit", String.valueOf(HEADLINES_REQUEST_SIZE));
					put("offset", String.valueOf(0));
					put("skip", String.valueOf(fskip));
					put("include_nested", "true");
					put("order_by", m_prefs.getBoolean("oldest_first", false) ? "date_reverse" : "");
					
					if (isCat) put("is_cat", "true");

					if (allowForceUpdate) {					
						put("force_update", "true");
					}
					
					if (m_searchQuery != null && m_searchQuery.length() != 0) {
						put("search", m_searchQuery);
						put("search_mode", "");
						put("match_on", "both");
					}
					
				}			 
			};

            Log.d(TAG, "[HP] request more headlines...");

			req.execute(map);
		}
	}		

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.setClassLoader(getClass().getClassLoader());
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
	
	private class ArticleListAdapter extends RecyclerArrayAdapter<Article, RecyclerView.ViewHolder> {
		//private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;

		/* public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;

			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;
		} */

        public class HeadlineViewHolder extends RecyclerView.ViewHolder {
            public TextView titleView;
            public TextView feedTitleView;
            public ImageView markedView;
            public ImageView publishedView;
            public TextView excerptView;
            public ImageView flavorImageView;
            public TextView authorView;
            public TextView dateView;
            public CheckBox selectionBoxView;
            public ImageView menuButtonView;
            public ViewGroup flavorImageHolder;
            public View headlineView;

            public HeadlineViewHolder(View v) {
                super(v);

                titleView = (TextView)v.findViewById(R.id.title);
                feedTitleView = (TextView)v.findViewById(R.id.feed_title);
                markedView = (ImageView)v.findViewById(R.id.marked);
                publishedView = (ImageView)v.findViewById(R.id.published);
                excerptView = (TextView)v.findViewById(R.id.excerpt);
                flavorImageView = (ImageView) v.findViewById(R.id.flavor_image);
                authorView = (TextView)v.findViewById(R.id.author);
                dateView = (TextView) v.findViewById(R.id.date);
                selectionBoxView = (CheckBox) v.findViewById(R.id.selected);
                menuButtonView = (ImageView) v.findViewById(R.id.article_menu_button);
                flavorImageHolder = (ViewGroup) v.findViewById(R.id.flavorImageHolder);
                headlineView = v;
            }
        }


        // Create new views (invoked by the layout manager)
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
            /* // create a new view
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.my_text_view, parent, false);
            // set the view's size, margins, paddings and layout parameters
            ...
            ViewHolder vh = new ViewHolder(v);
            return vh; */

            int layoutId = R.layout.headlines_row;

            switch (viewType) {
                case VIEW_LOADMORE:
                    layoutId = R.layout.headlines_row_loadmore;
                    break;
                case VIEW_UNREAD:
                    layoutId = R.layout.headlines_row_unread;
                    break;
                case VIEW_SELECTED:
                    layoutId = R.layout.headlines_row_selected;
                    break;
                case VIEW_SELECTED_UNREAD:
                    layoutId = R.layout.headlines_row_selected_unread;
                    break;
            }

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(layoutId, parent, false);

            ((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

            HeadlineViewHolder vh = new HeadlineViewHolder(v);

            return vh;
        }

        /* @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            HeadlineViewHolder avh = (HeadlineViewHolder) holder;

            // - get element from your dataset at this position
            // - replace the contents of the view with that element
            //holder.mTextView.setText(mDataset[position]);

        } */

        public ArticleListAdapter(ArrayList<Article> items) {
            super(items);

            Theme theme = getActivity().getTheme();
            TypedValue tv = new TypedValue();
            theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
            titleHighScoreUnreadColor = tv.data;
        }

		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Article a = m_items.get(position);
			
			if (a.id == -1) {
				return VIEW_LOADMORE;
			} else if (m_activeArticle != null && a.id == m_activeArticle.id && a.unread) {
				return VIEW_SELECTED_UNREAD;
			} else if (m_activeArticle != null && a.id == m_activeArticle.id) {
				return VIEW_SELECTED;
			} else if (a.unread) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}


        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, final int position) {
            HeadlineViewHolder holder = (HeadlineViewHolder) h;
			final Article article = m_items.get(position);

			int headlineFontSize = Integer.parseInt(m_prefs.getString("headlines_font_size_sp", "13"));
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));

            holder.headlineView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (article.id >= 0) {
                        m_listener.onArticleSelected(article);

                        // only set active article when it makes sense (in HeadlinesActivity)
                        if (getActivity().findViewById(R.id.article_fragment) != null) {
                            m_activeArticle = article;
                        }

                        m_adapter.notifyDataSetChanged();
                    }
                }
            });

            if (holder.titleView != null) {
				holder.titleView.setText(Html.fromHtml(article.title));
				
				if (m_prefs.getBoolean("enable_condensed_fonts", false)) {
					Typeface tf = TypefaceCache.get(m_activity, "sans-serif-condensed", article.unread ? Typeface.BOLD : Typeface.NORMAL);
					
					if (tf != null && !tf.equals(holder.titleView.getTypeface())) {
						holder.titleView.setTypeface(tf);
					}
					
					holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 5));
				} else {
					holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));
				}
				
				adjustTitleTextView(article.score, holder.titleView, position);
			}

			
			
			if (holder.feedTitleView != null) {				
				if (article.feed_title != null && (m_feed.is_cat || m_feed.id < 0)) {
					
					/* if (article.feed_title.length() > 20)
						ft.setText(article.feed_title.substring(0, 20) + "...");
					else */
					
					holder.feedTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
					holder.feedTitleView.setText(article.feed_title);
					
				} else {
					holder.feedTitleView.setVisibility(View.GONE);
				}
				
			}
			
			
			
			if (holder.markedView != null) {
				holder.markedView.setImageResource(article.marked ? R.drawable.ic_star_full : R.drawable.ic_star_empty);
				
				holder.markedView.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.marked = !article.marked;
						m_adapter.notifyDataSetChanged();
						
						m_activity.saveArticleMarked(article);
					}
				});
			}
			
			
			
			if (holder.publishedView != null) {
				holder.publishedView.setImageResource(article.published ? R.drawable.ic_published : R.drawable.ic_unpublished);
				
				holder.publishedView.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.published = !article.published;
						m_adapter.notifyDataSetChanged();
						
						m_activity.saveArticlePublished(article);
					}
				});
			}

			String articleContent = article.content != null ? article.content : "";

			if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
					String excerpt = Jsoup.parse(articleContent).text(); 
					
					if (excerpt.length() > CommonActivity.EXCERPT_MAX_SIZE)
						excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_SIZE) + "...";
					
					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);
				}
			}

			
			
			if (holder.flavorImageView != null && m_prefs.getBoolean("headlines_show_flavor_image", true)) {
				holder.flavorImageView.setVisibility(View.GONE);
				
				Document doc = Jsoup.parse(articleContent);
				
					Element img = doc.select("img").first();
					if (doc != null) {

					if (img != null) {
						String imgSrc = img.attr("src");
						
						// retarded schema-less urls
						if (imgSrc.indexOf("//") == 0)
							imgSrc = "http:" + imgSrc;
						
						DisplayImageOptions options = new DisplayImageOptions.Builder().
								cacheInMemory(true).
								cacheOnDisk(true).
								build();
						
						final ImageView flavorImageView = holder.flavorImageView;
						
						ImageLoader.getInstance().displayImage(imgSrc, holder.flavorImageView, options, new ImageLoadingListener() {

							@Override
							public void onLoadingCancelled(String arg0,
									View arg1) {
								// TODO Auto-generated method stub
								
							}

							@Override
							public void onLoadingComplete(String arg0,
									View arg1, Bitmap arg2) {
								// TODO Auto-generated method stub
								
								if (!isAdded() || arg2 == null) return;
								
								if (arg2.getWidth() > 128 && arg2.getHeight() > 128) {
									if (arg0 != null && !arg0.equals(arg1.getTag())) {
										if (!m_activity.isCompatMode() && flavorImageView.getVisibility() != View.VISIBLE) {
											ObjectAnimator anim = ObjectAnimator.ofFloat(flavorImageView, "alpha", 0f, 1f);
											anim.setDuration(500);
											anim.start();
										}
									}
									
									flavorImageView.setTag(arg0);
									flavorImageView.setVisibility(View.VISIBLE);
								}
							}

							@Override
							public void onLoadingFailed(String arg0,
									View arg1, FailReason arg2) {
								// TODO Auto-generated method stub
							}

							@Override
							public void onLoadingStarted(String arg0,
									View arg1) {
								// TODO Auto-generated method stub
								
							}
							
						});
					}
					
				}				
			} else if (holder.flavorImageHolder != null) {
				holder.flavorImageHolder.setVisibility(View.GONE);
			}
			
			String articleAuthor = article.author != null ? article.author : "";
			
			
			if (holder.authorView != null) {
				holder.authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);

				if (articleAuthor.length() > 0) {
					holder.authorView.setText(getString(R.string.author_formatted, articleAuthor));
				} else {
					holder.authorView.setText("");
				}
			}
					
			if (holder.dateView != null) {
				holder.dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
				
				Date d = new Date((long)article.updated * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				holder.dateView.setText(df.format(d));
			}
			

			if (holder.selectionBoxView != null) {
				holder.selectionBoxView.setChecked(m_selectedArticles.contains(article));
				holder.selectionBoxView.setOnClickListener(new OnClickListener() {
					
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

			if (holder.menuButtonView != null) {
				//if (m_activity.isDarkTheme())
				//	ib.setImageResource(R.drawable.ic_mailbox_collapsed_holo_dark);
				
				holder.menuButtonView.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						getActivity().openContextMenu(v);
					}
				});								
			}
		}

		private void adjustTitleTextView(int score, TextView tv, int position) {
			int viewType = getItemViewType(position);
			if (origTitleColors[viewType] == null)
				// store original color
				origTitleColors[viewType] = Integer.valueOf(tv.getCurrentTextColor());

			if (score < -500) {
				tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			} else if (score > 500) {
				tv.setTextColor(titleHighScoreUnreadColor);
				tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			} else {
				tv.setTextColor(origTitleColors[viewType].intValue());
				tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			}
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

	/* @Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (!m_refreshInProgress && m_articles.findById(-1) != null && firstVisibleItem + visibleItemCount == m_articles.size()) {
			refresh(true);
		}

		if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > 0 && !m_autoCatchupDisabled) {
			Article a = m_articles.get(firstVisibleItem - 1);

			if (a != null && a.unread) {
				a.unread = false;
				m_readArticles.add(a);
				m_feed.unread--;
			} 
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollState == SCROLL_STATE_IDLE && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
			if (!m_readArticles.isEmpty()) {
				m_activity.toggleArticlesUnread(m_readArticles);
				m_activity.refresh(false);
				m_readArticles.clear();
			}
		}
	} */

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

	/* class DownloadFlavorImagesTask extends AsyncTask<ImageView, Void, Bitmap> {
		   ImageView imageView = null;
		   @Override
		   protected Bitmap doInBackground(ImageView... imageViews) {
		      this.imageView = imageViews[0];
		      return download((URL)imageView.getTag());
		   }

		   @Override
		   protected void onPostExecute(Bitmap result) {
			  if (result != null) {
				  imageView.setImageBitmap(result);
				  imageView.setVisibility(View.VISIBLE);
			  }
		   }

		   private Bitmap download(URL url) {
			   try {
				   HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				
				   conn.setDoInput(true); 
				   conn.setUseCaches(true);
				   conn.connect();
				   
				   ByteArrayOutputStream bos = new ByteArrayOutputStream();
				   
				   byte[] buf = new byte[256];
				   int read = 0;

				   while ((read = conn.getInputStream().read(buf)) >= 0) {
					   bos.write(buf, 0, read);
				   }
				   
				   final BitmapFactory.Options options = new BitmapFactory.Options();

				   byte[] bitmap = bos.toByteArray();
				   
				   options.inJustDecodeBounds = true;
				   BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length, options);
				   options.inJustDecodeBounds = false;
				   
				   int inSampleSize = CommonActivity.calculateInSampleSize(options, 128, 128);
				   
				   Bitmap decodedBitmap = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length, options);
				   
			       return decodedBitmap;
			    } catch (OutOfMemoryError e) {
			    	Log.d(TAG, "OOM while trying to decode headline flavor image. :(");
			    	e.printStackTrace();
				} catch (IOException e) {
					//
				}
			   
		       return null;
		   }
	} */
}
