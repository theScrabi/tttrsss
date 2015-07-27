package org.fox.ttrss;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.google.gson.JsonElement;
import com.nhaarman.listviewanimations.appearance.AnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.SwingBottomInAnimationAdapter;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.display.RoundedBitmapDisplayer;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.shamanland.fab.FloatingActionButton;
import com.shamanland.fab.ShowHideOnScroll;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeadlinesFragment extends Fragment implements OnItemClickListener, OnScrollListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD }

    public static final int FLAVOR_IMG_MIN_SIZE = 128;
	public static final int THUMB_IMG_MIN_SIZE = 32;

    public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;

	public static final int ARTICLE_SPECIAL_LOADMORE = -1;
	public static final int ARTICLE_SPECIAL_SPACER = -2;
	public static final int ARTICLE_SPECIAL_TOP_CHANGED = -3;

	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private Article m_activeArticle;
	private String m_searchQuery = "";
	private boolean m_refreshInProgress = false;
	private boolean m_autoCatchupDisabled = false;
	private int m_firstId = 0;

	private SharedPreferences m_prefs;
	
	private ArticleListAdapter m_adapter;
	private AnimationAdapter m_animationAdapter;
	private ArticleList m_articles = new ArticleList(); //Application.getInstance().m_loadedArticles;
	//private ArticleList m_selectedArticles = new ArticleList();
	private ArticleList m_readArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
	private int m_maxImageSize = 0;
    private boolean m_compactLayoutMode = false;
    private int m_listPreviousVisibleItem;
    private ListView m_list;
	private ImageLoader m_imageLoader = ImageLoader.getInstance();

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

		getActivity().getMenuInflater().inflate(R.menu.context_headlines, menu);

		if (getSelectedArticles().size() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
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

            if (! (m_activity instanceof DetailActivity)) {
                m_articles = savedInstanceState.getParcelable("articles");
            } else {
                m_articles = ((DetailActivity)m_activity).m_articles;
            }

			m_activeArticle = savedInstanceState.getParcelable("activeArticle");
			//m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
            m_compactLayoutMode = savedInstanceState.getBoolean("compactLayoutMode");
			m_firstId = savedInstanceState.getInt("firstId");
		}

		String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");

        if ("HL_COMPACT".equals(headlineMode) || "HL_COMPACT_NOIMAGES".equals(headlineMode))
            m_compactLayoutMode = true;

		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
		m_maxImageSize = (int) (128 * metrics.density + 0.5);

		Log.d(TAG, "maxImageSize=" + m_maxImageSize);
		
		View view = inflater.inflate(R.layout.fragment_headlines, container, false);

		m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.headlines_swipe_container);

	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh(false, true);
			}
		});

		m_list = (ListView)view.findViewById(R.id.headlines_list);

		FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.headlines_fab);
		m_list.setOnTouchListener(new ShowHideOnScroll(fab));
		fab.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				refresh(false);
			}
		});

        if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int screenHeight = display.getHeight();

            View layout = inflater.inflate(R.layout.headlines_footer, container, false);

			layout.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));

            m_list.addFooterView(layout, null, false);
        }

        if (m_activity.isSmallScreen()) {
            View layout = inflater.inflate(R.layout.headlines_heading_spacer, m_list, false);
            m_list.addHeaderView(layout);

            m_swipeLayout.setProgressViewOffset(false, 0,
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material) +
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_padding_material));
        }

		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);
		m_animationAdapter = new SwingBottomInAnimationAdapter(m_adapter);

		m_animationAdapter.setAbsListView(m_list);
		m_list.setAdapter(m_animationAdapter);

		m_list.setOnItemClickListener(this);
		m_list.setOnScrollListener(this);
		registerForContextMenu(m_list);

        if (m_activity.isSmallScreen()) {
            m_activity.setTitle(m_feed.title);
        }

		Log.d(TAG, "onCreateView, feed=" + m_feed);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (m_adapter != null) m_adapter.notifyDataSetChanged();

		/* if (Application.getInstance().m_activeArticle != null) {
			m_activeArticle = Application.getInstance().m_activeArticle;
			Application.getInstance().m_activeArticle = null;
		} */

		if (m_activeArticle != null) {
			setActiveArticle(m_activeArticle);
		}

        /* if (!(m_activity instanceof DetailActivity)) {
            refresh(false);
        } */

        if (m_articles.size() == 0) {
            refresh(false);
        }

		/* if (m_articles.size() == 0 || !m_feed.equals(Application.getInstance().m_activeFeed)) {
			if (m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE) == null) {
				refresh(false);
				Application.getInstance().m_activeFeed = m_feed;
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
				
				// only set active article when it makes sense (in DetailActivity)
				if (getActivity() instanceof DetailActivity) {
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

			/* if (!m_feed.equals(Application.getInstance().m_activeFeed)) {
				append = false;
			} */

			// new stuff may appear on top, scroll back to show it
			if (!append) {
				if (getView() != null) {
					Log.d(TAG, "scroll hack");
					m_autoCatchupDisabled = true;
					m_list.setSelection(0);
					m_autoCatchupDisabled = false;
					m_animationAdapter.reset();
					m_articles.clear();
					m_adapter.notifyDataSetChanged();
				}
			}
			
			final boolean fappend = append;
			final String sessionId = m_activity.getSessionId();
			final boolean isCat = m_feed.is_cat;
			
			HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity, m_feed, m_articles) {
				@Override
				protected void onProgressUpdate(Integer... progress) {
					m_activity.setProgress(Math.round((((float) progress[0] / (float) progress[1]) * 10000)));
				}

				@Override
				protected void onPostExecute(JsonElement result) {
					if (isDetached() || !isAdded()) return;
					
					super.onPostExecute(result);

					if (isAdded()) {
                        if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
					} 

					if (result != null) {
						m_refreshInProgress = false;
						m_articles.add(0, new Article(ARTICLE_SPECIAL_SPACER));

						if (m_articles.indexOf(m_activeArticle) == -1)
							m_activeArticle = null;

						if (m_firstIdChanged) {
							m_articles.add(new Article(ARTICLE_SPECIAL_TOP_CHANGED));
						}

						HeadlinesFragment.this.m_firstId = m_firstId;

						m_adapter.notifyDataSetChanged();
						m_listener.onHeadlinesLoaded(fappend);

						// not sure why but listview sometimes gets positioned while ignoring the header so
						// top headline content becomes partially obscured by the toolbar on phones
						// (not reproducible on avd)
						if (!fappend) m_list.smoothScrollToPosition(0);

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

					if (m_firstId > 0) put("check_first_id", String.valueOf(m_firstId));

					if (m_activity.getApiLevel() >= 12) {
						put("include_header", "true");
					}
				}
			};

            Log.d(TAG, "[HP] request more headlines, firstId=" + m_firstId);

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
		out.putInt("firstId", m_firstId);
	}

	static class HeadlineViewHolder {
		public TextView titleView;
		public TextView feedTitleView;
		public ImageView markedView;
		public ImageView publishedView;
		public TextView excerptView;
		public ImageView flavorImageView;
		public ImageView flavorVideoKindView;
		public TextView authorView;
		public TextView dateView;
		public CheckBox selectionBoxView;
		public ImageView menuButtonView;
		public ViewGroup flavorImageHolder;
		public ProgressBar flavorImageLoadingBar;
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
		public View headlineHeader;
		public View topChangedMessage;

		public boolean flavorImageEmbedded;
	}
	
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		public static final int VIEW_SPACER = 5;
		public static final int VIEW_TOP_CHANGED = 6;
		
		public static final int VIEW_COUNT = VIEW_TOP_CHANGED+1;
		
		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;

        private ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();
		private final DisplayImageOptions displayImageOptions;
		boolean showFlavorImage;
		private int m_minimumHeightToEmbed;
		boolean m_youtubeInstalled;

		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;

			Display display = m_activity.getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			m_minimumHeightToEmbed = size.y/3;

			String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
			showFlavorImage = "HL_DEFAULT".equals(headlineMode) || "HL_COMPACT".equals(headlineMode);

			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;

			displayImageOptions = new DisplayImageOptions.Builder()
					.cacheInMemory(true)
					.resetViewBeforeLoading(true)
					.cacheOnDisk(true)
					.displayer(new FadeInBitmapDisplayer(500))
					.build();

			List<ApplicationInfo> packages = m_activity.getPackageManager().getInstalledApplications(0);
			for (ApplicationInfo pi : packages) {
				if (pi.packageName.equals("com.google.android.youtube")) {
					m_youtubeInstalled = true;
					break;
				}
			}
		}
		
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);

			// the special invisible SPACER is here because listview animations apparently glitch if there's only one headline loaded
			// so we add an invisible second one i guess

			if (a.id == ARTICLE_SPECIAL_LOADMORE) {
				return VIEW_LOADMORE;
			} else if (a.id == ARTICLE_SPECIAL_TOP_CHANGED) {
				return VIEW_TOP_CHANGED;
			} else if (a.id == ARTICLE_SPECIAL_SPACER) {
				return VIEW_SPACER;
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

        private void updateTextCheckedState(final HeadlineViewHolder holder, Article item) {
            String tmp = item.title.length() > 0 ? item.title.substring(0, 1).toUpperCase() : "?";

            if (item.selected) {
				holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));
				holder.textImage.setTag(null);

                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
				final Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(item.title));

				if (item.flavorImage == null) {
					holder.textImage.setImageDrawable(textDrawable);
					holder.textImage.setTag(null);
				} else {
					String imgSrc = item.flavorImage.attr("src");

					// retarded schema-less urls
					if (imgSrc.indexOf("//") == 0)
						imgSrc = "http:" + imgSrc;

					if (!imgSrc.equals(holder.textImage.getTag())) {

						holder.textImage.setTag("LOADING:" + imgSrc);
						ImageAware imageAware = new ImageViewAware(holder.textImage, false);

						DisplayImageOptions options = new DisplayImageOptions.Builder()
								.cacheInMemory(true)
								.resetViewBeforeLoading(true)
								.cacheOnDisk(true)
								.showImageOnLoading(textDrawable)
								.showImageOnFail(textDrawable)
								.showImageForEmptyUri(textDrawable)
								.displayer(new RoundedBitmapDisplayer(100))
								.build();

						final String finalImgSrc = imgSrc;
						m_imageLoader.displayImage(imgSrc, imageAware, options, new ImageLoadingListener() {
									@Override
									public void onLoadingStarted(String s, View view) {

									}

									@Override
									public void onLoadingFailed(String s, View view, FailReason failReason) {

									}

									@Override
									public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
										if (("LOADING:" + imageUri).equals(view.getTag()) && bitmap != null) {
											holder.textImage.setTag(finalImgSrc);

											if (bitmap.getWidth() < THUMB_IMG_MIN_SIZE || bitmap.getHeight() < THUMB_IMG_MIN_SIZE) {
												holder.textImage.setImageDrawable(textDrawable);
											}
										}
									}

									@Override
									public void onLoadingCancelled(String s, View view) {

									}
								}
						);


					}
				}

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
				case VIEW_SPACER:
					layoutId = R.layout.fragment_dummy;
					break;
				case VIEW_TOP_CHANGED:
					layoutId = R.layout.headlines_row_top_changed;
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
				holder.flavorVideoKindView = (ImageView) v.findViewById(R.id.flavor_video_kind);
				holder.authorView = (TextView)v.findViewById(R.id.author);
				holder.dateView = (TextView) v.findViewById(R.id.date);
				holder.selectionBoxView = (CheckBox) v.findViewById(R.id.selected);
				holder.menuButtonView = (ImageView) v.findViewById(R.id.article_menu_button);
				holder.flavorImageHolder = (ViewGroup) v.findViewById(R.id.flavorImageHolder);
                holder.flavorImageLoadingBar = (ProgressBar) v.findViewById(R.id.flavorImageLoadingBar);
                holder.headlineFooter = v.findViewById(R.id.headline_footer);
                holder.textImage = (ImageView) v.findViewById(R.id.text_image);
                holder.textChecked = (ImageView) v.findViewById(R.id.text_checked);
				holder.headlineHeader = v.findViewById(R.id.headline_header);
				holder.topChangedMessage = v.findViewById(R.id.headlines_row_top_changed);

				v.setTag(holder);

				// http://code.google.com/p/android/issues/detail?id=3414
				((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			} else {
				holder = (HeadlineViewHolder) v.getTag();
			}

			String articleContent = article.content != null ? article.content : "";

			String articleContentReduced = articleContent.length() > CommonActivity.EXCERPT_MAX_QUERY_LENGTH ?
					articleContent.substring(0, CommonActivity.EXCERPT_MAX_QUERY_LENGTH) : articleContent;

			if (article.articleDoc == null)
				article.articleDoc = Jsoup.parse(articleContentReduced);

			// block footer clicks to make button/selection clicking easier
            if (holder.headlineFooter != null) {
                holder.headlineFooter.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //
                    }
                });
            }

			if (holder.topChangedMessage != null) {
				holder.topChangedMessage.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						refresh(false, true);
					}
				});
			}

			if (showFlavorImage && article.flavorImage == null) {

				Elements imgs = article.articleDoc.select("img");

				for (Element tmp : imgs) {
					try {
						if (tmp.attr("src") != null && tmp.attr("src").indexOf("data:") == 0) {
							continue;
						}

						if (Integer.valueOf(tmp.attr("width")) > FLAVOR_IMG_MIN_SIZE && Integer.valueOf(tmp.attr("width")) > FLAVOR_IMG_MIN_SIZE) {
							article.flavorImage = tmp;
							break;
						}

					} catch (NumberFormatException e) {
						//
					}
				}

				if (article.flavorImage == null)
					article.flavorImage = imgs.first();

				article.flavorImageCount = imgs.size();
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
				ViewCompat.setTransitionName(holder.textImage, "TRANSITION:ARTICLE_IMAGES_PAGER");

				if (article.flavorImage != null) {
					final String imgSrcFirst = article.flavorImage.attr("src");

					holder.textImage.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {

							Intent intent = new Intent(m_activity, ArticleImagesPagerActivity.class);
							intent.putExtra("firstSrc", imgSrcFirst);
							intent.putExtra("title", article.title);
							intent.putExtra("content", article.content);

							ActivityOptionsCompat options =
									ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
											holder.textImage,   // The view which starts the transition
											"TRANSITION:ARTICLE_IMAGES_PAGER" // The transitionName of the view we’re transitioning to
									);
							ActivityCompat.startActivity(m_activity, intent, options.toBundle());

							return true;
						}
					});

				}

            }

			if (holder.titleView != null) {
				holder.titleView.setText(Html.fromHtml(article.title));
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

			TypedValue tvAccent = new TypedValue();
			m_activity.getTheme().resolveAttribute(R.attr.colorAccent, tvAccent, true);

			if (holder.markedView != null) {
				TypedValue tv = new TypedValue();
				m_activity.getTheme().resolveAttribute(article.marked ? R.attr.ic_star : R.attr.ic_star_outline, tv, true);

				holder.markedView.setImageResource(tv.resourceId);

				if (article.marked)
					holder.markedView.setColorFilter(tvAccent.data);
				else
					holder.markedView.setColorFilter(null);

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
				TypedValue tv = new TypedValue();
				m_activity.getTheme().resolveAttribute(article.published ? R.attr.ic_checkbox_marked : R.attr.ic_rss_box, tv, true);

				holder.publishedView.setImageResource(tv.resourceId);

				if (article.published)
					holder.publishedView.setColorFilter(tvAccent.data);
				else
					holder.publishedView.setColorFilter(null);

				holder.publishedView.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						article.published = !article.published;
						m_adapter.notifyDataSetChanged();

						m_activity.saveArticlePublished(article);
					}
				});
			}

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
                        excerpt = article.articleDoc.text();

                        if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH)
                            excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";
                    }

					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);

					if (!showFlavorImage) {
						holder.excerptView.setPadding(holder.excerptView.getPaddingLeft(),
								0,
								holder.excerptView.getPaddingRight(),
								holder.excerptView.getPaddingBottom());
					}
				}
			}

            if (!m_compactLayoutMode && holder.flavorImageHolder != null) {

				/* reset to default in case of convertview */
				holder.flavorImageLoadingBar.setVisibility(View.GONE);
				holder.flavorImageView.setVisibility(View.GONE);
				holder.flavorVideoKindView.setVisibility(View.GONE);
				holder.headlineHeader.setBackgroundDrawable(null);

				holder.headlineHeader.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						m_activity.openContextMenu(v);
						return true;
					}
				});

				holder.headlineHeader.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						m_listener.onArticleSelected(article);
					}
				});

				holder.flavorImageView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						m_activity.openContextMenu(v);
						return true;
					}
				});

				boolean videoFound = false;

				if (showFlavorImage && article.articleDoc != null && holder.flavorVideoKindView != null) {
					Element video = article.articleDoc.select("video").first();
					Element ytframe = article.articleDoc.select("iframe[src*=youtube.com/embed/]").first();

					if (video != null) {
						try {
							Element source = video.select("source").first();

							final String streamUri = source.attr("src");
							final String posterUri = video.attr("poster");

							if (streamUri.length() > 0 && posterUri.length() > 0) {

								if (!posterUri.equals(holder.flavorImageView.getTag())) {

									holder.flavorImageView.setTag("LOADING:" + posterUri);
									ImageAware imageAware = new ImageViewAware(holder.flavorImageView, false);

									m_imageLoader.displayImage(posterUri, imageAware, displayImageOptions, new ImageLoadingListener() {
											@Override
											public void onLoadingStarted(String s, View view) {
												holder.flavorImageLoadingBar.setVisibility(View.VISIBLE);
											}

											@Override
											public void onLoadingFailed(String s, View view, FailReason failReason) {
												holder.flavorImageLoadingBar.setVisibility(View.GONE);
											}

											@Override
											public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
												if (("LOADING:" + imageUri).equals(view.getTag()) && bitmap != null) {

													holder.flavorImageLoadingBar.setVisibility(View.GONE);
													holder.flavorImageView.setTag(posterUri);
													holder.flavorImageView.setVisibility(View.VISIBLE);
													holder.flavorVideoKindView.setVisibility(View.VISIBLE);

													maybeRepositionFlavorImage(view, bitmap, holder);
												}
											}

											@Override
											public void onLoadingCancelled(String s, View view) {
												holder.flavorImageLoadingBar.setVisibility(View.GONE);
											}
										}
									, new ImageLoadingProgressListener() {
										@Override
										public void onProgressUpdate(String s, View view, int current, int total) {
											if (total != 0) {
												int p = (int)((float)current/total*100);

												holder.flavorImageLoadingBar.setIndeterminate(false);
												holder.flavorImageLoadingBar.setProgress(p);
											} else {
												holder.flavorImageLoadingBar.setIndeterminate(true);
											}
										}
									});

								} else {
									holder.flavorImageView.setVisibility(View.VISIBLE);
									holder.flavorVideoKindView.setVisibility(View.VISIBLE);

									if (holder.flavorImageEmbedded) {
										TypedValue tv = new TypedValue();
										if (m_activity.getTheme().resolveAttribute(R.attr.headlineHeaderBackground, tv, true)) {
											holder.headlineHeader.setBackgroundColor(tv.data);
										}
									}

								}

								videoFound = true;

								holder.flavorVideoKindView.setImageResource(R.drawable.ic_play_circle);

								//ViewCompat.setTransitionName(holder.flavorImageView, "TRANSITION:ARTICLE_VIDEO_PLAYER");

								holder.flavorImageView.setOnClickListener(new OnClickListener() {
									@Override
									public void onClick(View v) {

										Intent intent = new Intent(m_activity, VideoPlayerActivity.class);
										intent.putExtra("streamUri", streamUri);
										intent.putExtra("title", article.title);

										/*ActivityOptionsCompat options =
												ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
														holder.flavorImageView,   // The view which starts the transition
														"TRANSITION:ARTICLE_VIDEO_PLAYER" // The transitionName of the view we’re transitioning to
												);
										ActivityCompat.startActivity(m_activity, intent, options.toBundle());*/

										startActivity(intent);
									}
								});

								// ONCLICK open video player

							}
						} catch (Exception e) {
							e.printStackTrace();
							videoFound = false;
						}
					} else if (ytframe != null) {
						// thumb: http://img.youtube.com/vi/{VID}/mqdefault.jpg
						String srcEmbed = ytframe.attr("src");

						if (srcEmbed.length() > 0) {
							Pattern pattern = Pattern.compile("/embed/([\\w-]+)");
							Matcher matcher = pattern.matcher(srcEmbed);

							if (matcher.find()) {
								final String vid = matcher.group(1);
								final String thumbUri = "http://img.youtube.com/vi/"+vid+"/mqdefault.jpg";
								final String videoUri = "https://youtu.be/" + vid;

								videoFound = true;

								holder.flavorVideoKindView.setImageResource(R.drawable.ic_youtube_play);

								if (!thumbUri.equals(holder.flavorImageView.getTag())) {
									holder.flavorImageView.setTag("LOADING:" + thumbUri);

									ImageAware imageAware = new ImageViewAware(holder.flavorImageView, false);
									m_imageLoader.displayImage(thumbUri, imageAware, displayImageOptions, new ImageLoadingListener() {
										@Override
										public void onLoadingStarted(String s, View view) {
											holder.flavorImageLoadingBar.setVisibility(View.VISIBLE);
										}

										@Override
										public void onLoadingFailed(String s, View view, FailReason failReason) {
											holder.flavorImageLoadingBar.setVisibility(View.GONE);
										}

										@Override
										public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
											if (("LOADING:" + imageUri).equals(view.getTag()) && bitmap != null) {
												holder.flavorImageLoadingBar.setVisibility(View.GONE);
												holder.flavorImageView.setTag(thumbUri);
												holder.flavorImageView.setVisibility(View.VISIBLE);
												holder.flavorVideoKindView.setVisibility(View.VISIBLE);

												maybeRepositionFlavorImage(view, bitmap, holder);
											}
										}

										@Override
										public void onLoadingCancelled(String s, View view) {
											holder.flavorImageLoadingBar.setVisibility(View.GONE);
										}
									}
									, new ImageLoadingProgressListener() {
										@Override
										public void onProgressUpdate(String s, View view, int current, int total) {
											if (total != 0) {
												int p = (int)((float)current/total*100);

												holder.flavorImageLoadingBar.setIndeterminate(false);
												holder.flavorImageLoadingBar.setProgress(p);
											} else {
												holder.flavorImageLoadingBar.setIndeterminate(true);
											}
										}
									});
								} else {
									holder.flavorImageView.setVisibility(View.VISIBLE);
									holder.flavorVideoKindView.setVisibility(View.VISIBLE);

									if (holder.flavorImageEmbedded) {
										TypedValue tv = new TypedValue();
										if (m_activity.getTheme().resolveAttribute(R.attr.headlineHeaderBackground, tv, true)) {
											holder.headlineHeader.setBackgroundColor(tv.data);
										}
									}

								}


								holder.flavorImageView.setOnClickListener(new OnClickListener() {
									@Override
									public void onClick(View v) {

										if (m_youtubeInstalled) {
											Intent intent = new Intent(m_activity, YoutubePlayerActivity.class);
											intent.putExtra("streamUri", videoUri);
											intent.putExtra("vid", vid);
											intent.putExtra("title", article.title);

											startActivity(intent);
										} else {
											Intent intent = new Intent(Intent.ACTION_VIEW,
													Uri.parse(videoUri));
											startActivity(intent);
										}
									}
								});
							}
						}
					}

				}

				if (!videoFound && showFlavorImage && holder.flavorImageView != null) {

					if (article.articleDoc != null) {

						if (article.flavorImage != null) {
							String imgSrc = article.flavorImage.attr("src");
							final String imgSrcFirst = imgSrc;

							// retarded schema-less urls
							if (imgSrc.indexOf("//") == 0)
								imgSrc = "http:" + imgSrc;

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

							if (!imgSrc.equals(holder.flavorImageView.getTag())) {

								holder.flavorImageView.setTag("LOADING:" + imgSrc);
								ImageAware imageAware = new ImageViewAware(holder.flavorImageView, false);

								final String finalImgSrc = imgSrc;
								m_imageLoader.displayImage(imgSrc, imageAware, displayImageOptions, new ImageLoadingListener() {

									@Override
									public void onLoadingCancelled(String arg0,
																   View arg1) {

										//
									}

									@Override
									public void onLoadingComplete(String imageUri,
																  View view, Bitmap bitmap) {

										if (("LOADING:" + imageUri).equals(view.getTag()) && bitmap != null) {

											holder.flavorImageLoadingBar.setVisibility(View.GONE);
											holder.flavorImageView.setTag(finalImgSrc);

											if (bitmap.getWidth() > FLAVOR_IMG_MIN_SIZE && bitmap.getHeight() > FLAVOR_IMG_MIN_SIZE) {
												holder.flavorImageView.setVisibility(View.VISIBLE);

												if (article.flavorImageCount > 1) {
													holder.flavorVideoKindView.setVisibility(View.VISIBLE);
													holder.flavorVideoKindView.setImageResource(R.drawable.ic_image_album);
												}

												maybeRepositionFlavorImage(view, bitmap, holder);
											} else {
												holder.flavorImageView.setImageDrawable(null);
											}
										}
									}

									@Override
									public void onLoadingFailed(String arg0,
																View arg1, FailReason arg2) {

										holder.flavorImageLoadingBar.setVisibility(View.GONE);
										holder.flavorImageView.setVisibility(View.GONE);
									}

									@Override
									public void onLoadingStarted(String arg0,
																 View arg1) {
										holder.flavorImageLoadingBar.setVisibility(View.VISIBLE);
									}

								}, new ImageLoadingProgressListener() {
									@Override
									public void onProgressUpdate(String s, View view, int current, int total) {
										if (total != 0) {
											int p = (int)((float)current/total*100);

											holder.flavorImageLoadingBar.setIndeterminate(false);
											holder.flavorImageLoadingBar.setProgress(p);
										} else {
											holder.flavorImageLoadingBar.setIndeterminate(true);
										}
									}
								});

							} else {
								holder.flavorImageView.setVisibility(View.VISIBLE);

								if (article.flavorImageCount > 1) {
									holder.flavorVideoKindView.setVisibility(View.VISIBLE);
									holder.flavorVideoKindView.setImageResource(R.drawable.ic_image_album);
								}

								if (holder.flavorImageEmbedded) {
									TypedValue tv = new TypedValue();
									if (m_activity.getTheme().resolveAttribute(R.attr.headlineHeaderBackground, tv, true)) {
										holder.headlineHeader.setBackgroundColor(tv.data);
									}
								}

							}
						}
					}
				}
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

		public int pxToDp(int px) {
			DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
			int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
			return dp;
		}

		public int dpToPx(int dp) {
			DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
			int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
			return px;
		}

		private void maybeRepositionFlavorImage(View view, Bitmap bitmap, HeadlineViewHolder holder) {
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();

			int w = bitmap.getWidth();
			int h = bitmap.getHeight();
			float r = h != 0 ? (float)w/h : 0;

			//Log.d(TAG, "XYR: " + pxToDp(w) + " " + pxToDp(h) + " " + r);

			if (bitmap.getHeight() < m_minimumHeightToEmbed || r >= 1.2) {

				lp.addRule(RelativeLayout.BELOW, R.id.headline_header);

				holder.headlineHeader.setBackgroundDrawable(null);
				holder.flavorImageEmbedded = false;

			} else {
				lp.addRule(RelativeLayout.BELOW, 0);

				TypedValue tv = new TypedValue();
				if (m_activity.getTheme().resolveAttribute(R.attr.headlineHeaderBackground, tv, true)) {
					holder.headlineHeader.setBackgroundColor(tv.data);
				}

				holder.flavorImageEmbedded = true;
			}

			view.setLayoutParams(lp);
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
		ArticleList tmp = (ArticleList) m_articles.clone();

		tmp.remove(0);

		return tmp;
	}

    // if setting active doesn't make sense, scroll to whatever is passed to us
	public void setActiveArticle(Article article) {
		if (article != m_activeArticle && article != null) {

            // only set active article when it makes sense (in DetailActivity)
            if (getActivity() instanceof DetailActivity) {
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
            return (Article) m_list.getItemAtPosition(position);
        } catch (ClassCastException e) {
            return null;
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
		if (!m_refreshInProgress && m_articles.findById(ARTICLE_SPECIAL_LOADMORE) != null && firstVisibleItem + visibleItemCount == m_articles.size()) {
			refresh(true);
		}

		if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > (m_activity.isSmallScreen() ? 1 : 0) && !m_autoCatchupDisabled) {
			Article a = (Article) view.getItemAtPosition(firstVisibleItem - 1);

			if (a != null && a.unread) {
                Log.d(TAG, "title=" + a.title);

				a.unread = false;
				m_readArticles.add(a);
				m_feed.unread--;
			} 
		}

        if (!m_activity.isTablet()) {
            if (m_adapter.getCount() > 0) {
                if (firstVisibleItem > m_listPreviousVisibleItem) {
                    m_activity.getSupportActionBar().hide();
                } else if (firstVisibleItem < m_listPreviousVisibleItem) {
                    m_activity.getSupportActionBar().show();
                }
            } else {
				m_activity.getSupportActionBar().show();
            }

            m_listPreviousVisibleItem = firstVisibleItem;
        }
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
			m_imageLoader.pause();
		} else {
			m_imageLoader.resume();
		}

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
                return m_adapter.getPosition(a) + m_list.getHeaderViewsCount();
            }
        }

        return -1;
    }

	/* public int getArticlePosition(Article article) {
		try {
			return m_adapter.getPosition(article);
		} catch (NullPointerException e) {
			return -1;
		}
	} */

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

    /*public ArticleList getArticles() {
        return m_articles;
    }*/

    public void setArticles(ArticleList articles) {
        m_articles.clear();
		m_articles.add(0, new Article(ARTICLE_SPECIAL_SPACER));
        m_articles.addAll(articles);
        m_adapter.notifyDataSetChanged();
    }
}
