package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class FeedsFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();

	protected FeedsListAdapter m_adapter;
	protected SharedPreferences m_prefs;
	protected int m_activeFeedId;
	protected Gson m_gson = new Gson();
	protected Cursor m_cursor;
	protected SQLiteDatabase m_db;

	private Timer m_timer;
	private TimerTask m_updateTask = new TimerTask() {
		@Override
		public void run() {

			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateSelf();
				}				
			});			
		}		
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_activeFeedId = savedInstanceState.getInt("activeFeedId");
		}
		
		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		DatabaseHelper helper = new DatabaseHelper(getActivity());
		
		m_db = helper.getReadableDatabase();		
		m_cursor = m_db.query("feeds_unread", null, "unread > 0", null, null, null, "title");
		
		m_adapter = new FeedsListAdapter(getActivity(), R.layout.feeds_row, m_cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);
		
		ListView list = (ListView) view.findViewById(R.id.feeds);
		
		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
			list.setEmptyView(view.findViewById(R.id.no_unread_feeds));
			list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
		}

		View pb = view.findViewById(R.id.loading_progress);
		
		if (pb != null && m_cursor.getCount() == 0)
			pb.setVisibility(View.VISIBLE);
	
		m_timer = new Timer("UpdateFeeds");
		m_timer.schedule(m_updateTask, 1000L, 60*1000L);

		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_cursor.close();
		m_db.close();
		
		m_timer.cancel();
		m_timer = null;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	private void updateSelf() {
		String sessionId = ((MainActivity)getActivity()).getSessionId();
		
		ApiRequest task = new ApiRequest(sessionId, 
				m_prefs.getString("ttrss_url", null),
				m_prefs.getString("login", null),
				m_prefs.getString("password", null)) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null && getAuthStatus() == STATUS_OK) {
					try {
						try {
							((MainActivity)getActivity()).setSessionId(getSessionId());
						} catch (NullPointerException e) {
							//
						}
						
						JsonArray feeds_object = (JsonArray) result.getAsJsonArray();
						
						Type listType = new TypeToken<List<Feed>>() {}.getType();
						List<Feed> feeds = m_gson.fromJson(feeds_object, listType);

						DatabaseHelper dh = new DatabaseHelper(getActivity());
						SQLiteDatabase db = dh.getWritableDatabase();

						SQLiteStatement stmtUpdate = db.compileStatement("UPDATE feeds SET " +
								"title = ?, feed_url = ?, has_icon = ?, cat_id = ?, last_updated = ? WHERE " +
								BaseColumns._ID + " = ?");

						SQLiteStatement stmtInsert = db.compileStatement("INSERT INTO feeds " +
								"("+BaseColumns._ID+", title, feed_url, has_icon, cat_id, last_updated) " +
								"VALUES (?, ?, ?, ?, ?, ?);");

						for (Feed feed : feeds) {
							Cursor c = db.query("feeds", new String[] { BaseColumns._ID } , BaseColumns._ID + "=?", 
									new String[] { String.valueOf(feed.id) }, null, null, null);
							
							if (c.getCount() != 0) {
								stmtUpdate.bindString(1, feed.title);
								stmtUpdate.bindString(2, feed.feed_url);								
								stmtUpdate.bindLong(3, feed.has_icon ? 1 : 0);
								stmtUpdate.bindLong(4, feed.cat_id);
								stmtUpdate.bindLong(5, feed.last_updated);
								stmtUpdate.bindLong(6, feed.id);
								stmtUpdate.execute();								
								
							} else {
								stmtInsert.bindLong(1, feed.id);
								stmtInsert.bindString(2, feed.title);
								stmtInsert.bindString(3, feed.feed_url);
								stmtInsert.bindLong(4, feed.has_icon ? 1 : 0);
								stmtInsert.bindLong(5, feed.cat_id);
								stmtInsert.bindLong(6, feed.last_updated);
								stmtInsert.execute();
							}
							
							c.close();
						} 
						
						// TODO delete not returned feeds which has no data here
						
						db.close();
						
						View pb = getView().findViewById(R.id.loading_progress);
						
						if (pb != null) pb.setVisibility(View.INVISIBLE);

						updateListView();
						
					} catch (Exception e) {
						e.printStackTrace();
					}										
				}
				
			} 
		};
		
		task.execute(new HashMap<String,String>() {   
			{
				put("sid", ((MainActivity)getActivity()).getSessionId());
				put("op", "getFeeds");
				put("cat_id", "-3");
				put("unread_only", "0");
			}			 
		});

	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("activeFeedId", m_activeFeedId);
	}
	
	class FeedsListAdapter extends SimpleCursorAdapter {

		private Context context;
		private int layout;
		
		public FeedsListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			
	        this.context = context;
	        this.layout = layout;
		}		
		
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);
		
		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);
			
			if (cursor != null) {
				int feedId = (int) cursor.getLong(0);

				Log.d(TAG, "clicked on feed " + feedId);
				
				viewFeed(feedId);				
			}			
		}		
	} 

	private void viewFeed(int feedId) {
		m_activeFeedId = feedId;
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();			
		HeadlinesFragment frag = new HeadlinesFragment();
		
		frag.initialize(feedId);
		
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.replace(R.id.headlines_container, frag);
		ft.commit();
		
		m_adapter.notifyDataSetChanged();

	}

	public synchronized void updateListView() {
		m_cursor.requery();
		m_adapter.notifyDataSetChanged();		
	}	

}
