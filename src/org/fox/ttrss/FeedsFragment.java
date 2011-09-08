package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

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

//	protected ArrayList<Feed> m_feeds = new ArrayList<Feed>();
	protected FeedsListAdapter m_adapter;
	protected SharedPreferences m_prefs;
	protected String m_sessionId;
	protected int m_activeFeedId;
	protected long m_lastUpdate;
	protected Gson m_gson = new Gson();
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		m_sessionId = m_prefs.getString("last_session_id", null);
		
		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_activeFeedId = savedInstanceState.getInt("activeFeedId");
			m_lastUpdate = savedInstanceState.getLong("lastUpdate");
		}
		
		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		DatabaseHelper helper = new DatabaseHelper(getActivity());
		Cursor cursor = helper.getReadableDatabase().query("feeds", null, null, null, null, null, "unread DESC");
		
		m_adapter = new FeedsListAdapter(getActivity(), R.layout.feeds_row, cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);
		
		ListView list = (ListView) view.findViewById(R.id.feeds);
		
		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
		}

		updateSelf();
		
		return view;    	
	}

	protected void updateSessionId(String sessionId) {
		m_sessionId = sessionId;

		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("last_session_id", m_sessionId);	
		editor.commit();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	public void initialize(String sessionId) {
		m_sessionId = sessionId;
	}
	
	private void updateSelf() {
		ApiRequest task = new ApiRequest(m_sessionId, 
				m_prefs.getString("ttrss_url", null),
				m_prefs.getString("login", null),
				m_prefs.getString("password", null)) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null && getAuthStatus() == STATUS_OK) {
					try {
						JsonArray feeds_object = (JsonArray) result.getAsJsonArray();
						
						Type listType = new TypeToken<List<Feed>>() {}.getType();
						List<Feed> feeds = m_gson.fromJson(feeds_object, listType);

						DatabaseHelper dh = new DatabaseHelper(getActivity());
						SQLiteDatabase db = dh.getWritableDatabase();

						db.execSQL("DELETE FROM FEEDS");
						
						SQLiteStatement stmt = db.compileStatement("INSERT INTO feeds " +
								"("+BaseColumns._ID+", title, feed_url, unread, has_icon, cat_id, last_updated) " +
								"VALUES (?, ?, ?, ?, ?, ?, ?);");
						
						for (Feed feed : feeds) {
							stmt.bindLong(1, feed.id);
							stmt.bindString(2, feed.title);
							stmt.bindString(3, feed.feed_url);
							stmt.bindLong(4, feed.unread);
							stmt.bindLong(5, 1);
							stmt.bindLong(6, feed.cat_id);
							stmt.bindLong(7, feed.last_updated);
							stmt.execute();
						}
						
						db.close();
						
						m_adapter.notifyDataSetChanged();
					
					} catch (Exception e) {
						e.printStackTrace();
					}										
				}
				
			} 
		};
		
		task.execute(new HashMap<String,String>() {   
			{
				put("sid", m_sessionId);
				put("op", "getFeeds");
				put("cat_id", "-3");
				put("unread_only", "true");
			}			 
		});

	} 

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
		out.putInt("activeFeedId", m_activeFeedId);
		out.putLong("lastUpdate", m_lastUpdate);
	}
	
	private class FeedsListAdapter extends SimpleCursorAdapter {

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
		
		frag.initialize(m_sessionId, feedId);
		
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.replace(R.id.headlines_container, frag);
		ft.commit();
		
		m_adapter.notifyDataSetChanged();

	}	

}
