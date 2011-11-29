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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class FeedCategoriesFragment extends Fragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedCategoryListAdapter m_adapter;
	private FeedCategoryList m_cats = new FeedCategoryList();
	private int m_selectedCatId = -100;
	private OnCatSelectedListener m_catSelectedListener;

	public interface OnCatSelectedListener {
		public void onCatSelected(FeedCategory cat);
	}
	
	class CatUnreadComparator implements Comparator<FeedCategory> {
		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.unread != b.unread)
					return b.unread - a.unread;
				else
					return a.title.compareTo(b.title);
			}
	}
	

	class CatTitleComparator implements Comparator<FeedCategory> {

		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.id >= 0 && b.id >= 0)
				return a.title.compareTo(b.title);
			else
				return a.id - b.id;
		}
		
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.category_menu, menu);
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    FeedCategory cat = m_adapter.getItem(info.position);
	    
		MainActivity activity = (MainActivity)getActivity();

	    if (cat != null) {
			m_selectedCatId = cat.id;
			m_adapter.notifyDataSetChanged();

	    	switch (item.getItemId()) {
	    	case R.id.browse_articles:
	    		activity.viewCategory(cat, true);
	    		break;
	    	case R.id.browse_feeds:
	    		activity.viewCategory(cat, false);
	    		break;
	    	}
	    }
			
		return true;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {  
		if (savedInstanceState != null) {
			m_selectedCatId = savedInstanceState.getInt("selectedCatId");
			m_cats = savedInstanceState.getParcelable("cats");
		}	
		
		View view = inflater.inflate(R.layout.cats_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);		
		m_adapter = new FeedCategoryListAdapter(getActivity(), R.layout.feeds_row, (ArrayList<FeedCategory>)m_cats);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		registerForContextMenu(list);
		
		if (m_cats == null || m_cats.size() == 0)
			refresh(false);
		else
			view.findViewById(R.id.loading_progress).setVisibility(View.GONE);
		
		return view; 
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		

		m_catSelectedListener = (OnCatSelectedListener)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("selectedCatId", m_selectedCatId);
		out.putParcelable("cats", m_cats);
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
	
	@SuppressWarnings("unchecked")
	public void refresh(boolean background) {
		CatsRequest req = new CatsRequest(getActivity().getApplicationContext());
		
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
			
			@SuppressWarnings("serial")
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getCategories");
					put("sid", sessionId);
					if (unreadOnly) {
						put("unread_only", String.valueOf(unreadOnly));
					}
				}			 
			};

			req.execute(map);
		
		}
	}
	
	private class CatsRequest extends ApiRequest {
		
		public CatsRequest(Context context) {
			super(context);
		}
		
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonArray content = result.getAsJsonArray();
					if (content != null) {
						Type listType = new TypeToken<List<FeedCategory>>() {}.getType();
						final List<FeedCategory> cats = new Gson().fromJson(content, listType);
						
						m_cats.clear();
						
						int apiLevel = ((MainActivity)getActivity()).getApiLevel();
						
						// virtual cats implemented in getCategories since api level 1
						if (apiLevel == 0) {
							m_cats.add(new FeedCategory(-1, "Special", 0));
							m_cats.add(new FeedCategory(-2, "Labels", 0));
							m_cats.add(new FeedCategory(0, "Uncategorized", 0));
						}
						
						for (FeedCategory c : cats)
							m_cats.add(c);
						
						sortCats();
						
						if (m_cats.size() == 0)
							setLoadingStatus(R.string.no_feeds_to_display, false);
						else
							setLoadingStatus(R.string.blank, false);
						
						return;
					}
							
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}

			if (m_lastError == ApiError.LOGIN_FAILED) {
				MainActivity activity = (MainActivity)getActivity();							
				activity.login();
			} else {
				setLoadingStatus(getErrorMessage(), false);
			}
		}

	}

	public void sortCats() {
		Comparator<FeedCategory> cmp;
		
		if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
			cmp = new CatUnreadComparator();
		} else {
			cmp = new CatTitleComparator();
		}
		
		Collections.sort(m_cats, cmp);
		m_adapter.notifyDataSetInvalidated();
		
	}
	
	private class FeedCategoryListAdapter extends ArrayAdapter<FeedCategory> {
		private ArrayList<FeedCategory> items;

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_SELECTED = 1;
		
		public static final int VIEW_COUNT = VIEW_SELECTED+1;

		public FeedCategoryListAdapter(Context context, int textViewResourceId, ArrayList<FeedCategory> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}

		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			FeedCategory cat = items.get(position);
			
			if (cat.id == m_selectedCatId) {
				return VIEW_SELECTED;
			} else {
				return VIEW_NORMAL;				
			}			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;

			FeedCategory cat = items.get(position);

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
				tt.setText(cat.title);
			}

			TextView tu = (TextView) v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(cat.unread));
				tu.setVisibility((cat.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}
			
			ImageView icon = (ImageView)v.findViewById(R.id.icon);
			
			if (icon != null) {
				icon.setImageResource(cat.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
			}

			return v;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		sortCats();
		
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			FeedCategory cat = (FeedCategory)list.getItemAtPosition(position);
			m_catSelectedListener.onCatSelected(cat);
			m_selectedCatId = cat.id;
			m_adapter.notifyDataSetChanged();
		}
	}
}
