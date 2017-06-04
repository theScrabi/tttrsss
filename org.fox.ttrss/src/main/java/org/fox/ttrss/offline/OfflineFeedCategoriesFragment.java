package org.fox.ttrss.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.fox.ttrss.BaseFeedlistFragment;
import org.fox.ttrss.R;

public class OfflineFeedCategoriesFragment extends BaseFeedlistFragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedCategoryListAdapter m_adapter;
	private int m_selectedCatId;
	private Cursor m_cursor;
	private OfflineMasterActivity m_activity;
    private SwipeRefreshLayout m_swipeLayout;
    private ListView m_list;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.context_category, menu);

		menu.findItem(R.id.create_shortcut).setEnabled(false);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Cursor cursor = getCatAtPosition(info.position);
		
		if (cursor != null) 
			menu.setHeaderTitle(cursor.getString(cursor.getColumnIndex("title")));

		super.onCreateContextMenu(menu, v, menuInfo);
		
	}
	
	public Cursor createCursor() {
		String unreadOnly = BaseColumns._ID + ">= 0 AND " + (m_activity.getUnreadOnly() ? "unread > 0" : "1");
		
		String order = m_prefs.getBoolean("sort_feeds_by_unread", false) ? "unread DESC, title" : "title";
		
		return m_activity.getDatabase().query("cats_unread", 
				null, unreadOnly, null, null, null, order);
	}
	
	public void refresh() {
        if (!isAdded()) return;

        if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);

		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
		
		m_cursor = createCursor();
		
		if (m_cursor != null && m_adapter != null) {
			m_adapter.changeCursor(m_cursor);
			m_adapter.notifyDataSetChanged();
            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
		}
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
				int catId = getCatIdAtPosition(info.position);
				if (catId != -10000) {
					m_activity.onCatSelected(catId, true);
				}
			}
			return true;
		case R.id.browse_feeds:
			if (true) {
				int catId = getCatIdAtPosition(info.position);
				if (catId != -10000) {
					m_activity.onCatSelected(catId, false);
				}
			}
			return true;
		case R.id.catchup_category:
			if (true) {
				int catId = getCatIdAtPosition(info.position);
				if (catId != -10000) {
					m_activity.catchupFeed(catId, true);
				}
			}
			return true;	
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_selectedCatId = savedInstanceState.getInt("selectedFeedId");
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

		m_cursor = createCursor();
		
		m_adapter = new FeedCategoryListAdapter(getActivity(), R.layout.feeds_row, m_cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);

		initDrawerHeader(inflater, view, m_list, m_activity, m_prefs, true);

		m_list.setAdapter(m_adapter);
		m_list.setOnItemClickListener(this);
		registerForContextMenu(m_list);

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

		out.putInt("selectedFeedId", m_selectedCatId);
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);
		
		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);
			
			if (cursor != null) {
				int feedId = (int) cursor.getLong(0);
				Log.d(TAG, "clicked on feed " + feedId);

                m_activity.onCatSelected(feedId);

                m_selectedCatId = feedId;

				m_adapter.notifyDataSetChanged();
			}
		}
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
	
	private class FeedCategoryListAdapter extends SimpleCursorAdapter {
		

		public FeedCategoryListAdapter(Context context, int layout, Cursor c,
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
		public int getItemViewType(int position) {
			Cursor cursor = (Cursor) this.getItem(position);
			
			if (!m_activity.isSmallScreen() && cursor.getLong(0) == m_selectedCatId) {
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

			ImageView icon = (ImageView) v.findViewById(R.id.icon);

			if (icon != null) {
				TypedValue tv = new TypedValue();

				m_activity.getTheme().resolveAttribute(R.attr.ic_folder_outline, tv, true);
				icon.setImageResource(tv.resourceId);

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
			} */


			return v;
		} 
	}

	public void sortCategories() {
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

		sortCategories();
	}

	public int getCatIdAtPosition(int position) {

        /* if (m_list != null) {
            Cursor c = (Cursor) m_list.getItemAtPosition(position);

            if (c != null) {
                int catId = c.getInt(0);
                return catId;
            }
        } */

        Cursor tmp = getCatAtPosition(position);

        if (tmp != null) {
            int id = tmp.getInt(0);

            return id;
        }

		return -10000;
	}

    public Cursor getCatAtPosition(int position) {

        if (m_list != null) {
            return (Cursor) m_list.getItemAtPosition(position);
        }

        return null;
    }

	public void setSelectedFeedId(int feedId) {
		m_selectedCatId = feedId;
		refresh();
	}

}
