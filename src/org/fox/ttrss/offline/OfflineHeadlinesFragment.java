package org.fox.ttrss.offline;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.fox.ttrss.GlobalState;
import org.fox.ttrss.R;
import org.jsoup.Jsoup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class OfflineHeadlinesFragment extends Fragment implements OnItemClickListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	private final String TAG = this.getClass().getSimpleName();
	
	private int m_feedId;
	private boolean m_feedIsCat = false;
	private int m_activeArticleId;
	private String m_searchQuery = "";
	
	private SharedPreferences m_prefs;
	
	private Cursor m_cursor;
	private ArticleListAdapter m_adapter;
	
	private OfflineHeadlinesEventListener m_listener;
	private OfflineActivity m_activity;
	
	private ImageGetter m_dummyGetter = new ImageGetter() {

		@SuppressWarnings("deprecation")
		@Override
		public Drawable getDrawable(String source) {
			return new BitmapDrawable();
		}
		
	};
	
	public void initialize(int feedId, boolean isCat) {
		m_feedId = feedId;
		m_feedIsCat = isCat;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}
	
	public int getSelectedArticleCount() {
		Cursor c = m_activity.getReadableDb().query("articles", 
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null, null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();
		
		return selected;
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		
		switch (item.getItemId()) {
		case R.id.article_link_copy:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				
				Cursor article = m_activity.getArticleById(articleId);
				
				if (article != null) {				
					m_activity.copyToClipboard(article.getString(article.getColumnIndex("link")));
					article.close();
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, marked = NOT marked WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, marked = NOT marked WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, published = NOT published WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, published = NOT published WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();
			return true;
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, unread = NOT unread WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = NOT unread WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();			
			return true;
		case R.id.share_article:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				m_activity.shareArticle(articleId);
			}
			return true;
		case R.id.catchup_above:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = null;
				
				String updatedOperator = (m_prefs.getBoolean("offline_oldest_first", false)) ? "<" : ">";
				
				if (m_feedIsCat) {
					stmt = m_activity.getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND unread = 1 AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");						
				} else {
					stmt = m_activity.getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND unread = 1 AND feed_id = ?");						
				}
				
				stmt.bindLong(1, articleId);
				stmt.bindLong(2, m_feedId);
				stmt.execute();
				stmt.close();
			}			
			refresh();
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
		
		if (getSelectedArticleCount() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			Cursor c = getArticleAtPosition(info.position);
			menu.setHeaderTitle(c.getString(c.getColumnIndex("title")));
			//c.close();
			menu.setGroupVisible(R.id.menu_group_single_article, true);
			
			menu.findItem(R.id.set_labels).setVisible(false);
			menu.findItem(R.id.article_set_note).setVisible(false);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (GlobalState.getInstance().m_selectedArticleId != 0) {			
			m_activeArticleId = GlobalState.getInstance().m_selectedArticleId;
			GlobalState.getInstance().m_selectedArticleId = 0;
		}

		if (m_activeArticleId != 0) {
			setActiveArticleId(m_activeArticleId);
		}

		refresh();
	}
	
	public void refresh() {
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
		
		m_cursor = createCursor();
		
		if (m_cursor != null) {
			m_adapter.changeCursor(m_cursor);
			m_adapter.notifyDataSetChanged();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feedId = savedInstanceState.getInt("feedId");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			//m_selectedArticles = savedInstanceState.getParcelableArrayList("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
			m_feedIsCat = savedInstanceState.getBoolean("feedIsCat");
		} else {
			m_activity.getWritableDb().execSQL("UPDATE articles SET selected = 0 ");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		m_cursor = createCursor();
		
		ListView list = (ListView)view.findViewById(R.id.headlines);		
		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, m_cursor,
				new String[] { "title" }, new int[] { R.id.title }, 0);
		
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		list.setEmptyView(view.findViewById(R.id.no_headlines));
		registerForContextMenu(list);

		if (m_activity.isSmallScreen() || m_activity.isPortrait())
			view.findViewById(R.id.headlines_fragment).setPadding(0, 0, 0, 0);
		
		getActivity().setProgressBarIndeterminateVisibility(false);

		return view;    	
	}

	public Cursor createCursor() {
		String feedClause = null;
		
		if (m_feedIsCat) {
			feedClause = "feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)";
		} else {
			feedClause = "feed_id = ?";
		}
		
		String orderBy = (m_prefs.getBoolean("offline_oldest_first", false)) ? "updated" : "updated DESC";
		
		if (m_searchQuery == null || m_searchQuery.equals("")) {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles.*", "feeds.title AS feed_title" }, feedClause, 
					new String[] { String.valueOf(m_feedId) }, null, null, orderBy);
		} else {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
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
			/* Cursor cursor = (Cursor)list.getItemAtPosition(position);
			
			int articleId = cursor.getInt(0); */
			
			int articleId = getArticleIdAtPosition(position);
			
			if (getActivity().findViewById(R.id.article_fragment) != null) {
				m_activeArticleId = articleId;
			}

			m_listener.onArticleSelected(articleId);
			
			refresh();
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
	
	private class ArticleListAdapter extends SimpleCursorAdapter {
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;
		
		public ArticleListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			
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

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			Cursor article = (Cursor)getItem(position);
			final int articleId = article.getInt(0);
			
			if (v == null) {
				int layoutId = R.layout.headlines_row;
				
				switch (getItemViewType(position)) {
				case VIEW_LOADMORE:
					layoutId = R.layout.headlines_row_loadmore;
					break;
				case VIEW_UNREAD:
					layoutId = R.layout.headlines_row_unread;
					break;
				case VIEW_SELECTED_UNREAD:
					layoutId = R.layout.headlines_row_selected_unread;
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
				tt.setText(Html.fromHtml(article.getString(article.getColumnIndex("title"))));

				int scoreIndex = article.getColumnIndex("score");
				if (scoreIndex >= 0)
					adjustTitleTextView(article.getInt(scoreIndex), tt, position);
			}
			
			TextView ft = (TextView)v.findViewById(R.id.feed_title);
			
			int feedTitleIndex = article.getColumnIndex("feed_title");
			
			if (ft != null && feedTitleIndex != -1 && m_feedIsCat) {				
				String feedTitle = article.getString(feedTitleIndex);
				
				if (feedTitle.length() > 20)
					feedTitle = feedTitle.substring(0, 20) + "...";
				
				if (feedTitle.length() > 0) {
					ft.setText(feedTitle);					
				} else {
					ft.setVisibility(View.GONE);
				}				
			} else if (ft != null) {
				ft.setVisibility(View.GONE);
			}
			
			ImageView marked = (ImageView)v.findViewById(R.id.marked);
			
			if (marked != null) {
				marked.setImageResource(article.getInt(article.getColumnIndex("marked")) == 1 ? android.R.drawable.star_on : android.R.drawable.star_off);
				
				marked.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET modified = 1, marked = NOT marked " +
								"WHERE " + BaseColumns._ID + " = ?");
						
						stmtUpdate.bindLong(1, articleId);
						stmtUpdate.execute();
						stmtUpdate.close();
						
						refresh();
					}
				});
			}
			
			ImageView published = (ImageView)v.findViewById(R.id.published);
			
			if (published != null) {
				published.setImageResource(article.getInt(article.getColumnIndex("published")) == 1 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
				
				published.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET modified = 1, published = NOT published " +
								"WHERE " + BaseColumns._ID + " = ?");
						
						stmtUpdate.bindLong(1, articleId);
						stmtUpdate.execute();
						stmtUpdate.close();

						refresh();
					}
				});
			}
			
			TextView te = (TextView)v.findViewById(R.id.excerpt);

			if (te != null) {
				String excerpt = Jsoup.parse(article.getString(article.getColumnIndex("content"))).text(); 
				
				if (excerpt.length() > 100)
					excerpt = excerpt.substring(0, 100) + "...";
				
				te.setText(excerpt);
			}       	

			TextView ta = (TextView)v.findViewById(R.id.author);

			if (ta != null) {
				int authorIndex = article.getColumnIndex("author");
				if (authorIndex >= 0)
					ta.setText(article.getString(authorIndex));
			}

			/* ImageView separator = (ImageView)v.findViewById(R.id.headlines_separator);
			
			if (separator != null && m_offlineServices.isSmallScreen()) {
				separator.setVisibility(View.GONE);
			} */
			
			TextView dv = (TextView) v.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date((long)article.getInt(article.getColumnIndex("updated")) * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				dv.setText(df.format(d));
			}
			
			CheckBox cb = (CheckBox) v.findViewById(R.id.selected);

			if (cb != null) {
				cb.setChecked(article.getInt(article.getColumnIndex("selected")) == 1);
				cb.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View view) {
						CheckBox cb = (CheckBox)view;

						SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET selected = ? " +
								"WHERE " + BaseColumns._ID + " = ?");
						
						stmtUpdate.bindLong(1, cb.isChecked() ? 1 : 0);
						stmtUpdate.bindLong(2, articleId);
						stmtUpdate.execute();
						stmtUpdate.close();
						
						refresh();
						
						m_activity.initMenu();
						
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

			ListView list = (ListView)getView().findViewById(R.id.headlines);
		
			Log.d(TAG, articleId + " position " + getArticleIdPosition(articleId));
			
			if (list != null) {
				list.setSelection(getArticleIdPosition(articleId));
			}
		} catch (NullPointerException e) {
			// invoked before view is created, nvm
		}
	}

	public Cursor getArticleAtPosition(int position) {
		return (Cursor) m_adapter.getItem(position);
	}

	public int getArticleIdAtPosition(int position) {
		/*Cursor c = getArticleAtPosition(position);
		
		if (c != null) {
			int id = c.getInt(0);
			return id;
		}		*/
		
		return (int) m_adapter.getItemId(position);
	}

	public int getActiveArticleId() {
		return m_activeArticleId;
	}

	public int getArticleIdPosition(int articleId) {
		for (int i = 0; i < m_adapter.getCount(); i++) {
			if (articleId == m_adapter.getItemId(i))
				return i;
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
	
}
