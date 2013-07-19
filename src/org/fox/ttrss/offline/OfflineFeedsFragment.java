package org.fox.ttrss.offline;

import java.io.File;

import org.fox.ttrss.R;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class OfflineFeedsFragment extends Fragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private static final String ICON_PATH = "/data/org.fox.ttrss/icons/";
	private int m_selectedFeedId;
	private int m_catId = -1;
	private boolean m_enableFeedIcons;
	private Cursor m_cursor;
	private OfflineFeedsActivity m_activity;
	
	public void initialize(int catId) {
		m_catId = catId;
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
		case R.id.browse_articles:
			if (true) {
				int feedId = getFeedIdAtPosition(info.position);
				if (feedId != -10000) {
					m_activity.openFeedArticles(feedId, false);
				}
			}
			return true;
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
		
		getActivity().getMenuInflater().inflate(R.menu.feed_menu, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Cursor cursor = (Cursor)m_adapter.getItem(info.position);
		
		if (cursor != null) 
			menu.setHeaderTitle(cursor.getString(cursor.getColumnIndex("title")));

		if (!m_activity.isSmallScreen()) {
			menu.findItem(R.id.browse_articles).setVisible(false);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	public Cursor createCursor() {
		String unreadOnly = m_activity.getUnreadOnly() ? "unread > 0" : "1";
		String order = m_prefs.getBoolean("sort_feeds_by_unread", false) ? "unread DESC, title" : "title";
		
		if (m_catId != -1) {
			return m_activity.getReadableDb().query("feeds_unread", 
					null, unreadOnly + " AND cat_id = ?",  new String[] { String.valueOf(m_catId) }, null, null, order);
		} else {		
			return m_activity.getReadableDb().query("feeds_unread", 
				null, unreadOnly, null, null, null, order);
		}
	}
	
	public void refresh() {
		try {
			if (!isAdded()) return;
			
			if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
			
			m_cursor = createCursor();
			
			if (m_cursor != null && m_adapter != null) {
				m_adapter.changeCursor(m_cursor);
				m_adapter.notifyDataSetChanged();
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
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);
		
		m_cursor = createCursor();
		
		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, m_cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);

		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		list.setEmptyView(view.findViewById(R.id.no_feeds));
		registerForContextMenu(list);

		view.findViewById(R.id.loading_container).setVisibility(View.GONE);
		
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
		
		m_activity = (OfflineFeedsActivity)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("selectedFeedId", m_selectedFeedId);
		out.putInt("catId", m_catId);
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);
		
		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);
			
			if (cursor != null) {
				int feedId = (int) cursor.getLong(0);
				Log.d(TAG, "clicked on feed " + feedId);
				
				if (!m_activity.isSmallScreen() && "ARTICLES".equals(m_prefs.getString("default_view_mode", "HEADLINES"))) {
					m_activity.openFeedArticles(feedId, false);
				} else {
					m_activity.onFeedSelected(feedId);
				}
				
				if (!m_activity.isSmallScreen())
					m_selectedFeedId = feedId;
				
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
			
			ImageView icon = (ImageView)v.findViewById(R.id.icon);
			
			if (icon != null) {
				
				if (m_enableFeedIcons) {
					
					try {
						File storage = Environment.getExternalStorageDirectory();
						
						File iconFile = new File(storage.getAbsolutePath() + ICON_PATH + cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)) + ".ico");
						if (iconFile.exists()) {
							Bitmap bmpOrig = BitmapFactory.decodeFile(iconFile.getAbsolutePath());		
							if (bmpOrig != null) {
								icon.setImageBitmap(bmpOrig);
							}
						} else {
							icon.setImageResource(cursor.getInt(cursor.getColumnIndex("unread")) > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
						}
					} catch (NullPointerException e) {
						icon.setImageResource(cursor.getInt(cursor.getColumnIndex("unread")) > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
					}
					
				} else {
					icon.setImageResource(cursor.getInt(cursor.getColumnIndex("unread")) > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
				}
				
			}

			ImageButton ib = (ImageButton) v.findViewById(R.id.feed_menu_button);
			
			if (ib != null) {
				if (m_activity.isDarkTheme())
					ib.setImageResource(R.drawable.ic_mailbox_collapsed_holo_dark);
				
				ib.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						getActivity().openContextMenu(v);
					}
				});								
			}

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
		Cursor c = (Cursor)m_adapter.getItem(position);
		
		if (c != null) {
			int feedId = c.getInt(0);
			return feedId;
		}
		
		return -10000;
	}
	
	public void setSelectedFeedId(int feedId) {
		m_selectedFeedId = feedId;
		refresh();
	}

}
