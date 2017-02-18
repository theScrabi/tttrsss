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
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.google.gson.JsonElement;
import com.nhaarman.listviewanimations.appearance.AnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.SwingBottomInAnimationAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.DynamicListView;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.DismissableManager;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.OnDismissCallback;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.undo.SimpleSwipeUndoAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.undo.TimedUndoAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.undo.UndoAdapter;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

public class HeadlinesFragment extends Fragment implements OnItemClickListener, OnScrollListener {
	public enum ArticlesSelection { ALL, NONE, UNREAD }

    public static final int FLAVOR_IMG_MIN_SIZE = 128;
	public static final int THUMB_IMG_MIN_SIZE = 32;

    public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;

	//public static final int ARTICLE_SPECIAL_LOADMORE = -1;
	//public static final int ARTICLE_SPECIAL_TOP_CHANGED = -3;

	private final String TAG = this.getClass().getSimpleName();
	
	private Feed m_feed;
	private Article m_activeArticle;
	private String m_searchQuery = "";
	private boolean m_refreshInProgress = false;
	private boolean m_autoCatchupDisabled = false;
	private int m_firstId = 0;
	private boolean m_lazyLoadDisabled = false;

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
    private DynamicListView m_list;
	private ImageLoader m_imageLoader = ImageLoader.getInstance();
	private View m_listLoadingView;
	private View m_topChangedView;
	private View m_amrFooterView;

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

	public boolean onArticleMenuItemSelected(MenuItem item, Article article) {

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
				m_adapter.notifyDataSetChanged();
				return true;
			case R.id.headlines_article_link_copy:
				m_activity.copyToClipboard(article.link);
				return true;
			case R.id.headlines_article_link_open:
				m_activity.openUri(Uri.parse(article.link));

				if (article.unread) {
					article.unread = false;
					m_activity.saveArticleUnread(article);

					m_adapter.notifyDataSetChanged();
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

		Article article = getArticleAtPosition(info.position);

		if (!onArticleMenuItemSelected(item, article))
			return super.onContextItemSelected(item);
		else
			return true;
	}

	/*@Override
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
					m_activity.openUri(Uri.parse(article.link));

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
	} */

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

		Log.d(TAG, "maxImageSize=" + m_maxImageSize);
		
		View view = inflater.inflate(R.layout.fragment_headlines, container, false);

		m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.headlines_swipe_container);

	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh(false, true);
			}
		});

		m_list = (DynamicListView) view.findViewById(R.id.headlines_list);

		FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.headlines_fab);

		boolean enableSwipeToDismiss = m_prefs.getBoolean("headlines_swipe_to_dismiss", true);

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

		m_listLoadingView = inflater.inflate(R.layout.headlines_row_loadmore, m_list, false);
		//m_list.addFooterView(m_listLoadingView, null, false);
		//m_listLoadingView.setVisibility(View.GONE);

		m_topChangedView = inflater.inflate(R.layout.headlines_row_top_changed, m_list, false);
		//m_list.addFooterView(m_topChangedView, null, false);
		//m_topChangedView.setVisibility(View.GONE);*/

		if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int screenHeight = display.getHeight();

            m_amrFooterView = inflater.inflate(R.layout.headlines_footer, container, false);
			m_amrFooterView.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));

            m_list.addFooterView(m_amrFooterView, null, false);
        }

        if (m_activity.isSmallScreen()) {
            View layout = inflater.inflate(R.layout.headlines_heading_spacer, m_list, false);
            m_list.addHeaderView(layout);

            m_swipeLayout.setProgressViewOffset(false, 0,
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material) +
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_padding_end_material));
        }

		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, m_articles);
		m_animationAdapter = new SwingBottomInAnimationAdapter(m_adapter);

		m_animationAdapter.setAbsListView(m_list);
		m_list.setAdapter(m_animationAdapter);

		if (enableSwipeToDismiss) {

			TimedUndoAdapter swipeUndoAdapter = new TimedUndoAdapter(m_adapter, m_activity,
					new OnDismissCallback() {
						@Override
						public void onDismiss(final ViewGroup listView, final int[] reverseSortedPositions) {
							for (int position : reverseSortedPositions) {
								Article article = m_adapter.getItem(position);

								Log.d(TAG, "onSwipeDismiss: " + article);

								if (article != null) {
									if (article.unread) {
										article.unread = false;
										m_activity.saveArticleUnread(article);
									}

									m_adapter.remove(article);
									m_adapter.notifyDataSetChanged();
								}
							}
						}
					}
			);

			swipeUndoAdapter.setTimeoutMs(2000);
			swipeUndoAdapter.setAbsListView(m_list);
			m_list.setAdapter(swipeUndoAdapter);
			m_list.enableSimpleSwipeUndo();
			m_list.setDismissableManager(new DismissableManager() {
				@Override
				public boolean isDismissable(long id, int position) {
					try {
						Article article = m_adapter.getItem(position);

						return article != null;
					} catch (Exception e) {
						// index out of bounds == footer or w/e
						return false;
					}
				}
			});
		}


		m_list.setOnItemClickListener(this);
		m_list.setOnScrollListener(this);

		if (!enableSwipeToDismiss) registerForContextMenu(m_list);

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
		m_list.removeFooterView(m_listLoadingView);
		m_list.removeFooterView(m_topChangedView);
		m_list.removeFooterView(m_amrFooterView);

		if (!append) m_lazyLoadDisabled = false;

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

					if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

					//m_listLoadingView.setVisibility(View.GONE);
					m_list.removeFooterView(m_listLoadingView);
					m_list.removeFooterView(m_topChangedView);
					m_list.removeFooterView(m_amrFooterView);

					if (result != null) {
						m_refreshInProgress = false;

						if (m_activeArticle != null && !m_articles.containsId(m_activeArticle.id)) {
							m_activeArticle = null;
						}

						if (m_firstIdChanged) {
							m_lazyLoadDisabled = true;

							//m_activity.toast(R.string.headlines_row_top_changed);

							//m_topChangedView.setVisibility(View.VISIBLE);
							//m_articles.add(new Article(ARTICLE_SPECIAL_TOP_CHANGED));

							m_list.addFooterView(m_topChangedView, null, false);
						}

						if (m_amountLoaded < HEADLINES_REQUEST_SIZE) {
							m_lazyLoadDisabled = true;
						}

						HeadlinesFragment.this.m_firstId = m_firstId;

						m_adapter.notifyDataSetChanged();
						m_listener.onHeadlinesLoaded(fappend);

						// not sure why but listview sometimes gets positioned while ignoring the header so
						// top headline content becomes partially obscured by the toolbar on phones
						// (not reproducible on avd)
						if (!fappend) {
							m_list.smoothScrollToPosition(0);
						}

						//m_listLoadingView.setVisibility(m_amountLoaded == HEADLINES_REQUEST_SIZE ? View.VISIBLE : View.GONE);

					} else {
						if (m_lastError == ApiError.LOGIN_FAILED) {
							m_activity.login(true);
						} else {

							if (m_lastErrorMessage != null) {
								m_activity.toast(getString(getErrorMessage()) + "\n" + m_lastErrorMessage);
							} else {
								m_activity.toast(getErrorMessage());
							}

							//m_activity.setLoadingStatus(getErrorMessage(), false);
						}
					}

					if (m_amrFooterView != null) m_list.addFooterView(m_amrFooterView, null, false);
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

				if (skip > 0) {
					m_list.addFooterView(m_listLoadingView, null, false);
					//m_listLoadingView.setVisibility(View.VISIBLE);
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
		out.putBoolean("lazyLoadDisabled", m_lazyLoadDisabled);
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
		public View flavorImageOverflow;
		public int position;
		public boolean flavorImageEmbedded;
	}
	
	private class ArticleListAdapter extends ArrayAdapter<Article> implements UndoAdapter {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		//public static final int VIEW_LOADMORE = 4;
		//public static final int VIEW_TOP_CHANGED = 4;
		
		public static final int VIEW_COUNT = VIEW_SELECTED_UNREAD + 1;
		
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

			/*if (a.id == ARTICLE_SPECIAL_LOADMORE) {
				return VIEW_LOADMORE; */
			/*if (a.id == ARTICLE_SPECIAL_TOP_CHANGED) {
				return VIEW_TOP_CHANGED;
			} else */ if (m_activeArticle != null && a.id == m_activeArticle.id && a.unread) {
				return VIEW_SELECTED_UNREAD;
			} else if (m_activeArticle != null && a.id == m_activeArticle.id) {
				return VIEW_SELECTED;
			} else if (a.unread) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}

        private void updateTextCheckedState(final HeadlineViewHolder holder, final Article article, final int position) {
            String tmp = article.title.length() > 0 ? article.title.substring(0, 1).toUpperCase() : "?";

            if (article.selected) {
				holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));
				holder.textImage.setTag(null);

                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
				final Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(article.title));

				holder.textImage.setImageDrawable(textDrawable);
				holder.textImage.setTag(null);

				//holder.textChecked.setVisibility(View.GONE);

				if (!showFlavorImage || article.flavorImage == null) {
					holder.textImage.setImageDrawable(textDrawable);
					holder.textImage.setTag(null);
				} else {
					if (!article.flavorImageUri.equals(holder.textImage.getTag())) {

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

						m_imageLoader.displayImage(article.flavorImageUri, imageAware, options, new ImageLoadingListener() {
									@Override
									public void onLoadingStarted(String s, View view) {

									}

									@Override
									public void onLoadingFailed(String s, View view, FailReason failReason) {

									}

									@Override
									public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
										if (position == holder.position && bitmap != null) {
											holder.textImage.setTag(article.flavorImageUri);

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
		public View getView(final int position, final View convertView, ViewGroup parent) {

			View v = convertView;

			final Article article = items.get(position);
			final HeadlineViewHolder holder;

			int headlineFontSize = Integer.parseInt(m_prefs.getString("headlines_font_size_sp", "13"));
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));

			if (v == null) {
                int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

                switch (getItemViewType(position)) {
				/*case VIEW_LOADMORE:
					layoutId = R.layout.headlines_row_loadmore;
					break;
				case VIEW_TOP_CHANGED:
					layoutId = R.layout.headlines_row_top_changed;
					break;*/
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
				holder.flavorImageOverflow = v.findViewById(R.id.flavor_image_overflow);

				v.setTag(holder);

				// http://code.google.com/p/android/issues/detail?id=3414
				((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			} else {
				holder = (HeadlineViewHolder) v.getTag();
			}

			//Log.d(TAG, "getView: " + position + ":" + article.title);

			holder.position = position;

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

            if (holder.textImage != null) {
                updateTextCheckedState(holder, article, position);

				holder.textImage.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View view) {
						Log.d(TAG, "textImage : onclicked");

						article.selected = !article.selected;

						updateTextCheckedState(holder, article, position);

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
				holder.flavorImageView.setVisibility(View.GONE);
				holder.flavorVideoKindView.setVisibility(View.GONE);
				holder.flavorImageOverflow.setVisibility(View.GONE);
				holder.headlineHeader.setBackgroundDrawable(null);

				// this is needed if our flavor image goes behind base listview element
				holder.headlineHeader.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						m_listener.onArticleSelected(article);
					}
				});

				holder.headlineHeader.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						m_activity.openContextMenu(v);

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
								inflater.inflate(R.menu.context_article_content_img, popup.getMenu());

								popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
									@Override
									public boolean onMenuItemClick(MenuItem item) {
										switch (item.getItemId()) {
											case R.id.article_img_open:
												m_activity.openUri(Uri.parse(article.flavorImageUri));
												return true;
											case R.id.article_img_copy:
												m_activity.copyToClipboard(article.flavorImageUri);
												return true;
											case R.id.article_img_share:
												m_activity.shareText(article.flavorImageUri);
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

								m_activity.openContextMenu(v);

								return true;
							}
						});
					}

					if (!article.flavorImageUri.equals(holder.flavorImageView.getTag())) {

						//Log.d(TAG, "IMG: " + article.flavorImageUri + " STREAM: " + article.flavorStreamUri);

						ImageAware imageAware = new ImageViewAware(holder.flavorImageView, false);

						m_imageLoader.displayImage(article.flavorImageUri, imageAware, displayImageOptions, new ImageLoadingListener() {
							@Override
							public void onLoadingStarted(String s, View view) {
								holder.flavorImageLoadingBar.setVisibility(View.VISIBLE);
								holder.flavorImageLoadingBar.setIndeterminate(false);
								holder.flavorImageLoadingBar.setProgress(0);
							}

							@Override
							public void onLoadingFailed(String s, View view, FailReason failReason) {
								holder.flavorImageLoadingBar.setVisibility(View.GONE);
							}

							@Override
							public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
								if (position == holder.position && bitmap != null) {

									holder.flavorImageLoadingBar.setVisibility(View.GONE);

									if (bitmap.getWidth() > FLAVOR_IMG_MIN_SIZE && bitmap.getHeight() > FLAVOR_IMG_MIN_SIZE) {
										holder.flavorImageView.setTag(article.flavorImageUri);

										holder.flavorImageView.setVisibility(View.VISIBLE);
										holder.flavorImageOverflow.setVisibility(View.VISIBLE);

										maybeRepositionFlavorImage(view, bitmap, holder);
										adjustVideoKindView(holder, article);

									} else {
										holder.flavorImageView.setImageDrawable(null);
									}
								}
							}

							@Override
							public void onLoadingCancelled(String s, View view) {
								holder.flavorImageLoadingBar.setVisibility(View.GONE);
							}
						}, new ImageLoadingProgressListener() {
							@Override
							public void onProgressUpdate(String s, View view, int current, int total) {
								if (total != 0) {
									int p = (int) ((float) current / total * 100);

									holder.flavorImageLoadingBar.setIndeterminate(false);
									holder.flavorImageLoadingBar.setProgress(p);
								} else {
									holder.flavorImageLoadingBar.setIndeterminate(true);
								}
							}
						});

					} else { // already tagged
						holder.flavorImageView.setVisibility(View.VISIBLE);
						holder.flavorImageOverflow.setVisibility(View.VISIBLE);

						adjustVideoKindView(holder, article);

						if (holder.flavorImageEmbedded) {
							TypedValue tv = new TypedValue();
							if (m_activity.getTheme().resolveAttribute(R.attr.headlineHeaderBackground, tv, true)) {
								holder.headlineHeader.setBackgroundColor(tv.data);
							}
						} else {
							holder.headlineHeader.setBackgroundDrawable(null);
						}
					}
				}

				holder.flavorImageView.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View view) {

						openGalleryForType(article, holder, null);

					}
				});
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
				//if (m_activity.isDarkTheme())
				//	ib.setImageResource(R.drawable.ic_mailbox_collapsed_holo_dark);

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
								return onArticleMenuItemSelected(item, article);
							}
						});

						popup.show();
					}
				});
			}

			return v;
		}

		private void openGalleryForType(Article article, HeadlineViewHolder holder, View transitionView) {
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

			} else if ("video".equals(article.flavorImage.tagName().toLowerCase())) {

				Intent intent = new Intent(m_activity, VideoPlayerActivity.class);
				intent.putExtra("streamUri", article.flavorStreamUri);
				intent.putExtra("title", article.title);
				intent.putExtra("coverSrc", article.flavorImageUri);

				//startActivity(intent);
				//m_activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

				ActivityOptionsCompat options =
						ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
								transitionView != null ? transitionView : holder.flavorImageView,
								"gallery:" + article.flavorImageUri);

				ActivityCompat.startActivity(m_activity, intent, options.toBundle());

			} else {

				Intent intent = new Intent(m_activity, ArticleImagesPagerActivity.class);

				intent.putExtra("firstSrc", article.flavorImageUri);
				intent.putExtra("title", article.title);
				intent.putExtra("content", article.content);

				ActivityOptionsCompat options =
						ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
								transitionView != null ? transitionView : holder.flavorImageView,
								"gallery:" + article.flavorImageUri);

				ActivityCompat.startActivity(m_activity, intent, options.toBundle());
			}

		}

		private void adjustVideoKindView(HeadlineViewHolder holder, Article article) {
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

		@NonNull
		@Override
		public View getUndoView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			View view = convertView;

			if (view == null) {
				view = LayoutInflater.from(m_activity).inflate(R.layout.headlines_row_undo, parent, false);
			}
			return view;
		}

		@NonNull
		@Override
		public View getUndoClickView(@NonNull View view) {
			return view.findViewById(R.id.headlines_row_undo_button);
		}
	}


	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public ArticleList getAllArticles() {
		return (ArticleList) m_articles.clone();
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

		if (!m_refreshInProgress && !m_lazyLoadDisabled && /*m_articles.findById(ARTICLE_SPECIAL_LOADMORE) != null &&*/ firstVisibleItem + visibleItemCount == m_articles.size()) {
			refresh(true);
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

			// could be called before fragment view has been initialized
			if (m_list != null) {
				refresh(false);
			}
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
        m_articles.addAll(articles);
        m_adapter.notifyDataSetChanged();
    }
}
