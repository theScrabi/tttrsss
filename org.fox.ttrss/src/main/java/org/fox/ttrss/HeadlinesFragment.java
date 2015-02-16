package org.fox.ttrss;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.transition.Fade;
import android.transition.Transition;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.google.gson.JsonElement;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.utils.MemoryCacheUtils;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

public class HeadlinesFragment extends Fragment implements OnItemClickListener, OnScrollListener {
    public static enum ArticlesSelection { ALL, NONE, UNREAD }

    public static final int FLAVOR_IMG_MIN_WIDTH = 128;
    public static final int FLAVOR_IMG_MIN_HEIGHT = 128;

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
	private ArticleList m_articles = new ArticleList(); //GlobalState.getInstance().m_loadedArticles;
	//private ArticleList m_selectedArticles = new ArticleList();
	private ArticleList m_readArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
	private int m_maxImageSize = 0;
    private boolean m_compactLayoutMode = false;

	public ArticleList getSelectedArticles() {
        ArticleList tmp = new ArticleList();

        for (Article a : m_articles) {
            if (a.selected) tmp.add(a);
        }

		return tmp;
	}
	
	public void initialize(Feed feed) {
		m_feed = feed;
	}

	public void initialize(Feed feed, Article activeArticle, boolean compactMode, ArticleList articles) {
		m_feed = feed;
        m_compactLayoutMode = compactMode;
		
		if (activeArticle != null) {
			m_activeArticle = getArticleById(activeArticle.id);
		}

        if (articles != null) {
            m_articles = articles;
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

    public HeadlinesFragment() {
        super();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition fade = new Fade();

            setEnterTransition(fade);
            setReenterTransition(fade);
        }
    }
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_context_menu, menu);
		
		if (getSelectedArticles().size() > 0) {
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

            if (! (m_activity instanceof HeadlinesActivity)) {
                m_articles = savedInstanceState.getParcelable("articles");
            } else {
                m_articles = ((HeadlinesActivity)m_activity).m_articles;
            }

			m_activeArticle = savedInstanceState.getParcelable("activeArticle");
			//m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
            m_compactLayoutMode = savedInstanceState.getBoolean("compactLayoutMode");
		}

        if ("HL_COMPACT".equals(m_prefs.getString("headline_mode", "HL_DEFAULT")))
            m_compactLayoutMode = true;

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

		ListView list = (ListView)view.findViewById(R.id.headlines_list);

        /* if (!m_compactLayoutMode) {
            list.setDividerHeight(0);
            list.setDivider(null);
        } */

        if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int screenHeight = display.getHeight();

            View layout = inflater.inflate(R.layout.headlines_footer, container, false);

            layout.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));

            list.addFooterView(layout, null, false);
        }

		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);

		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		list.setOnScrollListener(this);
		//list.setEmptyView(view.findViewById(R.id.no_headlines));
		registerForContextMenu(list);

        if (m_activity.isSmallScreen()) {
            m_activity.setTitle(m_feed.title);
        }

		Log.d(TAG, "onCreateView, feed=" + m_feed);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		/* if (GlobalState.getInstance().m_activeArticle != null) {
			m_activeArticle = GlobalState.getInstance().m_activeArticle;
			GlobalState.getInstance().m_activeArticle = null;
		} */

		if (m_activeArticle != null) {
			setActiveArticle(m_activeArticle);
		}

        /* if (!(m_activity instanceof HeadlinesActivity)) {
            refresh(false);
        } */

        if (m_articles.size() == 0) {
            refresh(false);
        }

		/* if (m_articles.size() == 0 || !m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
			if (m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE) == null) {
				refresh(false);
				GlobalState.getInstance().m_activeFeed = m_feed;
			}			
		} else {
			notifyUpdated();
		} */
		
		m_activity.invalidateOptionsMenu();
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

            // could be footer or w/e
			if (article != null && article.id >= 0) {
				m_listener.onArticleSelected(article);
				
				// only set active article when it makes sense (in HeadlinesActivity)
				if (getActivity() instanceof HeadlinesActivity) {
					m_activeArticle = article;
				}
				
				m_adapter.notifyDataSetChanged();
			}
		}
	}

	public void refresh(boolean append) {
		refresh(append, false);	
	}
	
	@SuppressWarnings({ "serial" })
	public void refresh(boolean append, boolean userInitiated) {
		if (m_activity != null && m_feed != null) {
			m_refreshInProgress = true;

			if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);

			/* if (!m_feed.equals(GlobalState.getInstance().m_activeFeed)) {
				append = false;
			} */

			// new stuff may appear on top, scroll back to show it
			if (!append) {
				if (getView() != null) {
					Log.d(TAG, "scroll hack");
					ListView list = (ListView)getView().findViewById(R.id.headlines_list);
					m_autoCatchupDisabled = true;
					list.setSelection(0);
					m_autoCatchupDisabled = false;
					list.setEmptyView(null);
					m_adapter.clear();
					m_adapter.notifyDataSetChanged();
				}
			}
			
			final boolean fappend = append;
			final String sessionId = m_activity.getSessionId();
			final boolean isCat = m_feed.is_cat;
			
			HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity, m_feed, m_articles) {
				@Override
				protected void onProgressUpdate(Integer... progress) {
					m_activity.setProgress(Math.round((((float)progress[0] / (float)progress[1]) * 10000)));
				}

				@Override
				protected void onPostExecute(JsonElement result) {
					if (isDetached() || !isAdded()) return;
					
					if (getView() != null) {
						ListView list = (ListView)getView().findViewById(R.id.headlines_list);
					
						/* if (list != null) {
							list.setEmptyView(getView().findViewById(R.id.no_headlines));
						} */
					}

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
                            m_activity.toast(getErrorMessage());
							//m_activity.setLoadingStatus(getErrorMessage(), false);
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
				//m_activity.setLoadingStatus(R.string.blank, true);
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
                    put("show_excerpt", "true");
                    put("excerpt_length", String.valueOf(CommonActivity.EXCERPT_MAX_LENGTH));
					put("show_content", "true");
					put("include_attachments", "true");
					put("view_mode", m_activity.getViewMode());
					put("limit", String.valueOf(HEADLINES_REQUEST_SIZE));
					put("offset", String.valueOf(0));
					put("skip", String.valueOf(fskip));
					put("include_nested", "true");
                    put("has_sandbox", "true");
					put("order_by", m_activity.getSortMode());
					
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
		out.putParcelable("articles", m_articles);
		out.putParcelable("activeArticle", m_activeArticle);
		//out.putParcelable("selectedArticles", m_selectedArticles);
		out.putCharSequence("searchQuery", m_searchQuery);
        out.putBoolean("compactLayoutMode", m_compactLayoutMode);
	}

	static class HeadlineViewHolder {
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
		public ProgressBar flavorImageLoadingBar;
        public View flavorImageArrow;
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
    }
	
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;

        private ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();

		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;

			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;
		}
		
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);
			
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

        private void updateTextCheckedState(HeadlineViewHolder holder, Article item) {
            String tmp = item.title.length() > 0 ? item.title.substring(0, 1) : "?";

            if (item.selected) {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));

                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(tmp, m_colorGenerator.getColor(item.title)));

                holder.textChecked.setVisibility(View.GONE);
            }
        }

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;
			
			final Article article = items.get(position);
			final HeadlineViewHolder holder;
			
			int headlineFontSize = Integer.parseInt(m_prefs.getString("headlines_font_size_sp", "13"));
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));
			
			if (v == null) {
                int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

                switch (getItemViewType(position)) {
				case VIEW_LOADMORE:
					layoutId = R.layout.headlines_row_loadmore;
					break;
				case VIEW_UNREAD:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_unread_compact : R.layout.headlines_row_unread;
					break;
				case VIEW_SELECTED:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_selected_compact : R.layout.headlines_row;
					break;
				case VIEW_SELECTED_UNREAD:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_selected_unread_compact : R.layout.headlines_row_unread;
					break;
				}
				
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutId, null);

				holder = new HeadlineViewHolder();
				holder.titleView = (TextView)v.findViewById(R.id.title);

				holder.feedTitleView = (TextView)v.findViewById(R.id.feed_title);
				holder.markedView = (ImageView)v.findViewById(R.id.marked);
				holder.publishedView = (ImageView)v.findViewById(R.id.published);
				holder.excerptView = (TextView)v.findViewById(R.id.excerpt);
				holder.flavorImageView = (ImageView) v.findViewById(R.id.flavor_image);
				holder.authorView = (TextView)v.findViewById(R.id.author);
				holder.dateView = (TextView) v.findViewById(R.id.date);
				holder.selectionBoxView = (CheckBox) v.findViewById(R.id.selected);
				holder.menuButtonView = (ImageView) v.findViewById(R.id.article_menu_button);
				holder.flavorImageHolder = (ViewGroup) v.findViewById(R.id.flavorImageHolder);
                holder.flavorImageLoadingBar = (ProgressBar) v.findViewById(R.id.flavorImageLoadingBar);
                holder.flavorImageArrow = v.findViewById(R.id.flavorImageArrow);
                holder.headlineFooter = v.findViewById(R.id.headline_footer);
                holder.textImage = (ImageView) v.findViewById(R.id.text_image);
                holder.textChecked = (ImageView) v.findViewById(R.id.text_checked);
				
				v.setTag(holder);
				
				// http://code.google.com/p/android/issues/detail?id=3414
				((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			} else {
				holder = (HeadlineViewHolder) v.getTag();
			}

            // block footer clicks to make button/selection clicking easier
            if (holder.headlineFooter != null) {
                holder.headlineFooter.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //
                    }
                });
            }

            if (holder.textImage != null) {
                updateTextCheckedState(holder, article);

                holder.textImage.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "textImage : onclicked");

                        article.selected = !article.selected;

                        updateTextCheckedState(holder, article);

                        m_listener.onArticleListSelectionChange(getSelectedArticles());

                        Log.d(TAG, "num selected: " + getSelectedArticles().size());
                    }
                });

            }

			if (holder.titleView != null) {				
				holder.titleView.setText(Html.fromHtml(article.title));
				
				/* if (m_prefs.getBoolean("enable_condensed_fonts", false)) {
					Typeface tf = TypefaceCache.get(m_activity, "sans-serif-condensed", article.unread ? Typeface.BOLD : Typeface.NORMAL);
					
					if (tf != null && !tf.equals(holder.titleView.getTypeface())) {
						holder.titleView.setTypeface(tf);
					}
					
					holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 5));
				} else {
					holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));
				} */

                holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));

				adjustTitleTextView(article.score, holder.titleView, position);
			}

			if (holder.feedTitleView != null) {				
				if (article.feed_title != null && (m_feed.is_cat || m_feed.id < 0)) {
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

            String articleContentReduced = articleContent.length() > CommonActivity.EXCERPT_MAX_QUERY_LENGTH ?
                    articleContent.substring(0, CommonActivity.EXCERPT_MAX_QUERY_LENGTH) : articleContent;

            Document articleDoc = Jsoup.parse(articleContentReduced);

            if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
                    String excerpt;

                    if (m_activity.getApiLevel() >= 11) {
                        excerpt = article.excerpt != null ? article.excerpt : "";
                        excerpt = excerpt.replace("&hellip;", "…");
                        excerpt = excerpt.replace("]]>", "");
                        excerpt = Jsoup.parse(excerpt).text();
                    } else {
                        excerpt = articleDoc.text();

                        if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH)
                            excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";
                    }

					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);
				}
			}

            if (!m_compactLayoutMode) {
                boolean showFlavorImage = "HL_DEFAULT".equals(m_prefs.getString("headline_mode", "HL_DEFAULT"));

                if (holder.flavorImageView != null && showFlavorImage) {
                    holder.flavorImageArrow.setVisibility(View.GONE);

                    boolean loadableImageFound = false;

                    if (articleDoc != null) {
                        final Elements imgs = articleDoc.select("img");
                        Element img = null;

                        for (Element tmp : imgs) {
                            try {
                                if (Integer.valueOf(tmp.attr("width")) > FLAVOR_IMG_MIN_WIDTH && Integer.valueOf(tmp.attr("width")) > FLAVOR_IMG_MIN_HEIGHT) {
                                    img = tmp;
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                //
                            }
                        }

                        if (img == null)
                            img = imgs.first();

                        if (img != null) {
                            String imgSrc = img.attr("src");
                            final String imgSrcFirst = imgSrc;

                            // retarded schema-less urls
                            if (imgSrc.indexOf("//") == 0)
                                imgSrc = "http:" + imgSrc;

                            DisplayImageOptions options = new DisplayImageOptions.Builder()
                                    .cacheInMemory(true)
                                    .resetViewBeforeLoading(true)
                                    .cacheOnDisk(true)
                                    .build();

                            ViewCompat.setTransitionName(holder.flavorImageView, "TRANSITION:ARTICLE_IMAGES_PAGER");

                            holder.flavorImageView.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    Intent intent = new Intent(m_activity, ArticleImagesPagerActivity.class);
                                    intent.putExtra("firstSrc", imgSrcFirst);
                                    intent.putExtra("title", article.title);
                                    intent.putExtra("content", article.content);

                                    ActivityOptionsCompat options =
                                    ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
                                            holder.flavorImageView,   // The view which starts the transition
                                            "TRANSITION:ARTICLE_IMAGES_PAGER" // The transitionName of the view we’re transitioning to
                                            );
                                    ActivityCompat.startActivity(m_activity, intent, options.toBundle());

                                    //startActivityForResult(intent, 0);
                                }
                            });

                            final ViewGroup flavorImageHolder = holder.flavorImageHolder;
                            final ImageView flavorImageView = holder.flavorImageView;
                            final ProgressBar flavorImageLoadingBar = holder.flavorImageLoadingBar;

                            ImageAware imageAware = new ImageViewAware(holder.flavorImageView, false);

                            flavorImageHolder.setVisibility(View.VISIBLE);

                            if (imgs.size() > 1 && holder.flavorImageArrow != null) {
                                holder.flavorImageArrow.setVisibility(View.VISIBLE);
                            }

                            final boolean weNeedAnimation = MemoryCacheUtils.findCachedBitmapsForImageUri(imgSrc, ImageLoader.getInstance().getMemoryCache()).size() == 0;

                            loadableImageFound = true;
                            ImageLoader.getInstance().displayImage(imgSrc, imageAware, options, new ImageLoadingListener() {

                                @Override
                                public void onLoadingCancelled(String arg0,
                                                               View arg1) {
                                    // TODO Auto-generated method stub

                                }

                                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                                @Override
                                public void onLoadingComplete(String arg0,
                                                              View arg1, Bitmap arg2) {
                                    // TODO Auto-generated method stub

                                    if (!isAdded() || arg2 == null) return;

                                    flavorImageLoadingBar.setVisibility(View.INVISIBLE);

                                    if (arg2.getWidth() > FLAVOR_IMG_MIN_WIDTH && arg2.getHeight() > FLAVOR_IMG_MIN_HEIGHT) {
                                        if (weNeedAnimation) {
                                            ObjectAnimator anim = ObjectAnimator.ofFloat(flavorImageView, "alpha", 0f, 1f);
                                            anim.setDuration(200);
                                            anim.start();
                                        }
                                        //flavorImageHolder.setVisibility(View.VISIBLE);
                                    } else {
                                        flavorImageHolder.setVisibility(View.GONE);
                                    }
                                }

                                @Override
                                public void onLoadingFailed(String arg0,
                                                            View arg1, FailReason arg2) {
                                    // TODO Auto-generated method stub
                                    flavorImageHolder.setVisibility(View.GONE);
                                }

                                @Override
                                public void onLoadingStarted(String arg0,
                                                             View arg1) {
                                    // TODO Auto-generated method stub

                                }

                            });
                        }
                    }

                    if (!loadableImageFound && holder.flavorImageHolder != null) {
                        holder.flavorImageHolder.setVisibility(View.GONE);
                    }
                } else if (holder.flavorImageHolder != null) {
                    holder.flavorImageHolder.setVisibility(View.GONE);
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
                Date now = new Date();

                DateFormat df;

                if (now.getYear() == d.getYear() && now.getMonth() == d.getMonth() && now.getDay() == d.getDay()) {
                    df = new SimpleDateFormat("HH:mm");
                } else {
                    df = new SimpleDateFormat("MMM dd");
                }

				df.setTimeZone(TimeZone.getDefault());
				holder.dateView.setText(df.format(d));
			}
			

			if (holder.selectionBoxView != null) {
				holder.selectionBoxView.setChecked(article.selected);
				holder.selectionBoxView.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View view) {
						CheckBox cb = (CheckBox)view;
						
						if (cb.isChecked()) {
							article.selected = true;
						} else {
							article.selected = false;
						}
						
						m_listener.onArticleListSelectionChange(getSelectedArticles());
						
						Log.d(TAG, "num selected: " + getSelectedArticles().size());
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

			return v;
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

    // if setting active doesn't make sense, scroll to whatever is passed to us
	public void setActiveArticle(Article article) {
		if (article != m_activeArticle && article != null) {

            // only set active article when it makes sense (in HeadlinesActivity)
            if (getActivity() instanceof HeadlinesActivity) {
                m_activeArticle = article;
            }

            m_adapter.notifyDataSetChanged();

			ListView list = (ListView)getView().findViewById(R.id.headlines_list);

			if (list != null) {
				int position = getArticlePositionById(article.id);

                if (position != -1) {
                    list.smoothScrollToPosition(position);
                }
			}
		}
	}

	public void setSelection(ArticlesSelection select) {
		for (Article a : m_articles)
            a.selected = false;
		
		if (select != ArticlesSelection.NONE) {
			for (Article a : m_articles) {
				if (select == ArticlesSelection.ALL || select == ArticlesSelection.UNREAD && a.unread) {
					a.selected = true;
				}
			}
		}

        if (m_adapter != null) {
            m_adapter.notifyDataSetChanged();
        }
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
	}

	public Article getActiveArticle() {
		return m_activeArticle;
	}

    public int getArticlePositionById(int id) {
        for (Article a : m_adapter.items) {
            if (a.id == id) {
                return m_adapter.getPosition(a);
            }
        }

        return -1;
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

    public ArticleList getArticles() {
        return m_articles;
    }

    public void setArticles(ArticleList articles) {
        m_articles.clear();
        m_articles.addAll(articles);
        m_adapter.notifyDataSetChanged();
    }
}
