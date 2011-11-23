package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class FeedsFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private String m_sessionId;
	//private int m_activeFeedId;
	private FeedListAdapter m_adapter;
	private List<Feed> m_feeds = new ArrayList<Feed>();
	private OnFeedSelectedListener m_feedSelectedListener;
	
	public interface OnFeedSelectedListener {
		public void onFeedSelected(Feed feed);
	}
	
	public void showLoading(boolean show) {
		View v = getView();
		
		if (v != null) {
			v = v.findViewById(R.id.loading_container);
	
			if (v != null)
				v.setVisibility(show ? View.VISIBLE : View.GONE);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			//m_sessionId = savedInstanceState.getString("sessionId");
			//m_activeFeedId = savedInstanceState.getInt("activeFeedId");
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);		
		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, (ArrayList<Feed>)m_feeds);
		list.setAdapter(m_adapter);
		list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);        
		list.setOnItemClickListener(this);

		if (m_sessionId != null) 
			refresh();
		
		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_feedSelectedListener = (OnFeedSelectedListener) activity;
		
		m_sessionId = ((MainActivity)activity).getSessionId();
	
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putString("sessionId", m_sessionId);
		//out.putInt("activeFeedId", m_activeFeedId);
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Feed feed = (Feed)list.getItemAtPosition(position);
			m_feedSelectedListener.onFeedSelected(feed);
		}
	}

	public void refresh() {
		FeedsRequest fr = new FeedsRequest();
		
		fr.setApi(m_prefs.getString("ttrss_url", null));

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getFeeds");
				put("sid", m_sessionId);
				put("cat_id", "-3");
				put("unread_only", "true");
			}			 
		};

		fr.execute(map);
	}
	
	public void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView)getView().findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(status);
		}
		
		View pb = getView().findViewById(R.id.loading_progress);
		
		if (pb != null) {
			pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
		}
	}
	
	private class FeedsRequest extends ApiRequest {
			
			protected void onPostExecute(JsonElement result) {
				if (result != null) {
					try {			
						JsonObject rv = result.getAsJsonObject();
	
						Gson gson = new Gson();
						
						int status = rv.get("status").getAsInt();
						
						if (status == 0) {
							JsonArray content = rv.get("content").getAsJsonArray();
							if (content != null) {
								Type listType = new TypeToken<List<Feed>>() {}.getType();
								final List<Feed> feeds = gson.fromJson(content, listType);
								
								getActivity().runOnUiThread(new Runnable() {
									public void run() {
										m_feeds.clear();
										
										for (Feed f : feeds) 
											m_feeds.add(f);
										
										m_adapter.notifyDataSetInvalidated();
										
										showLoading(false);
									}
								});
							}
						} else {
							JsonObject content = rv.get("content").getAsJsonObject();
							
							if (content != null) {
								String error = content.get("error").getAsString();
	
								/* m_sessionId = null;
	
								if (error.equals("LOGIN_ERROR")) {
									setLoadingStatus(R.string.login_wrong_password, false);
								} else if (error.equals("API_DISABLED")) {
									setLoadingStatus(R.string.login_api_disabled, false);
								} else {
									setLoadingStatus(R.string.login_failed, false);
								} */
								
								// TODO report error back to MainActivity
							}							
						}
					} catch (Exception e) {
						e.printStackTrace();
						
						MainActivity ma = (MainActivity)getActivity();
						ma.toast("Error parsing feedlist: incorrect format");
					}
				} else {
					MainActivity ma = (MainActivity)getActivity();
					ma.toast("Error parsing feedlist: null object.");
				}
				
				return;

		    }
		}

	private class FeedListAdapter extends ArrayAdapter<Feed> {
		private ArrayList<Feed> items;

		public FeedListAdapter(Context context, int textViewResourceId, ArrayList<Feed> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			Feed active_feed = ((MainActivity)getActivity()).getActiveFeed();
			
			View v = convertView;

			Feed feed = items.get(position);

			if (v == null) {
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.feeds_row, null);
			}

			TextView tt = (TextView) v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(feed.title);
				
				if (active_feed != null && feed.id == active_feed.id)
					tt.setTextAppearance(getContext(), R.style.SelectedFeed);
				else
					tt.setTextAppearance(getContext(), R.style.Feed);
			}

			TextView tu = (TextView) v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(feed.unread));
				tu.setVisibility((feed.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}        	

			return v;
		}
	}

}
