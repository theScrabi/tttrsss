package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private FeedList m_feeds = new FeedList();
	private OnFeedSelectedListener m_feedSelectedListener;
	private int m_selectedFeedId;
	
	public interface OnFeedSelectedListener {
		public void onFeedSelected(Feed feed);
	}

	class FeedUnreadComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.unread != b.unread)
					return b.unread - a.unread;
				else
					return a.title.compareTo(b.title);
			}
		
	}
	

	class FeedTitleComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.id >= 0 && b.id >= 0)
				return a.title.compareTo(b.title);
			else
				return a.id - b.id;
		}
		
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_selectedFeedId = savedInstanceState.getInt("selectedFeedId");
			m_feeds = savedInstanceState.getParcelable("feeds");
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);		
		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, (ArrayList<Feed>)m_feeds);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		
		if (m_feeds == null || m_feeds.size() == 0)
			refresh(false);
		else
			view.findViewById(R.id.loading_progress).setVisibility(View.GONE);
		
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
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("selectedFeedId", m_selectedFeedId);
		out.putParcelable("feeds", m_feeds);
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Feed feed = (Feed)list.getItemAtPosition(position);
			m_feedSelectedListener.onFeedSelected(feed);
			m_selectedFeedId = feed.id;
			m_adapter.notifyDataSetChanged();
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean background) {
		FeedsRequest req = new FeedsRequest(getActivity().getApplicationContext());
		
		final String sessionId = ((MainActivity)getActivity()).getSessionId();
		final boolean unreadOnly = ((MainActivity)getActivity()).getUnreadOnly();
		
		if (sessionId != null) {
			
			if (!background) {
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setLoadingStatus(R.string.blank, true);
					}
				});
			}
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getFeeds");
					put("sid", sessionId);
					put("cat_id", "-4");
					if (unreadOnly) {
						put("unread_only", String.valueOf(unreadOnly));
					}
				}			 
			};

			req.execute(map);
		
		}
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
	
	private class FeedsRequest extends ApiRequest {
			
		public FeedsRequest(Context context) {
			super(context);
		}

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
							
							m_feeds.clear();
							
							for (Feed f : feeds)
								if (f.id > -10) // skip labels for now
									m_feeds.add(f);
							
							sortFeeds();
							
							if (m_feeds.size() == 0)
								setLoadingStatus(R.string.error_no_feeds, false);
							else
								setLoadingStatus(R.string.blank, false);
									
						}
					} else {
						MainActivity activity = (MainActivity)getActivity();							
						activity.login();
					}
				} catch (Exception e) {
					e.printStackTrace();
					setLoadingStatus(R.string.error_invalid_object, false);
					// report invalid object received
				}
			} else {
				// report null object received, unless we've been awakened from sleep right in the right time
				// so that current request failed
				if (m_feeds.size() == 0) setLoadingStatus(R.string.error_no_data, false);
			}
			
			return;
	    }
	}

	private class FeedListAdapter extends ArrayAdapter<Feed> {
		private ArrayList<Feed> items;

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_SELECTED = 1;
		
		public static final int VIEW_COUNT = VIEW_SELECTED+1;

		public FeedListAdapter(Context context, int textViewResourceId, ArrayList<Feed> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}

		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Feed feed = items.get(position);
			
			if (feed.id == m_selectedFeedId) {
				return VIEW_SELECTED;
			} else {
				return VIEW_NORMAL;				
			}			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;

			Feed feed = items.get(position);

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
				tt.setText(feed.title);
			}

			TextView tu = (TextView) v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(feed.unread));
				tu.setVisibility((feed.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}        	

			return v;
		}
	}

	public void sortFeeds() {
		Comparator<Feed> cmp;
		
		if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
			cmp = new FeedUnreadComparator();
		} else {
			cmp = new FeedTitleComparator();
		}
		
		Collections.sort(m_feeds, cmp);
		m_adapter.notifyDataSetInvalidated();
	}
}
