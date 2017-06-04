package org.fox.ttrss.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
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
import android.widget.ListView;
import android.widget.TextView;

import org.fox.ttrss.BaseFeedlistFragment;
import org.fox.ttrss.R;

public class OfflineFeedsFragment extends BaseFeedlistFragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private static final String ICON_PATH = "/data/org.fox.ttrss/icons/";
	private int m_selectedFeedId;
	private int m_catId = -1;
	private boolean m_enableFeedIcons;
	private Cursor m_cursor;
	private OfflineMasterActivity m_activity;
    private SwipeRefreshLayout m_swipeLayout;
    private boolean m_enableParentBtn = false;
    private ListView m_list;

	public void initialize(int catId, boolean enableParentBtn) {
		m_catId = catId;
        m_enableParentBtn = enableParentBtn;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.browse_headlines:
			if (true) {
				int feedId = getFeedIdAtPosition(info.position);
				if (feedId != -10000) {
					m_activity.onFeedSelected(feedId);
				}
			}
			return true;
		case R.id.catchup_feed:
			int feedId = getFeedIdAtPosition(info.position);
			if (feedId != -10000) {
				m_activity.catchupFeed(feedId, false);
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
		
		getActivity().getMenuInflater().inflate(R.menu.context_feed, menu);

		menu.findItem(R.id.create_shortcut).setEnabled(false);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Cursor cursor = (Cursor)getFeedAtPosition(info.position);
		
		if (cursor != null) 
			menu.setHeaderTitle(cursor.getString(cursor.getColumnIndex("title")));

		super.onCreateContextMenu(menu, v, menuInfo);
		
	}
	
	public Cursor createCursor() {
		String unreadOnly = m_activity.getUnreadOnly() ? "unread > 0" : "1";
		String order = m_prefs.getBoolean("sort_feeds_by_unread", false) ? "unread DESC, title" : "title";
		
		if (m_catId != -1) {
			return m_activity.getDatabase().query("feeds_unread", 
					null, unreadOnly + " AND cat_id = ?",  new String[] { String.valueOf(m_catId) }, null, null, order);
		} else {		
			return m_activity.getDatabase().query("feeds_unread", 
				null, unreadOnly, null, null, null, order);
		}
	}

	public void refresh() {
		try {
			if (!isAdded()) return;

            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);
			
			if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
			
			m_cursor = createCursor();
			
			if (m_cursor != null && m_adapter != null) {
				m_adapter.changeCursor(m_cursor);
				m_adapter.notifyDataSetChanged();
                if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_selectedFeedId = savedInstanceState.getInt("selectedFeedId");
			m_catId = savedInstanceState.getInt("catId");
            m_enableParentBtn = savedInstanceState.getBoolean("enableParentBtn");
		}

		View view = inflater.inflate(R.layout.fragment_feeds, container, false);

        m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.feeds_swipe_container);

        m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

		m_list = (ListView)view.findViewById(R.id.feeds);

		initDrawerHeader(inflater, view, m_list, m_activity, m_prefs, !m_enableParentBtn);

		if (m_enableParentBtn) {
            View layout = inflater.inflate(R.layout.feeds_goback, container, false);

            layout.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT,
                    ListView.LayoutParams.WRAP_CONTENT));

            layout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    m_activity.getSupportFragmentManager().popBackStack();
                }
            });

            m_list.addHeaderView(layout, null, false);
        }

        m_cursor = createCursor();
		
		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, m_cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);

		m_list.setAdapter(m_adapter);
		m_list.setOnItemClickListener(this);
		registerForContextMenu(m_list);

		m_enableFeedIcons = m_prefs.getBoolean("download_feed_icons", false);
		
		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_activity = (OfflineMasterActivity)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("selectedFeedId", m_selectedFeedId);
		out.putInt("catId", m_catId);
        out.putBoolean("enableParentBtn", m_enableParentBtn);
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);
		
		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);
			
			if (cursor != null) {
				int feedId = (int) cursor.getLong(0);
				Log.d(TAG, "clicked on feed " + feedId);
				
                m_activity.onFeedSelected(feedId);

                m_selectedFeedId = feedId;
				
				m_adapter.notifyDataSetChanged();
			}
		}
	}

	private class FeedListAdapter extends SimpleCursorAdapter {
		

		public FeedListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
		}

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_SELECTED = 1;
		
		public static final int VIEW_COUNT = VIEW_SELECTED+1;

		@Override
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

        @Override
        public boolean isEmpty() {
            return m_enableParentBtn ? false : super.isEmpty();
        }

		@Override
		public int getItemViewType(int position) {
			Cursor cursor = (Cursor) this.getItem(position);
			
			if (!m_activity.isSmallScreen() && cursor.getLong(0) == m_selectedFeedId) {
				return VIEW_SELECTED;
			} else {
				return VIEW_NORMAL;				
			}			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;

			Cursor cursor = (Cursor)getItem(position);

			if (v == null) {
				int layoutId = R.layout.feeds_row;
				
				switch (getItemViewType(position)) {
				case VIEW_SELECTED:
					layoutId = R.layout.feeds_row_selected;
					break;
				}
				
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutId, null);

			}

			TextView tt = (TextView) v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(cursor.getString(cursor.getColumnIndex("title")));
			}

			TextView tu = (TextView) v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(cursor.getInt(cursor.getColumnIndex("unread"))));
				tu.setVisibility((cursor.getInt(cursor.getColumnIndex("unread")) > 0) ? View.VISIBLE : View.INVISIBLE);
			}

			/*ImageButton ib = (ImageButton) v.findViewById(R.id.feed_menu_button);
			
			if (ib != null) {
				ib.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						getActivity().openContextMenu(v);
					}
				});								
			}*/

			return v;
		} 
	}

	public void sortFeeds() {
		try {
			refresh();
		} catch (NullPointerException e) {
			// activity is gone?
		} catch  (IllegalStateException e) {
			// we're probably closing and DB is gone already
		}
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		sortFeeds();
		m_enableFeedIcons = m_prefs.getBoolean("download_feed_icons", false);
		
	}

	public int getFeedIdAtPosition(int position) {
        /* if (m_list != null) {
            Cursor c = (Cursor) m_list.getItemAtPosition(position);

            if (c != null) {
                int feedId = c.getInt(0);
                return feedId;
            }
        } */

        Cursor tmp = getFeedAtPosition(position);

        if (tmp != null) {
            int id = tmp.getInt(0);

            return id;
        }

		return -10000;
	}

    public Cursor getFeedAtPosition(int position) {

        if (m_list != null) {
            return (Cursor) m_list.getItemAtPosition(position);
        }

        return null;
    }

	public void setSelectedFeedId(int feedId) {
		m_selectedFeedId = feedId;
		refresh();
	}

}
