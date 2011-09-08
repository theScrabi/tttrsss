package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class FeedsFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	protected ArrayList<Feed> m_feeds = new ArrayList<Feed>();
	protected FeedsListAdapter m_adapter;
	protected SharedPreferences m_prefs;
	protected String m_sessionId;
	protected Gson m_gson = new Gson();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
		}
		
		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		m_adapter = new FeedsListAdapter(getActivity(), R.id.feeds_row, m_feeds);
		
		ListView list = (ListView) view.findViewById(R.id.feeds);
		
		if (list != null) {
			list.setAdapter(m_adapter);			
		}
		
		return view;    	
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

		refresh();
	}

	public void initialize(String sessionId) {
		m_sessionId = sessionId;
	}
	
	@SuppressWarnings("unchecked")
	private void refresh() {
		ApiRequest task = new ApiRequest(null, m_prefs.getString("ttrss_url", null)) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null) {
					try {					
						JsonObject rv = result.getAsJsonObject();

						int status = rv.get("status").getAsInt();
						
						if (status == 0) {
							Type listType = new TypeToken<List<Feed>>() {}.getType();
							List<Feed> feeds = m_gson.fromJson(rv.get("content"), listType);
							
							Collections.sort(feeds);
							
							if (feeds != null) {
								m_feeds.clear();
								
								for (Feed feed : feeds) {
									if (feed.id == -4 || feed.id > 0)
										m_feeds.add(feed);
								}						
								
								m_adapter.notifyDataSetChanged();
								
								View v = getView().findViewById(R.id.loading_progress);
								
								if (v != null) v.setVisibility(View.GONE);
								
								return;
							}
						} else {
							JsonObject content = rv.get("content").getAsJsonObject();
							
							if (content != null) {
								String error = content.get("error").getAsString();

								if (error.equals("NOT_LOGGED_IN")) {
									MainActivity ma = (MainActivity)getActivity();
									
									if (ma != null) ma.logout();									
								}
							}							
						}
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
				put("cat_id", "-4");
				put("unread_only", "true");
			}			 
		});

	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
	}
	
	private class FeedsListAdapter extends ArrayAdapter<Feed> {
		private ArrayList<Feed> items;
		
		public FeedsListAdapter(Context context, int textViewResourceId, ArrayList<Feed> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			Feed item = items.get(position);
			
			if (v == null) {
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.feeds_row, null);
			}
			
			TextView title = (TextView) v.findViewById(R.id.title);
			
			if (title != null) {
				title.setText(item.title);
			}
			
			TextView unread = (TextView) v.findViewById(R.id.unread_counter);
			
			if (unread != null) {
				unread.setText(String.valueOf(item.unread));
			}
			
			return v;
		}
	}
	
	private class Feed implements Comparable<Feed> {
		String feed_url;
		String title;
		int id;
		int unread;
		boolean has_icon;
		int cat_id;
		int last_updated;
		
		@Override
		public int compareTo(Feed feed) {
			return feed.unread - this.unread;
		}
	}
}
