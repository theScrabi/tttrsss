package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class FeedsFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();

	protected ArrayList<Feed> m_feeds = new ArrayList<Feed>();
	protected FeedsListAdapter m_adapter;
	protected SharedPreferences m_prefs;
	protected String m_sessionId;
	protected int m_activeFeedId;
	protected long m_lastUpdate;
	protected Gson m_gson = new Gson();
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_sessionId = savedInstanceState.getString("sessionId");
			m_activeFeedId = savedInstanceState.getInt("activeFeedId");
			m_lastUpdate = savedInstanceState.getLong("lastUpdate");
		}
		
		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		m_adapter = new FeedsListAdapter(getActivity(), R.id.feeds_row, m_feeds);
		
		ListView list = (ListView) view.findViewById(R.id.feeds);
		
		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
		}

		return view;    	
	}

	@Override
	public void onStart() {
		super.onStart();
		
		if (new Date().getTime() - m_lastUpdate > 30*1000) {
			refresh();
		} else {
			//
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
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
						m_lastUpdate = new Date().getTime();

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
								
								/* if (getView() != null) {								
									View v = getView().findViewById(R.id.loading_progress);
								
									if (v != null) v.setVisibility(View.GONE);
								
									v = getView().findViewById(R.id.no_unread_feeds);
									
									if (v != null) {
										if (m_feeds.size() > 0)
											v.setVisibility(View.INVISIBLE);
										else
											v.setVisibility(View.VISIBLE);
									}
								} */
								
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
		out.putInt("activeFeedId", m_activeFeedId);
		out.putLong("lastUpdate", m_lastUpdate);
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

			Feed feed = items.get(position);
			
			if (v == null) {
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.feeds_row, null);
			}
			
			TextView title = (TextView) v.findViewById(R.id.title);
			
			if (title != null) {
				title.setText(feed.title);
				
				if (feed.id == m_activeFeedId) {
					title.setTextAppearance(getContext(), R.style.SelectedFeed);
				} else {
					title.setTextAppearance(getContext(), R.style.Feed);
				}
			}
			
			TextView unread = (TextView) v.findViewById(R.id.unread_counter);
			
			if (unread != null) {
				unread.setText(String.valueOf(feed.unread));
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
			if (feed.unread != this.unread)
				return feed.unread - this.unread;
			else
				return this.title.compareTo(feed.title);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);
		
		if (list != null) {
			Feed feed = (Feed) list.getItemAtPosition(position);
			
			if (feed != null) {
				Log.d(TAG, "clicked on feed " + feed.id);
				
				viewFeed(feed.id);
				
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
