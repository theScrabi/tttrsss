package org.fox.ttrss.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
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
import com.bumptech.glide.request.target.Target;
import com.shamanland.fab.FloatingActionButton;

import org.fox.ttrss.Application;
import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.GalleryActivity;
import org.fox.ttrss.HeadlinesFragment;
import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import jp.wasabeef.glide.transformations.CropCircleTransformation;

public class OfflineHeadlinesFragment extends Fragment implements OnItemClickListener, AbsListView.OnScrollListener {
	public enum ArticlesSelection { ALL, NONE, UNREAD }

    private final String TAG = this.getClass().getSimpleName();
	
	private int m_feedId;
	private boolean m_feedIsCat = false;
	private String m_feedTitle;

	private int m_activeArticleId;
	private String m_searchQuery = "";
	
	private SharedPreferences m_prefs;
    private ArrayList<Integer> m_readArticleIds = new ArrayList<Integer>();
	private ArrayList<Integer> m_autoMarkedArticleIds = new ArrayList<Integer>();

	private HashMap<Integer, Integer> m_flavorHeightStorage = new HashMap<>();
	
	private Cursor m_cursor;
	private ArticleListAdapter m_adapter;

	private OfflineHeadlinesEventListener m_listener;
	private OfflineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;

    private boolean m_compactLayoutMode = false;
    private ListView m_list;
    private int m_listPreviousVisibleItem;

	public void initialize(int feedId, boolean isCat, boolean compactMode) {
		m_feedId = feedId;
		m_feedIsCat = isCat;
        m_compactLayoutMode = compactMode;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}
	
	public int getSelectedArticleCount() {
		Cursor c = m_activity.getDatabase().query("articles", 
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null, null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();
		
		return selected;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
									ContextMenuInfo menuInfo) {

		getActivity().getMenuInflater().inflate(R.menu.context_headlines, menu);

		menu.findItem(R.id.set_labels).setVisible(false);
		menu.findItem(R.id.article_set_note).setVisible(false);

		if (m_prefs.getBoolean("offline_sort_by_feed", false)) {
			menu.findItem(R.id.catchup_above).setVisible(false);
		}

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		int articleId = getArticleIdAtPosition(info.position);

		if (!onArticleMenuItemSelected(item, articleId))
			return super.onContextItemSelected(item);
		else
			return true;
	}

	private boolean onArticleMenuItemSelected(MenuItem item, final int articleId) {
		switch (item.getItemId()) {
			case R.id.headlines_article_unread:
				if (true) {

					SQLiteStatement stmt = m_activity.getDatabase().compileStatement(
							"UPDATE articles SET modified = 1, unread = not unread " + "WHERE " + BaseColumns._ID
									+ " = ?");

					stmt.bindLong(1, articleId);
					stmt.execute();
					stmt.close();

					refresh();
				}
				return true;
			case R.id.headlines_article_link_copy:
				if (true) {
					Cursor article = m_activity.getArticleById(articleId);

					if (article != null) {
						m_activity.copyToClipboard(article.getString(article.getColumnIndex("link")));
						article.close();
					}
				}
				return true;
			case R.id.headlines_article_link_open:
				if (true) {
					Cursor article = m_activity.getArticleById(articleId);

					if (article != null) {
						m_activity.openUri(Uri.parse(article.getString(article.getColumnIndex("link"))));

						// TODO: mark article as read, set modified = 1, refresh

						article.close();
					}
				}
				return true;
			case R.id.headlines_share_article:
				m_activity.shareArticle(articleId);
				return true;
			case R.id.catchup_above:
				if (true) {
					if (m_prefs.getBoolean("confirm_headlines_catchup", true)) {

						AlertDialog.Builder builder = new AlertDialog.Builder(
								m_activity)
								.setMessage(R.string.confirm_catchup_above)
								.setPositiveButton(R.string.dialog_ok,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
																int which) {

												catchupAbove(articleId);

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
						catchupAbove(articleId);
					}

				}
				return true;
			default:
				Log.d(TAG, "onArticleMenuItemSelected, unhandled id=" + item.getItemId());
				return false;
		}

	}

	private void catchupAbove(int articleId) {
		SQLiteStatement stmt = null;

		String updatedOperator = (m_prefs.getBoolean("offline_oldest_first", false)) ? "<" : ">";

		if (m_feedIsCat) {
            stmt = m_activity.getDatabase().compileStatement(
                    "UPDATE articles SET modified = 1, unread = 0 WHERE " +
                            "updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
                            "AND unread = 1 AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
        } else {
            stmt = m_activity.getDatabase().compileStatement(
                    "UPDATE articles SET modified = 1, unread = 0 WHERE " +
                            "updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
                            "AND unread = 1 AND feed_id = ?");
        }

		stmt.bindLong(1, articleId);
		stmt.bindLong(2, m_feedId);
		stmt.execute();
		stmt.close();

		refresh();
	}

	@Override
	public void onResume() {
		super.onResume();
		
		if (Application.getInstance().m_selectedArticleId != 0) {
			m_activeArticleId = Application.getInstance().m_selectedArticleId;
			Application.getInstance().m_selectedArticleId = 0;
		}

		if (m_activeArticleId != 0) {
			setActiveArticleId(m_activeArticleId);
		}

		//refresh();

		m_activity.invalidateOptionsMenu();
	}

	public void refresh() {
		refresh(true);
	}

	public void refresh(boolean keepRemnantIds) {
		try {
			if (!isAdded()) return;

            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);
			
			if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();

			if (!keepRemnantIds) m_autoMarkedArticleIds.clear();

			m_cursor = createCursor();
			
			if (m_cursor != null && m_adapter != null) {
				m_adapter.changeCursor(m_cursor);
				m_adapter.notifyDataSetChanged();
			}

            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
			
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);

		Glide.get(getContext()).clearMemory();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feedId = savedInstanceState.getInt("feedId");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			//m_selectedArticles = savedInstanceState.getParcelableArrayList("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
			m_feedIsCat = savedInstanceState.getBoolean("feedIsCat");
            m_compactLayoutMode = savedInstanceState.getBoolean("compactLayoutMode");
			m_readArticleIds = savedInstanceState.getIntegerArrayList("autoMarkedIds");

		} else {
			m_activity.getDatabase().execSQL("UPDATE articles SET selected = 0 ");
		}

		String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");

		if ("HL_COMPACT".equals(headlineMode) || "HL_COMPACT_NOIMAGES".equals(headlineMode))
			m_compactLayoutMode = true;

		View view = inflater.inflate(R.layout.fragment_headlines_offline, container, false);

		m_swipeLayout = view.findViewById(R.id.headlines_swipe_container);
		
	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh(false);
			}
		});

		m_cursor = createCursor();
		
		m_list = view.findViewById(R.id.headlines_list);

		FloatingActionButton fab = view.findViewById(R.id.headlines_fab);
		fab.setVisibility(View.GONE);

        if (m_activity.isSmallScreen()) {
            View layout = inflater.inflate(R.layout.headlines_heading_spacer, m_list, false);
            m_list.addHeaderView(layout);

            m_swipeLayout.setProgressViewOffset(false, 0,
                    m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material) +
                            m_activity.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_padding_end_material));
        }

        if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int screenHeight = display.getHeight();

            View layout = inflater.inflate(R.layout.headlines_footer, container, false);

            layout.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));

            m_list.addFooterView(layout, null, false);
        }


        m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, m_cursor,
				new String[] { "title" }, new int[] { R.id.title }, 0);
		m_list.setAdapter(m_adapter);

		m_list.setOnItemClickListener(this);
        m_list.setOnScrollListener(this);
		registerForContextMenu(m_list);

		m_feedTitle = m_activity.getFeedTitle(m_feedId, m_feedIsCat);

		if (m_feedTitle != null && m_activity.isSmallScreen()) {
			m_activity.setTitle(m_feedTitle);
		}

		return view;    	
	}

	public Cursor createCursor() {
		String feedClause = null;
		
		if (m_feedIsCat) {
			feedClause = "feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)";
		} else {
			feedClause = "feed_id = ?";
		}
		
		String viewMode = m_activity.getViewMode();
		
		if ("adaptive".equals(viewMode)) {
			// TODO: implement adaptive			
		} else if ("marked".equals(viewMode)) {
			feedClause += "AND (marked = 1)";
		} else if ("published".equals(viewMode)) {
			feedClause += "AND (published = 1)";
		} else if ("unread".equals(viewMode)) {
			String idsMarkedRead = "articles." + BaseColumns._ID + " in (" + android.text.TextUtils.join(",", m_autoMarkedArticleIds) + ")";

			feedClause += "AND (unread = 1 OR "+idsMarkedRead+")";
		} else { // all_articles
			//
		}
		
		String orderBy = (m_prefs.getBoolean("offline_oldest_first", false)) ? "updated" : "updated DESC";

		if (m_prefs.getBoolean("offline_sort_by_feed", false)) {
			orderBy = "feed_title, " + orderBy;
		}

		if (m_searchQuery == null || m_searchQuery.equals("")) {
			return m_activity.getDatabase().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles.*", "feeds.title AS feed_title" }, feedClause, 
					new String[] { String.valueOf(m_feedId) }, null, null, orderBy);
		} else {
			return m_activity.getDatabase().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles.*", "feeds.title AS feed_title" },
					feedClause + " AND (articles.title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%')", 
					new String[] { String.valueOf(m_feedId), m_searchQuery, m_searchQuery }, null, null, orderBy);
		}
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_listener = (OfflineHeadlinesEventListener) activity;
		m_activity = (OfflineActivity) activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {

			int articleId = getArticleIdAtPosition(position);

            if (articleId != 0) {

                if (getActivity().findViewById(R.id.article_fragment) != null) {
                    m_activeArticleId = articleId;
                }

                m_listener.onArticleSelected(articleId);

                refresh();
            }
		}
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("feedId", m_feedId);
		out.putInt("activeArticleId", m_activeArticleId);
		//out.putParcelableArrayList("selectedArticles", m_selectedArticles);
		out.putCharSequence("searchQuery", m_searchQuery);
		out.putBoolean("feedIsCat", m_feedIsCat);
		out.putIntegerArrayList("autoMarkedIds", m_autoMarkedArticleIds);

        out.putBoolean("compactLayoutMode", m_compactLayoutMode);
	}

	/* public void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}	
		}
		
		getActivity().setProgressBarIndeterminateVisibility(showProgress);
	} */

    static class ArticleViewHolder {

		public int articleId;
		HashMap<Integer, Integer> flavorHeightStorage;

		public View view;

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
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
		public ImageView flavorVideoKindView;
		public View flavorImageOverflow;
		public View headlineHeader;
		public ImageView attachmentsView;

		public ArticleViewHolder(View v) {

			view = v;

			view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
				@Override
				public boolean onPreDraw() {
					View flavorImage = view.findViewById(R.id.flavor_image);

					if (flavorImage != null) {
						int height = flavorImage.getMeasuredHeight();

						if (height > 0) {
							//Log.d("ArticleViewHolder", "view measured height: " + flavorImage.getMeasuredHeight() + " for " + articleId);

							flavorHeightStorage.put(articleId, flavorImage.getMeasuredHeight());
						}
					}

					return true;
				}
			});

			titleView = v.findViewById(R.id.title);

			feedTitleView = v.findViewById(R.id.feed_title);
			markedView = v.findViewById(R.id.marked);
			publishedView = v.findViewById(R.id.published);
			excerptView = v.findViewById(R.id.excerpt);
			flavorImageView = v.findViewById(R.id.flavor_image);
			authorView = v.findViewById(R.id.author);
			dateView = v.findViewById(R.id.date);
			selectionBoxView = v.findViewById(R.id.selected);
			menuButtonView = v.findViewById(R.id.article_menu_button);
			flavorImageHolder = v.findViewById(R.id.flavorImageHolder);
			flavorImageLoadingBar = v.findViewById(R.id.flavorImageLoadingBar);
			headlineFooter = v.findViewById(R.id.headline_footer);
			textImage = v.findViewById(R.id.text_image);
			textChecked = v.findViewById(R.id.text_checked);
			flavorVideoKindView = v.findViewById(R.id.flavor_video_kind);
			headlineHeader = v.findViewById(R.id.headline_header);
			flavorImageOverflow = v.findViewById(R.id.gallery_overflow);
			attachmentsView = v.findViewById(R.id.attachments);
		}
	}

    private class ArticleListAdapter extends SimpleCursorAdapter {
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

		private boolean showFlavorImage;
		private int m_screenHeight;
		
		public ArticleListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			
			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;

			String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
			showFlavorImage = "HL_DEFAULT".equals(headlineMode) || "HL_COMPACT".equals(headlineMode);

			Display display = m_activity.getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			//m_minimumHeightToEmbed = size.y/3;
			m_screenHeight = size.y;
		}

		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Cursor c = (Cursor) getItem(position);
			
			//Log.d(TAG, "@gIVT " + position + " " + c.getInt(0) + " vs " + m_activeArticleId);
			
			if (c.getInt(0) == m_activeArticleId && c.getInt(c.getColumnIndex("unread")) == 1) {				
				return VIEW_SELECTED_UNREAD;
			} else if (c.getInt(0) == m_activeArticleId) {
				return VIEW_SELECTED;
			} else if (c.getInt(c.getColumnIndex("unread")) == 1) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}

        private void updateTextCheckedState(ArticleViewHolder holder, Cursor item, ArticleFlavorInfo afi) {
            String title = item.getString(item.getColumnIndex("title"));

            String tmp = title.length() > 0 ? title.substring(0, 1) : "?";

            boolean isChecked = item.getInt(item.getColumnIndex("selected")) == 1;

            if (isChecked) {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));

                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
				Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(title));

				if (showFlavorImage && afi.flavorImageUri != null) {

					holder.textImage.setImageDrawable(textDrawable);

					Glide.with(OfflineHeadlinesFragment.this)
							.load(afi.flavorImageUri)
							.placeholder(textDrawable)
							.bitmapTransform(new CropCircleTransformation(getActivity()))
							.diskCacheStrategy(DiskCacheStrategy.NONE)
							.skipMemoryCache(false)
							.listener(new RequestListener<String, GlideDrawable>() {
								@Override
								public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
									return false;
								}

								@Override
								public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {

									return resource.getIntrinsicWidth() < HeadlinesFragment.THUMB_IMG_MIN_SIZE ||
											resource.getIntrinsicHeight() < HeadlinesFragment.THUMB_IMG_MIN_SIZE;
								}
							})
							.into(holder.textImage);

				} else {
					holder.textImage.setImageDrawable(textDrawable);
				}

                holder.textChecked.setVisibility(View.GONE);
            }
        }

		@Override
		public View getView(int position, final View convertView, ViewGroup parent) {

			View v = convertView;

			final Cursor article = (Cursor)getItem(position);

            final ArticleViewHolder holder;

			final int articleId = article.getInt(0);
			
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

				holder = new ArticleViewHolder(v);
                v.setTag(holder);

                // http://code.google.com/p/android/issues/detail?id=3414
                ((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            } else {
                holder = (ArticleViewHolder) v.getTag();
            }

            holder.articleId = articleId;
			holder.flavorHeightStorage = m_flavorHeightStorage;

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
				final ArticleFlavorInfo afi = findFlavorImage(article);

				updateTextCheckedState(holder, article, afi);

                holder.textImage.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "textImage : onclicked");

                        SQLiteStatement stmtUpdate = m_activity.getDatabase().compileStatement("UPDATE articles SET selected = NOT selected " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        updateTextCheckedState(holder, article, afi);

                        refresh();

                        m_activity.invalidateOptionsMenu();
                    }
                });

				ViewCompat.setTransitionName(holder.textImage, "gallery:" + afi.flavorImageUri);

				if (afi.flavorImageUri != null) {

					final String articleContent = article.getString(article.getColumnIndex("content"));
					final String articleTitle = article.getString(article.getColumnIndex("title"));

					holder.textImage.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
							openGalleryForType(afi, articleTitle, articleContent, holder, holder.textImage);
							return true;
						}
					});

				}


            }

			if (holder.titleView != null) {
				
				holder.titleView.setText(Html.fromHtml(article.getString(article.getColumnIndex("title"))));
                holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));

				int scoreIndex = article.getColumnIndex("score");
				if (scoreIndex >= 0)
					adjustTitleTextView(article.getInt(scoreIndex), holder.titleView, position);
			}
			
			int feedTitleIndex = article.getColumnIndex("feed_title");
			
			if (holder.feedTitleView != null && feedTitleIndex != -1 && m_feedIsCat) {				
				String feedTitle = article.getString(feedTitleIndex);
				
				if (feedTitle.length() > 20)
					feedTitle = feedTitle.substring(0, 20) + "...";
				
				if (feedTitle.length() > 0) {
					holder.feedTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
					holder.feedTitleView.setText(feedTitle);					
				} else {
					holder.feedTitleView.setVisibility(View.GONE);
				}				
			} else if (holder.feedTitleView != null) {
				holder.feedTitleView.setVisibility(View.GONE);
			}

			TypedValue tvAccent = new TypedValue();
			m_activity.getTheme().resolveAttribute(R.attr.colorAccent, tvAccent, true);

			if (holder.attachmentsView != null) {
				holder.attachmentsView.setVisibility(View.GONE);
			}

			if (holder.markedView != null) {
				TypedValue tv = new TypedValue();

				boolean marked = article.getInt(article.getColumnIndex("marked")) == 1;

				m_activity.getTheme().resolveAttribute(marked ? R.attr.ic_star : R.attr.ic_star_outline, tv, true);

				holder.markedView.setImageResource(tv.resourceId);

				if (marked)
					holder.markedView.setColorFilter(tvAccent.data);
				else
					holder.markedView.setColorFilter(null);
				
				holder.markedView.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						SQLiteStatement stmtUpdate = m_activity.getDatabase()
								.compileStatement("UPDATE articles SET modified = 1, modified_marked = 1, marked = NOT marked " +
									"WHERE " + BaseColumns._ID + " = ?");

						stmtUpdate.bindLong(1, articleId);
						stmtUpdate.execute();
						stmtUpdate.close();

						refresh();
					}
				});
			}
			
			if (holder.publishedView != null) {
				TypedValue tv = new TypedValue();

				boolean published = article.getInt(article.getColumnIndex("published")) == 1;

				m_activity.getTheme().resolveAttribute(published ? R.attr.ic_checkbox_marked : R.attr.ic_rss_box, tv, true);

				holder.publishedView.setImageResource(tv.resourceId);

				if (published)
					holder.publishedView.setColorFilter(tvAccent.data);
				else
					holder.publishedView.setColorFilter(null);

				holder.publishedView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        SQLiteStatement stmtUpdate = m_activity.getDatabase()
								.compileStatement("UPDATE articles SET modified = 1, modified_published = 1, published = NOT published " +
                                	"WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        refresh();
                    }
                });
			}

			if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
                    String articleContent = article.getString(article.getColumnIndex("content"));

                    String tmp = articleContent.length() > CommonActivity.EXCERPT_MAX_QUERY_LENGTH ?
                            articleContent.substring(0, CommonActivity.EXCERPT_MAX_QUERY_LENGTH) : articleContent;

                    String excerpt = Jsoup.parse(tmp).text();

                    if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH) excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";

					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);
				}
			}       	

			if (holder.authorView != null) {
				int authorIndex = article.getColumnIndex("author");
				if (authorIndex >= 0) {
					String author = article.getString(authorIndex);
					
					holder.authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);					
					
					if (author != null && author.length() > 0) 
						holder.authorView.setText(getString(R.string.author_formatted, author));
					else
						holder.authorView.setText("");
				}
			}
			
			if (holder.dateView != null) {
				holder.dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
				
				Date d = new Date((long)article.getInt(article.getColumnIndex("updated")) * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				holder.dateView.setText(df.format(d));
			}

			if (holder.selectionBoxView != null) {
				holder.selectionBoxView.setChecked(article.getInt(article.getColumnIndex("selected")) == 1);
				holder.selectionBoxView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        CheckBox cb = (CheckBox) view;

                        SQLiteStatement stmtUpdate = m_activity.getDatabase().compileStatement("UPDATE articles SET selected = ? " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, cb.isChecked() ? 1 : 0);
                        stmtUpdate.bindLong(2, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        refresh();

                        m_activity.invalidateOptionsMenu();

                    }
                });
			}

            if (holder.flavorImageHolder != null) {

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
						m_listener.onArticleSelected(articleId);
					}
				});

				holder.headlineHeader.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						m_activity.openContextMenu(v);

						return true;
					}
				});

				if (showFlavorImage) {
					final ArticleFlavorInfo afi = findFlavorImage(article);

					if (afi.flavorImageUri != null) {
						holder.flavorImageView.setVisibility(View.VISIBLE);

						holder.flavorImageView.setMaxHeight((int)(m_screenHeight * 0.8f));

						int flavorViewHeight = m_flavorHeightStorage.containsKey(articleId) ? m_flavorHeightStorage.get(articleId) : 0;

						//Log.d(TAG, articleId + " IMG: " + afi.flavorImageUri + " STREAM: " + afi.flavorStreamUri + " H:" + flavorViewHeight);

						if (flavorViewHeight > 0) {
							RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
							lp.height = flavorViewHeight;
							holder.flavorImageView.setLayoutParams(lp);
						}

						final String articleContent = article.getString(article.getColumnIndex("content"));
						final String articleTitle = article.getString(article.getColumnIndex("title"));


                        holder.flavorImageView.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View view) {

								openGalleryForType(afi, articleTitle, articleContent, holder, holder.flavorImageView);
                            }
                        });

						try {

							Glide.with(OfflineHeadlinesFragment.this)
									.load(afi.flavorImageUri)
									.dontTransform()
									.diskCacheStrategy(DiskCacheStrategy.NONE)
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

											if (resource.getIntrinsicWidth() > HeadlinesFragment.FLAVOR_IMG_MIN_SIZE &&
													resource.getIntrinsicHeight() > HeadlinesFragment.FLAVOR_IMG_MIN_SIZE) {

												holder.flavorImageView.setVisibility(View.VISIBLE);


												//TODO: not implemented
												//holder.flavorImageOverflow.setVisibility(View.VISIBLE);

												/*boolean forceDown = article.flavorImage != null && "video".equals(article.flavorImage.tagName().toLowerCase());

												maybeRepositionFlavorImage(holder.flavorImageView, resource, holder, forceDown);*/
												adjustVideoKindView(holder, afi);

												/* we don't support image embedding in offline */

												RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
												lp.addRule(RelativeLayout.BELOW, R.id.headline_header);
												lp.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
												holder.flavorImageView.setLayoutParams(lp);

												holder.headlineHeader.setBackgroundDrawable(null);

												return false;
											} else {

												holder.flavorImageOverflow.setVisibility(View.GONE);
												holder.flavorImageView.setVisibility(View.GONE);

												return true;
											}
										}
									})
									.into(holder.flavorImageView);
						} catch (OutOfMemoryError e) {
							e.printStackTrace();
						}

                    }
				}
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

						popup.getMenu().findItem(R.id.set_labels).setVisible(false);
						popup.getMenu().findItem(R.id.article_set_note).setVisible(false);

						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								return onArticleMenuItemSelected(item, articleId);
							}
						});

						popup.show();
                    }
                });								
			}

            return v;
		}

		private void adjustVideoKindView(ArticleViewHolder holder, ArticleFlavorInfo afi) {
			if (afi.flavorImageUri != null) {
				if (afi.flavorStreamUri != null) {
					holder.flavorVideoKindView.setImageResource(R.drawable.ic_play_circle);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else if (afi.mediaList.size() > 1) {
					holder.flavorVideoKindView.setImageResource(R.drawable.ic_image_album);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else {
					holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
				}
			} else {
				holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
			}
		}

		private void openGalleryForType(ArticleFlavorInfo afi, String title, String content, ArticleViewHolder viewHolder, View transitionView) {

			Intent intent = new Intent(m_activity, GalleryActivity.class);

			intent.putExtra("firstSrc", afi.flavorStreamUri != null ? afi.flavorStreamUri : afi.flavorImageUri);
			intent.putExtra("title", title);
			intent.putExtra("content", rewriteUrlsToLocal(content));

			ActivityOptionsCompat options =
					ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
							transitionView != null ? transitionView : viewHolder.flavorImageView,
							"gallery:" + (afi.flavorStreamUri != null ? afi.flavorStreamUri : afi.flavorImageUri));

			ActivityCompat.startActivity(m_activity, intent, options.toBundle());

		}

		private String rewriteUrlsToLocal(String content) {
			Document doc = Jsoup.parse(content);

			if (doc != null) {
				List<Element> mediaList = doc.select("img,source");

				for (Element e : mediaList) {
					String url = e.attr("src");

					if (url != null && ImageCacheService.isUrlCached(m_activity, url)) {
						e.attr("src", "file://" + ImageCacheService.getCacheFileName(m_activity, url));
					}
				}

				content = doc.html();
			}

			return content;
		}

		private class ArticleFlavorInfo {
            String flavorImageUri;
            String flavorStreamUri;
			public List<Element> mediaList = new ArrayList<>();
		}

		private ArticleFlavorInfo findFlavorImage(Cursor article) {
            ArticleFlavorInfo afi = new ArticleFlavorInfo();

			String content = article.getString(article.getColumnIndex("content"));

			if (content != null) {
				Document articleDoc = Jsoup.parse(content);

				if (articleDoc != null) {

					Element flavorImage = null;
					afi.mediaList = articleDoc.select("img,video");

					for (Element e : afi.mediaList) {
						flavorImage = e;
						break;
					}

					if (flavorImage != null) {

						try {

							if ("video".equals(flavorImage.tagName().toLowerCase())) {
								Element source = flavorImage.select("source").first();
                                afi.flavorStreamUri = source.attr("src");

                                afi.flavorImageUri = flavorImage.attr("poster");
							} else {
                                afi.flavorImageUri = flavorImage.attr("src");

								if (afi.flavorImageUri != null && afi.flavorImageUri.startsWith("//")) {
                                    afi.flavorImageUri = "https:" + afi.flavorImageUri;
								}

                                afi.flavorStreamUri = null;
							}
						} catch (Exception e) {
							e.printStackTrace();

							afi.flavorImageUri = null;
                            afi.flavorStreamUri = null;
						}
					}
				}
			}

            if (afi.flavorImageUri != null && ImageCacheService.isUrlCached(m_activity, afi.flavorImageUri)) {
                afi.flavorImageUri = "file://" + ImageCacheService.getCacheFileName(m_activity, afi.flavorImageUri);
            } else {
                afi.flavorImageUri = null;
            }

            if (afi.flavorStreamUri != null && ImageCacheService.isUrlCached(m_activity, afi.flavorStreamUri)) {
                afi.flavorStreamUri = "file://" + ImageCacheService.getCacheFileName(m_activity, afi.flavorStreamUri);
            } else {
                afi.flavorStreamUri = null;
            }

            return afi;
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

	public void setActiveArticleId(int articleId) {
		m_activeArticleId = articleId;
		try {
			m_adapter.notifyDataSetChanged();

			ListView list = getView().findViewById(R.id.headlines_list);
		
			Log.d(TAG, articleId + " position " + getArticleIdPosition(articleId));
			
			if (list != null) {
				list.setSelection(getArticleIdPosition(articleId));
			}
		} catch (NullPointerException e) {
			// invoked before view is created, nvm
		}
	}

	public Cursor getArticleAtPosition(int position) {
		return (Cursor) m_list.getItemAtPosition(position);
	}

	public int getArticleIdAtPosition(int position) {
		Cursor c = getArticleAtPosition(position);
		
		if (c != null) {
			int id = c.getInt(0);
			return id;
		}

        return 0;

		//return (int) m_adapter.getItemId(position + m_list.getHeaderViewsCount());
	}

	public int getActiveArticleId() {
		return m_activeArticleId;
	}

	public int getArticleIdPosition(int articleId) {
		for (int i = 0; i < m_adapter.getCount(); i++) {
			if (articleId == m_adapter.getItemId(i))
				return i + m_list.getHeaderViewsCount();
		}
		
		return -1;
	}
	
	public int getArticleCount() {
		return m_adapter.getCount();
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;
		}
	}

	public int getFeedId() {
		return m_feedId;
	}
	
	public boolean getFeedIsCat() {
		return m_feedIsCat;
	}

	public String getSearchQuery() {
		return m_searchQuery;
	}

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > (m_activity.isSmallScreen() ? 1 : 0)) {
            //Article a = m_articles.get(firstVisibleItem - 1);

            Cursor article = getArticleAtPosition(firstVisibleItem - 1);

            if (article != null) {
                Integer id = article.getInt(article.getColumnIndex(BaseColumns._ID));
                boolean unread = article.getInt(article.getColumnIndex("unread")) != 0;

                if (unread && !m_readArticleIds.contains(id)) {
                    m_readArticleIds.add(id);
                }
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
        if (scrollState == SCROLL_STATE_IDLE && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            if (!m_readArticleIds.isEmpty()) {

                SQLiteStatement stmt = m_activity.getDatabase().compileStatement(
                        "UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
                                + " = ?");

                for (int articleId : m_readArticleIds) {
                    stmt.bindLong(1, articleId);
                    stmt.execute();
                }

                stmt.close();

				m_autoMarkedArticleIds.addAll(m_readArticleIds);
				m_readArticleIds.clear();

                refresh();

            }
        }
    }

}
