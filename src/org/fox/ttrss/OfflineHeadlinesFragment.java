package org.fox.ttrss;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.jsoup.Jsoup;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
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
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class OfflineHeadlinesFragment extends Fragment implements OnItemClickListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD };

	private final String TAG = this.getClass().getSimpleName();
	
	private int m_feedId;
	private int m_activeArticleId;
	private boolean m_combinedMode = true;
	private String m_searchQuery = "";
	
	private SharedPreferences m_prefs;
	
	private Cursor m_cursor;
	private ArticleListAdapter m_adapter;
	
	private OfflineServices m_offlineServices;
	
	private ImageGetter m_dummyGetter = new ImageGetter() {

		@Override
		public Drawable getDrawable(String source) {
			return new BitmapDrawable();
		}
		
	};
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}
	
	public int getSelectedArticleCount() {
		Cursor c = m_offlineServices.getReadableDb().query("articles", 
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null, null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();
		
		return selected;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_menu, menu);
		
		if (getSelectedArticleCount() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			Cursor c = getArticleAtPosition(info.position);
			menu.setHeaderTitle(c.getString(c.getColumnIndex("title")));
			//c.close();
			menu.setGroupVisible(R.id.menu_group_single_article, true);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	public void refresh() {
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
		
		m_cursor = createCursor();
		
		if (m_cursor != null) {
			m_adapter.changeCursor(m_cursor);
			setActiveArticleId(m_offlineServices.getSelectedArticleId());
			m_adapter.notifyDataSetChanged();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feedId = savedInstanceState.getInt("feedId");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			//m_selectedArticles = savedInstanceState.getParcelableArrayList("selectedArticles");
			m_combinedMode = savedInstanceState.getBoolean("combinedMode");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
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

		view.findViewById(R.id.loading_progress).setVisibility(View.GONE);

		return view;    	
	}

	public Cursor createCursor() {
		if (m_searchQuery.equals("")) {
			return m_offlineServices.getReadableDb().query("articles", 
					null, "feed_id = ?", new String[] { String.valueOf(m_feedId) }, null, null, "updated DESC");
		} else {
			return m_offlineServices.getReadableDb().query("articles", 
					null, "feed_id = ? AND (title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%')", 
					new String[] { String.valueOf(m_feedId), m_searchQuery, m_searchQuery }, null, null, "updated DESC");
		}
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_offlineServices = (OfflineServices)activity;
		
		m_feedId = m_offlineServices.getActiveFeedId();
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_combinedMode = m_prefs.getBoolean("combined_mode", false);
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {
			Cursor cursor = (Cursor)list.getItemAtPosition(position);
			
			m_activeArticleId = cursor.getInt(0);

			SQLiteStatement stmtUpdate = m_offlineServices.getWritableDb().compileStatement("UPDATE articles SET unread = 0 " +
					"WHERE " + BaseColumns._ID + " = ?");
			
			stmtUpdate.bindLong(1, m_activeArticleId);
			stmtUpdate.execute();
			stmtUpdate.close();

			if (!m_combinedMode) { 
				m_offlineServices.openArticle(m_activeArticleId, 0);
			}
			
			refresh();
		}
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("feedId", m_feedId);
		out.putInt("activeArticleId", m_activeArticleId);
		//out.putParcelableArrayList("selectedArticles", m_selectedArticles);
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
	
	private class ArticleListAdapter extends SimpleCursorAdapter {
		public ArticleListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			// TODO Auto-generated constructor stub
		}

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_LOADMORE = 3;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Cursor c = (Cursor) getItem(position);
			
			//Log.d(TAG, "@gIVT " + position + " " + c.getInt(0) + " vs " + m_activeArticleId);
			
			if (c.getInt(0) == m_activeArticleId) {
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
					tt.setText(Html.fromHtml("<a href=\""+article.getString(article.getColumnIndex("link")).trim().replace("\"", "\\\"")+"\">" + 
							article.getString(article.getColumnIndex("title")) + "</a>"));
				} else {
					tt.setText(Html.fromHtml(article.getString(article.getColumnIndex("title"))));
				}
			}
			
			ImageView marked = (ImageView)v.findViewById(R.id.marked);
			
			if (marked != null) {
				marked.setImageResource(article.getInt(article.getColumnIndex("marked")) == 1 ? android.R.drawable.star_on : android.R.drawable.star_off);
				
				marked.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						SQLiteStatement stmtUpdate = m_offlineServices.getWritableDb().compileStatement("UPDATE articles SET marked = NOT marked " +
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
						SQLiteStatement stmtUpdate = m_offlineServices.getWritableDb().compileStatement("UPDATE articles SET published = NOT published " +
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
				if (!m_combinedMode) {			
					String excerpt = Jsoup.parse(article.getString(article.getColumnIndex("content"))).text(); 
				
					if (excerpt.length() > 100)
						excerpt = excerpt.substring(0, 100) + "...";
				
					te.setText(excerpt);
				} else {
					te.setVisibility(View.GONE);
				}
			}       	

			ImageView separator = (ImageView)v.findViewById(R.id.headlines_separator);
			
			if (separator != null && m_offlineServices.isSmallScreen()) {
				separator.setVisibility(View.GONE);
			}
			
			TextView content = (TextView)v.findViewById(R.id.content);
			
			if (content != null) {
				if (m_combinedMode) {
					content.setMovementMethod(LinkMovementMethod.getInstance());
					
					content.setText(Html.fromHtml(article.getString(article.getColumnIndex("content")), m_dummyGetter, null));
					
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
				}				
			}
			
			v.findViewById(R.id.attachments_holder).setVisibility(View.GONE);
			
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

						SQLiteStatement stmtUpdate = m_offlineServices.getWritableDb().compileStatement("UPDATE articles SET selected = ? " +
								"WHERE " + BaseColumns._ID + " = ?");
						
						stmtUpdate.bindLong(1, cb.isChecked() ? 1 : 0);
						stmtUpdate.bindLong(2, articleId);
						stmtUpdate.execute();
						stmtUpdate.close();
						
						refresh();
						
						m_offlineServices.initMainMenu();
						
					}
				});
			}
			
			return v;
		}
	}

	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public void setActiveArticleId(int articleId) {
		m_activeArticleId = articleId;
	//	m_adapter.notifyDataSetChanged();
		
		ListView list = (ListView)getView().findViewById(R.id.headlines);
		
		if (list != null) {
			list.setSelection(getArticleIdPosition(articleId));
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
		
		return 0;
	}
	
	public int getArticleCount() {
		return m_adapter.getCount();
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;
			refresh();
		}
	}
	
}
