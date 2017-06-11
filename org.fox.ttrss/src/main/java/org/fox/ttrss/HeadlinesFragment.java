package org.fox.ttrss;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources.Theme;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.google.gson.JsonElement;
import com.shamanland.fab.FloatingActionButton;
import com.shamanland.fab.ShowHideOnScroll;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeaderViewRecyclerAdapter;
import org.fox.ttrss.util.HeadlinesRequest;
import org.fox.ttrss.glide.ProgressTarget;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import jp.wasabeef.glide.transformations.CropCircleTransformation;

public class HeadlinesFragment extends Fragment {

	public enum ArticlesSelection { ALL, NONE, UNREAD }

    public static final int FLAVOR_IMG_MIN_SIZE = 128;
	public static final int THUMB_IMG_MIN_SIZE = 32;

    public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 1000;

	private final String TAG = this.getClass().getSimpleName();

	private Feed m_feed;
	private Article m_activeArticle;
	private String m_searchQuery = "";
	private boolean m_refreshInProgress = false;
	private int m_firstId = 0;
	private boolean m_lazyLoadDisabled = false;
	private int m_amountScrolled;
	private int m_scrollToToggleBar;

	private SharedPreferences m_prefs;

	private HeaderViewRecyclerAdapter m_adapter;
	private ArticleList m_articles = new ArticleList();
	private ArticleList m_readArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
	private int m_maxImageSize = 0;
    private boolean m_compactLayoutMode = false;
    private RecyclerView m_list;
	private LinearLayoutManager m_layoutManager;

	private MediaPlayer m_mediaPlayer;
	private TextureView m_activeTexture;

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

	public boolean onArticleMenuItemSelected(MenuItem item, Article article, int position) {

		if (article == null) return false;

		switch (item.getItemId()) {
			case R.id.set_labels:
				m_activity.editArticleLabels(article);
				return true;
			case R.id.article_set_note:
				m_activity.editArticleNote(article);
				return true;
			case R.id.headlines_article_unread:
				article.unread = !article.unread;
				m_activity.saveArticleUnread(article);
				m_adapter.notifyItemChanged(position);
				return true;
			case R.id.headlines_article_link_copy:
				m_activity.copyToClipboard(article.link);
				return true;
			case R.id.headlines_article_link_open:
				m_activity.openUri(Uri.parse(article.link));

				if (article.unread) {
					article.unread = false;
					m_activity.saveArticleUnread(article);

					m_adapter.notifyItemChanged(position);
				}
				return true;
			case R.id.headlines_share_article:
				m_activity.shareArticle(article);
				return true;
			case R.id.catchup_above:
				if (true) {

					if (m_prefs.getBoolean("confirm_headlines_catchup", true)) {
						final Article fa = article;

						AlertDialog.Builder builder = new AlertDialog.Builder(
								m_activity)
								.setMessage(R.string.confirm_catchup_above)
								.setPositiveButton(R.string.dialog_ok,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
																int which) {

												catchupAbove(fa);

											}
										})
								.setNegativeButton(R.string.dialog_cancel,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
																int which) {

											}
										});

						AlertDialog dlg = builder.create();
						dlg.show();
					} else {
						catchupAbove(article);
					}

				}
				return true;
			default:
				Log.d(TAG, "onArticleMenuItemSelected, unhandled id=" + item.getItemId());
				return false;
		}
	}

	private void catchupAbove(Article article) {
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
		m_adapter.notifyDataSetChanged();
	}

	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		if (info != null) {

			Article article = getArticleAtPosition(info.position - m_adapter.getHeaderCount());

			if (!onArticleMenuItemSelected(item, article, info.position))
				return super.onContextItemSelected(item);
		}

		return super.onContextItemSelected(item);
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

		menu.findItem(R.id.set_labels).setEnabled(m_activity.getApiLevel() >= 1);
		menu.findItem(R.id.article_set_note).setEnabled(m_activity.getApiLevel() >= 1);

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
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
			m_lazyLoadDisabled = savedInstanceState.getBoolean("lazyLoadDisabled");
		}

		String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");

        if ("HL_COMPACT".equals(headlineMode) || "HL_COMPACT_NOIMAGES".equals(headlineMode))
            m_compactLayoutMode = true;

		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
		m_maxImageSize = (int) (128 * metrics.density + 0.5);

		m_scrollToToggleBar = m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material);

		Log.d(TAG, "maxImageSize=" + m_maxImageSize);

		View view = inflater.inflate(R.layout.fragment_headlines, container, false);

		m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.headlines_swipe_container);

	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh(false, true);
			}
		});

		m_list = (RecyclerView) view.findViewById(R.id.headlines_list);
		registerForContextMenu(m_list);

		m_layoutManager = new LinearLayoutManager(m_activity.getApplicationContext());
		m_list.setLayoutManager(m_layoutManager);
		m_list.setItemAnimator(new DefaultItemAnimator());
		m_list.addItemDecoration(new DividerItemDecoration(m_list.getContext(), m_layoutManager.getOrientation()));

		ArticleListAdapter adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, m_articles);

		m_adapter = new HeaderViewRecyclerAdapter(adapter);

		m_list.setAdapter(m_adapter);

		FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.headlines_fab);

		if (m_prefs.getBoolean("headlines_swipe_to_dismiss", true) && !m_prefs.getBoolean("headlines_mark_read_scroll", false) ) {

			ItemTouchHelper swipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

				@Override
				public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
					return false;
				}

				@Override
				public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {

					int position = viewHolder.getAdapterPosition() - m_adapter.getHeaderCount();

					Article article = getArticleAtPosition(position);

					if (article == null || article.id < 0)
						return 0;

					return super.getSwipeDirs(recyclerView, viewHolder);
				}

				@Override
				public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

					final int adapterPosition = viewHolder.getAdapterPosition();
					final int position = adapterPosition - m_adapter.getHeaderCount();

					try {
						final Article article = getArticleAtPosition(position);
						final boolean wasUnread;

						if (article != null && article.id > 0) {
							if (article.unread) {
								wasUnread = true;

								article.unread = false;
								m_activity.saveArticleUnread(article);
							} else {
								wasUnread = false;
							}

							m_articles.remove(position);
							m_adapter.notifyDataSetChanged();

							Snackbar.make(m_list, R.string.headline_undo_row_prompt, Snackbar.LENGTH_LONG)
									.setAction(getString(R.string.headline_undo_row_button), new OnClickListener() {
										@Override
										public void onClick(View v) {

											if (wasUnread) {
												article.unread = true;
												m_activity.saveArticleUnread(article);
											}

											m_articles.add(position, article);
											m_adapter.notifyItemInserted(adapterPosition);
											m_adapter.notifyItemRangeChanged(adapterPosition, 1);
										}
									}).show();

						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

			swipeHelper.attachToRecyclerView(m_list);

		};

		if (! (getActivity() instanceof DetailActivity)) {

			m_list.setOnTouchListener(new ShowHideOnScroll(fab));
			fab.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					refresh(false);
				}
			});

		} else {
			fab.setVisibility(View.GONE);
		}

        if (m_activity.isSmallScreen()) {
            View layout = inflater.inflate(R.layout.headlines_heading_spacer, m_list, false);
            m_adapter.addHeaderView(layout);

            m_swipeLayout.setProgressViewOffset(false, 0,
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material) +
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_padding_end_material) + 15);
        }

		m_list.setOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);

				/*if (newState != RecyclerView.SCROLL_STATE_IDLE) {

					try {
						if (m_mediaPlayer != null && m_mediaPlayer.isPlaying()) {
							m_mediaPlayer.pause();
						}
					} catch (IllegalStateException e) {
						// i guess it was already released, oh well
					}

				}*/

				if (newState == RecyclerView.SCROLL_STATE_IDLE && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
					if (!m_readArticles.isEmpty()) {
						m_activity.toggleArticlesUnread(m_readArticles);
						m_readArticles.clear();

						new Handler().postDelayed(new Runnable() {
							@Override
							public void run() {
								m_activity.refresh(false);
							}
						}, 100);
					}
				}
			}

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);

				int firstVisibleItem = m_layoutManager.findFirstVisibleItemPosition();
				int lastVisibleItem = m_layoutManager.findLastVisibleItemPosition();

				//Log.d(TAG, "onScrolled: FVI=" + firstVisibleItem + " LVI=" + lastVisibleItem);

				if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > m_adapter.getHeaderCount()) {

					if (firstVisibleItem <= m_articles.size() + m_adapter.getHeaderCount()) {

						Article a = getArticleAtPosition(firstVisibleItem - m_adapter.getHeaderCount() - 1);

						if (a != null && a.unread) {
							Log.d(TAG, "title=" + a.title);

							a.unread = false;
							m_readArticles.add(a);
							m_feed.unread--;
						}
					}
				}

				if (!m_activity.isTablet() && m_articles.size() > 0) {
					m_amountScrolled += dy;
					ActionBar bar = m_activity.getSupportActionBar();

					if (dy > 0 && m_amountScrolled >= m_scrollToToggleBar) {
						bar.hide();
						m_amountScrolled = 0;
					} else if (dy < 0 && m_amountScrolled <= -m_scrollToToggleBar) {
						bar.show();
						m_amountScrolled = 0;
					}

				}

				//Log.d(TAG, "onScrolled: " + m_refreshInProgress + " " + m_lazyLoadDisabled + " " + lastVisibleItem + " " + m_articles.size());

				if (!m_refreshInProgress && !m_lazyLoadDisabled && lastVisibleItem >= m_articles.size() - 5) {
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {
							refresh(true);
						}
					}, 100);

				}

			}
		});

        if (m_activity.isSmallScreen()) {
            m_activity.setTitle(m_feed.title);
        }

		Log.d(TAG, "onCreateView, feed=" + m_feed);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		//if (m_adapter != null) m_adapter.notifyDataSetChanged();

		if (m_activeArticle != null) {
			setActiveArticle(m_activeArticle);
		}

        if (m_articles.size() == 0) {
            refresh(false);
        }

		m_activity.invalidateOptionsMenu();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_activity = (OnlineActivity) activity;
		m_listener = (HeadlinesEventListener) activity;
	}

	public void refresh(boolean append) {
		refresh(append, false);
	}

	@SuppressWarnings({ "serial" })
	public void refresh(final boolean append, boolean userInitiated) {
		m_articles.stripFooters();
		m_adapter.notifyDataSetChanged();

		if (!append) m_lazyLoadDisabled = false;

		if (m_activity != null && isAdded() && m_feed != null) {
			m_refreshInProgress = true;

			if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);

			if (!append) {
				m_activity.getSupportActionBar().show();
				m_articles.clear();
				m_adapter.notifyDataSetChanged();
			} else {
				if (!(m_activity instanceof DetailActivity)) {
					m_articles.add(new Article(Article.TYPE_LOADMORE));
					m_adapter.notifyDataSetChanged();
				}
			}

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
					m_adapter.notifyDataSetChanged();

					if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

					//m_adapter.removeAllFooterViews();
					m_refreshInProgress = false;

					if (result != null) {

						if (m_activeArticle != null && !m_articles.containsId(m_activeArticle.id)) {
							m_activeArticle = null;
						}

						if (m_firstIdChanged) {
							m_lazyLoadDisabled = true;

							//Log.d(TAG, "first id changed, disabling lazy load");

							if (m_activity.isSmallScreen() || !m_activity.isPortrait()) {

								Snackbar.make(getView(), R.string.headlines_row_top_changed, Snackbar.LENGTH_LONG)
										.setAction(R.string.reload, new OnClickListener() {
											@Override
											public void onClick(View v) {
												refresh(false);
											}
										}).show();
							}
						}

						if (m_amountLoaded < HEADLINES_REQUEST_SIZE) {
							//Log.d(TAG, "amount loaded < request size, disabling lazy load");
							m_lazyLoadDisabled = true;
						}

						HeadlinesFragment.this.m_firstId = m_firstId;

						m_adapter.notifyDataSetChanged();
						m_listener.onHeadlinesLoaded(append);

					} else {
						m_lazyLoadDisabled = true;

						if (m_lastError == ApiCommon.ApiError.LOGIN_FAILED) {
							m_activity.login(true);
						} else {
							if (m_lastErrorMessage != null) {
								m_activity.toast(getString(getErrorMessage()) + "\n" + m_lastErrorMessage);
							} else {
								m_activity.toast(getErrorMessage());
							}
						}
					}

					if (!(m_activity instanceof DetailActivity)) {
						m_articles.add(new Article(Article.TYPE_AMR_FOOTER));
						m_adapter.notifyDataSetChanged();
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
		out.putBoolean("lazyLoadDisabled", m_lazyLoadDisabled);
	}

	static class ArticleViewHolder extends RecyclerView.ViewHolder {
		public View view;
		public Article article;

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
		public View flavorImageOverflow;
		public TextureView flavorVideoView;
		//public int position;
		public boolean flavorImageEmbedded;
		public ProgressTarget<String, GlideDrawable> flavorProgressTarget;

		public ArticleViewHolder(View v) {
			super(v);

			view = v;

			view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
				@Override
				public boolean onPreDraw() {
					View flavorImage = view.findViewById(R.id.flavor_image);

					if (flavorImage != null) {
						article.flavorViewHeight = flavorImage.getMeasuredHeight();
					}

					return true;
				}
			});

			titleView = (TextView)v.findViewById(R.id.title);

			feedTitleView = (TextView)v.findViewById(R.id.feed_title);
			markedView = (ImageView)v.findViewById(R.id.marked);
			publishedView = (ImageView)v.findViewById(R.id.published);
			excerptView = (TextView)v.findViewById(R.id.excerpt);
			flavorImageView = (ImageView) v.findViewById(R.id.flavor_image);
			flavorVideoKindView = (ImageView) v.findViewById(R.id.flavor_video_kind);
			authorView = (TextView)v.findViewById(R.id.author);
			dateView = (TextView) v.findViewById(R.id.date);
			selectionBoxView = (CheckBox) v.findViewById(R.id.selected);
			menuButtonView = (ImageView) v.findViewById(R.id.article_menu_button);
			flavorImageHolder = (ViewGroup) v.findViewById(R.id.flavorImageHolder);
			flavorImageLoadingBar = (ProgressBar) v.findViewById(R.id.flavorImageLoadingBar);
			headlineFooter = v.findViewById(R.id.headline_footer);
			textImage = (ImageView) v.findViewById(R.id.text_image);
			textChecked = (ImageView) v.findViewById(R.id.text_checked);
			headlineHeader = v.findViewById(R.id.headline_header);
			flavorImageOverflow = v.findViewById(R.id.gallery_overflow);
			flavorVideoView = (TextureView) v.findViewById(R.id.flavor_video);

			if (flavorImageView != null && flavorImageLoadingBar != null) {
				flavorProgressTarget = new FlavorProgressTarget<>(new GlideDrawableImageViewTarget(flavorImageView), flavorImageLoadingBar);
			}
		}

		public void clearAnimation() {
			view.clearAnimation();
		}
	}

	private static class FlavorProgressTarget<Z> extends ProgressTarget<String, Z> {
		private final ProgressBar progress;
		public FlavorProgressTarget(Target<Z> target, ProgressBar progress) {
			super(target);
			this.progress = progress;
		}

		@Override public float getGranualityPercentage() {
			return 0.1f; // this matches the format string for #text below
		}

		@Override protected void onConnecting() {
			progress.setIndeterminate(true);
			progress.setVisibility(View.VISIBLE);
		}
		@Override protected void onDownloading(long bytesRead, long expectedLength) {
			progress.setIndeterminate(false);
			progress.setProgress((int)(100 * bytesRead / expectedLength));
		}
		@Override protected void onDownloaded() {
			progress.setIndeterminate(true);
		}
		@Override protected void onDelivered() {
			progress.setVisibility(View.INVISIBLE);
		}
	}

	private class ArticleListAdapter extends RecyclerView.Adapter<ArticleViewHolder>  {
		private ArrayList<Article> items;

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		public static final int VIEW_AMR_FOOTER = 5;

		public static final int VIEW_COUNT = VIEW_AMR_FOOTER + 1;

		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;

        private ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();

		boolean showFlavorImage;
		private int m_minimumHeightToEmbed;
		boolean m_youtubeInstalled;
		private int m_screenHeight;
		private int m_lastAddedPosition;

		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super();
			this.items = items;

			Display display = m_activity.getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			m_minimumHeightToEmbed = size.y/3;
			m_screenHeight = size.y;

			String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
			showFlavorImage = "HL_DEFAULT".equals(headlineMode) || "HL_COMPACT".equals(headlineMode);

			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;

			List<ApplicationInfo> packages = m_activity.getPackageManager().getInstalledApplications(0);
			for (ApplicationInfo pi : packages) {
				if (pi.packageName.equals("com.google.android.youtube")) {
					m_youtubeInstalled = true;
					break;
				}
			}
		}

		@Override
		public ArticleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

			int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

			switch (viewType) {
				case VIEW_AMR_FOOTER:
					layoutId = R.layout.headlines_footer;
					break;
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

			View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);

			//registerForContextMenu(v);

			return new ArticleViewHolder(v);
		}

		@Override
		public void onBindViewHolder(final ArticleViewHolder holder, int position) {
			holder.article = items.get(position);

			int headlineFontSize = Integer.parseInt(m_prefs.getString("headlines_font_size_sp", "13"));
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));

			final Article article = holder.article;

			if (article.id == Article.TYPE_AMR_FOOTER && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
				WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
				Display display = wm.getDefaultDisplay();
				int screenHeight = display.getHeight();

				holder.view.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));
			}

			// nothing else of interest for those below anyway
			if (article.id < 0) return;

			holder.view.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					m_list.showContextMenuForChild(v);
					return true;
				}
			});

			holder.view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					m_listener.onArticleSelected(article);

					// only set active article when it makes sense (in DetailActivity)
					if (getActivity() instanceof DetailActivity) {
						m_activeArticle = article;
						m_adapter.notifyDataSetChanged();
					}
				}
			});

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
				updateTextCheckedState(holder, article, position);

				holder.textImage.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View view) {
						Log.d(TAG, "textImage : onclicked");

						article.selected = !article.selected;

						updateTextCheckedState(holder, article, m_list.getChildPosition(holder.view));

						m_listener.onArticleListSelectionChange(getSelectedArticles());

						Log.d(TAG, "num selected: " + getSelectedArticles().size());
					}
				});
				ViewCompat.setTransitionName(holder.textImage, "gallery:" + article.flavorImageUri);

				if (article.flavorImage != null) {

					holder.textImage.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {

							openGalleryForType(article, holder, holder.textImage);

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

						m_adapter.notifyItemChanged(m_list.getChildPosition(holder.view));

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
						//m_adapter.notifyDataSetChanged();
						m_adapter.notifyItemChanged(m_list.getChildPosition(holder.view));

						m_activity.saveArticlePublished(article);
					}
				});
			}

			if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
					String excerpt;

					try {
						if (article.excerpt != null) {
							excerpt = article.excerpt;
						} else {
							excerpt = article.articleDoc.text();

							if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH)
								excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";
						}
					} catch (Exception e) {
						e.printStackTrace();
						excerpt = "";
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
				holder.flavorImageLoadingBar.setIndeterminate(false);
				holder.flavorImageView.setVisibility(View.GONE);
				holder.flavorVideoKindView.setVisibility(View.GONE);
				holder.flavorImageOverflow.setVisibility(View.GONE);
				holder.flavorVideoView.setVisibility(View.GONE);
				holder.headlineHeader.setBackgroundDrawable(null);

				// this is needed if our flavor image goes behind base listview element
				holder.headlineHeader.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						m_listener.onArticleSelected(article);

						// only set active article when it makes sense (in DetailActivity)
						if (getActivity() instanceof DetailActivity) {
							m_activeArticle = article;
							m_adapter.notifyDataSetChanged();
						}
					}
				});

				holder.headlineHeader.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						m_list.showContextMenuForChild(holder.view);

						return true;
					}
				});

				if (showFlavorImage && article.flavorImageUri != null && holder.flavorImageView != null) {
					if (holder.flavorImageOverflow != null) {
						holder.flavorImageOverflow.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								PopupMenu popup = new PopupMenu(getActivity(), holder.flavorImageOverflow);
								MenuInflater inflater = popup.getMenuInflater();
								inflater.inflate(R.menu.content_gallery_entry, popup.getMenu());

								popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
									@Override
									public boolean onMenuItemClick(MenuItem item) {

										Uri mediaUri = Uri.parse(article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri);

										switch (item.getItemId()) {
											case R.id.article_img_open:
												m_activity.openUri(mediaUri);
												return true;
											case R.id.article_img_copy:
												m_activity.copyToClipboard(mediaUri.toString());
												return true;
											case R.id.article_img_share:
												m_activity.shareText(mediaUri.toString());
												return true;
											case R.id.article_img_view_caption:
												m_activity.displayImageCaption(article.flavorImageUri, article.content);
												return true;
											default:
												return false;
										}
									}
								});

								popup.show();
							}
						});

						holder.flavorImageView.setOnLongClickListener(new View.OnLongClickListener() {
							@Override
							public boolean onLongClick(View v) {
								m_list.showContextMenuForChild(holder.view);
								return true;
							}
						});
					}

					//Log.d(TAG, "IMG: " + article.flavorImageUri + " STREAM: " + article.flavorStreamUri + " H:" + article.flavorViewHeight);
					//Log.d(TAG, "TAG:" + holder.flavorImageOverflow.getTag());

					holder.flavorImageView.setVisibility(View.VISIBLE);

						holder.flavorImageView.setMaxHeight((int)(m_screenHeight * 0.8f));
						holder.flavorProgressTarget.setModel(article.flavorImageUri);

					if (article.flavorViewHeight > 0) {
						RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
						lp.height = article.flavorViewHeight;
						holder.flavorImageView.setLayoutParams(lp);
					}


					/*	TODO: maybe an option? force height for all images to reduce list jumping around

						RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
						lp.height = (int)(m_screenHeight * 0.5f);
						lp.addRule(RelativeLayout.BELOW, R.id.headline_header);
						holder.flavorImageView.setLayoutParams(lp);
					*/

					Glide.with(HeadlinesFragment.this)
							.load(article.flavorImageUri)
							.dontTransform()
							.diskCacheStrategy(DiskCacheStrategy.ALL)
							.skipMemoryCache(false)
							.listener(new RequestListener<String, GlideDrawable>() {
								@Override
								public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {

									holder.flavorImageLoadingBar.setVisibility(View.GONE);
									holder.flavorImageView.setVisibility(View.GONE);

									return false;
								}

								@Override
								public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {

									holder.flavorImageLoadingBar.setVisibility(View.GONE);

									if (resource.getIntrinsicWidth() > FLAVOR_IMG_MIN_SIZE && resource.getIntrinsicHeight() > FLAVOR_IMG_MIN_SIZE) {

										//holder.flavorImageView.setVisibility(View.VISIBLE);
										holder.flavorImageOverflow.setVisibility(View.VISIBLE);

										boolean forceDown = article.flavorImage != null && "video".equals(article.flavorImage.tagName().toLowerCase());

										RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
										lp.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
										holder.flavorImageView.setLayoutParams(lp);

										maybeRepositionFlavorImage(holder.flavorImageView, resource, holder, forceDown);
										adjustVideoKindView(holder, article);

										return false;
									} else {

										holder.flavorImageOverflow.setVisibility(View.GONE);
										holder.flavorImageView.setVisibility(View.GONE);

										return true;
									}
								}
							})
							.into(holder.flavorProgressTarget);
				}

				if (m_prefs.getBoolean("inline_video_player", false) && article.flavorImage != null &&
						"video".equals(article.flavorImage.tagName().toLowerCase()) && article.flavorStreamUri != null) {

					holder.flavorImageView.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
							releaseSurface();
							openGalleryForType(article, holder, holder.flavorImageView);
							return true;
						}
					});

					holder.flavorVideoView.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
							releaseSurface();
							openGalleryForType(article, holder, holder.flavorImageView);
							return true;
						}
					});

					holder.flavorImageView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							releaseSurface();
							m_mediaPlayer = new MediaPlayer();

							holder.flavorVideoView.setVisibility(View.VISIBLE);
							final ProgressBar bar = holder.flavorImageLoadingBar;

							bar.setIndeterminate(true);
							bar.setVisibility(View.VISIBLE);

							holder.flavorVideoView.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									try {
										if (m_mediaPlayer.isPlaying())
											m_mediaPlayer.pause();
										else
											m_mediaPlayer.start();
									} catch (IllegalStateException e) {
										releaseSurface();
									}
								}
							});

							m_activeTexture = holder.flavorVideoView;

							android.view.ViewGroup.LayoutParams lp = m_activeTexture.getLayoutParams();

							Drawable drawable = holder.flavorImageView.getDrawable();

							if (drawable != null) {

								float aspect = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();

								lp.height = holder.flavorImageView.getMeasuredHeight();
								lp.width = (int) (lp.height * aspect);

								m_activeTexture.setLayoutParams(lp);
							}

							holder.flavorVideoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
									 @Override
									 public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
										 try {
											 m_mediaPlayer.setSurface(new Surface(surface));

											 m_mediaPlayer.setDataSource(article.flavorStreamUri);

											 m_mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
													 @Override
													 public void onPrepared(MediaPlayer mp) {

														 try {
															 bar.setVisibility(View.GONE);
															 mp.setLooping(true);
															 mp.start();
														 } catch (IllegalStateException e) {
															 e.printStackTrace();
														 }
													 }
												 });

											 m_mediaPlayer.prepareAsync();
										 } catch (Exception e) {
											 e.printStackTrace();
										 }

									 }

									 @Override
									 public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

									 }

									 @Override
									 public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
										 try {
											 m_mediaPlayer.release();
										 } catch (Exception e) {
											 e.printStackTrace();
										 }
										 return false;
									 }

									 @Override
									 public void onSurfaceTextureUpdated(SurfaceTexture surface) {

									 }
								 }
							);

						}
					});

				} else {
					holder.flavorImageView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							openGalleryForType(article, holder, holder.flavorImageView);
						}
					});
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

						article.selected = cb.isChecked();

						m_listener.onArticleListSelectionChange(getSelectedArticles());

						Log.d(TAG, "num selected: " + getSelectedArticles().size());
					}
				});
			}

			if (holder.menuButtonView != null) {

				holder.menuButtonView.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {

						PopupMenu popup = new PopupMenu(getActivity(), v);
						MenuInflater inflater = popup.getMenuInflater();
						inflater.inflate(R.menu.context_headlines, popup.getMenu());

						popup.getMenu().findItem(R.id.set_labels).setEnabled(m_activity.getApiLevel() >= 1);
						popup.getMenu().findItem(R.id.article_set_note).setEnabled(m_activity.getApiLevel() >= 1);

						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								return onArticleMenuItemSelected(item, article, m_list.getChildPosition(holder.view));
							}
						});

						popup.show();
					}
				});
			}
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);

			if (a.id == Article.TYPE_AMR_FOOTER) {
				return VIEW_AMR_FOOTER;
			} else if (a.id == Article.TYPE_LOADMORE) {
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
		public int getItemCount() {
			return items.size();
		}

		private void updateTextCheckedState(final ArticleViewHolder holder, final Article article, final int position) {
            String tmp = article.title.length() > 0 ? article.title.substring(0, 1).toUpperCase() : "?";

            if (article.selected) {
				holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));
                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
				final Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(article.title));

				holder.textImage.setImageDrawable(textDrawable);

				if (!showFlavorImage || article.flavorImage == null) {
					holder.textImage.setImageDrawable(textDrawable);

				} else {
					Glide.with(HeadlinesFragment.this)
							.load(article.flavorImageUri)
							.placeholder(textDrawable)
							.bitmapTransform(new CropCircleTransformation(getActivity()))
							.diskCacheStrategy(DiskCacheStrategy.ALL)
							.skipMemoryCache(false)
							.listener(new RequestListener<String, GlideDrawable>() {
								@Override
								public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
									return false;
								}

								@Override
								public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {

									if (resource.getIntrinsicWidth() < THUMB_IMG_MIN_SIZE || resource.getIntrinsicHeight() < THUMB_IMG_MIN_SIZE) {
										return true;
									} else {
										return false;
									}
								}
							})
							.into(holder.textImage);
				}

                holder.textChecked.setVisibility(View.GONE);
            }
        }

		private void openGalleryForType(Article article, ArticleViewHolder holder, View transitionView) {
			//Log.d(TAG, "openGalleryForType: " + article + " " + holder + " " + transitionView);

			if ("iframe".equals(article.flavorImage.tagName().toLowerCase())) {

				if (m_youtubeInstalled) {
					Intent intent = new Intent(m_activity, YoutubePlayerActivity.class);
					intent.putExtra("streamUri", article.flavorStreamUri);
					intent.putExtra("vid", article.youtubeVid);
					intent.putExtra("title", article.title);

					startActivity(intent);
					m_activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

				} else {
					m_activity.openUri(Uri.parse(article.flavorStreamUri));
				}

			} else {

				Intent intent = new Intent(m_activity, GalleryActivity.class);

				intent.putExtra("firstSrc", article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri);
				intent.putExtra("title", article.title);
				intent.putExtra("content", article.content);

				ActivityOptionsCompat options =
						ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
								transitionView != null ? transitionView : holder.flavorImageView,
								"gallery:" + (article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri));

				ActivityCompat.startActivity(m_activity, intent, options.toBundle());
			}

		}

		private void adjustVideoKindView(ArticleViewHolder holder, Article article) {
			if (article.flavorImage != null) {
				if ("iframe".equals(article.flavorImage.tagName().toLowerCase())) {
					holder.flavorVideoKindView.setImageResource(R.drawable.ic_youtube_play);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else if ("video".equals(article.flavorImage.tagName().toLowerCase())) {
					holder.flavorVideoKindView.setImageResource(R.drawable.ic_play_circle);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else if (article.mediaList.size() > 1) {
					holder.flavorVideoKindView.setImageResource(R.drawable.ic_image_album);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else {
					holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
				}
			} else {
				holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
			}
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

		private void maybeRepositionFlavorImage(View view, GlideDrawable resource, ArticleViewHolder holder, boolean forceDown) {
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();

			int w = resource.getIntrinsicWidth();
			int h = resource.getIntrinsicHeight();
			float r = h != 0 ? (float)w/h : 0;

			//Log.d(TAG, "XYR: " + pxToDp(w) + " " + pxToDp(h) + " " + r);

			if (forceDown || h < m_minimumHeightToEmbed || r >= 1.2) {

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

	private void releaseSurface() {
		try {
			if (m_mediaPlayer != null) {
				m_mediaPlayer.release();
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}

		try {
			if (m_activeTexture != null) {
				m_activeTexture.setVisibility(View.GONE);
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}

	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	// returns cloned array without footers
	public ArticleList getAllArticles() {
		ArticleList tmp = (ArticleList) m_articles.clone();
		tmp.stripFooters();

		return tmp;
	}

    // if setting active doesn't make sense, scroll to whatever is passed to us
	public void setActiveArticle(Article article) {
		if (article != m_activeArticle && article != null) {

            // only set active article when it makes sense (in DetailActivity)
            if (getActivity() instanceof DetailActivity) {
                m_activeArticle = article;
				m_adapter.notifyDataSetChanged();
            }

			if (m_list != null) {
				int position = getArticlePositionById(article.id);
				m_list.scrollToPosition(position);
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
			return m_articles.get(position);
		} catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
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

	public Article getActiveArticle() {
		return m_activeArticle;
	}

    public int getArticlePositionById(int id) {
        for (int i = 0; i < m_articles.size(); i++) {
            if (m_articles.get(i).id == id) {
                return i + m_adapter.getHeaderCount();
            }
        }

        return -1;
    }

	public String getSearchQuery() {
		return m_searchQuery;
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;

			// could be called before fragment view has been initialized
			if (m_list != null) {
				refresh(false);
			}
		}
	}

	public Feed getFeed() {
		return m_feed;
	}

    public void setArticles(ArticleList articles) {
        m_articles.clear();
        m_articles.addAll(articles);

		m_articles.add(new Article(Article.TYPE_AMR_FOOTER));

        m_adapter.notifyDataSetChanged();
    }

    @Override
	public void onPause() {
		super.onPause();

		releaseSurface();
	}

}
