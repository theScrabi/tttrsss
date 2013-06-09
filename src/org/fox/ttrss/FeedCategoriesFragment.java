package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.FeedCategoryList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
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
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class FeedCategoriesFragment extends Fragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedCategoryListAdapter m_adapter;
	private FeedCategoryList m_cats = new FeedCategoryList();
	private FeedCategory m_selectedCat;
	private FeedsActivity m_activity;

	@SuppressLint("DefaultLocale")
	class CatUnreadComparator implements Comparator<FeedCategory> {
		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.unread != b.unread)
					return b.unread - a.unread;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			}
	}
	

	@SuppressLint("DefaultLocale")
	class CatTitleComparator implements Comparator<FeedCategory> {

		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.id >= 0 && b.id >= 0)
				return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;
		}
		
	}

	@SuppressLint("DefaultLocale")
	class CatOrderComparator implements Comparator<FeedCategory> {

		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.id >= 0 && b.id >= 0)
				if (a.order_id != 0 && b.order_id != 0)
					return a.order_id - b.order_id;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;
		}
		
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		
		switch (item.getItemId()) {
		case R.id.browse_articles:
			if (true) {
				FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
					m_activity.openFeedArticles(new Feed(cat.id, cat.title, true));
					//setSelectedCategory(cat);
				}
			}
			return true;		
		case R.id.browse_headlines:
			if (true) {
				FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
					m_activity.onCatSelected(cat, true);
					//setSelectedCategory(cat);
				}
			}
			return true;
		case R.id.browse_feeds:
			if (true) {
				FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
					m_activity.onCatSelected(cat, false);
					//cf.setSelectedCategory(cat);
				}
			}
			return true;
		case R.id.catchup_category:
			if (true) {
				FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
					m_activity.catchupFeed(new Feed(cat.id, cat.title, true));
				}
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
		
		m_activity.getMenuInflater().inflate(R.menu.category_menu, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		FeedCategory cat = m_adapter.getItem(info.position);
		
		if (cat != null) 
			menu.setHeaderTitle(cat.title);
		
		if (!m_activity.isSmallScreen()) {
			menu.findItem(R.id.browse_articles).setVisible(false);
		}

		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	public FeedCategory getCategoryAtPosition(int position) {
		return m_adapter.getItem(position);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {  
		if (savedInstanceState != null) {
			m_selectedCat = savedInstanceState.getParcelable("selectedCat");
			m_cats = savedInstanceState.getParcelable("cats");
		}	
		
		View view = inflater.inflate(R.layout.cats_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);		
		m_adapter = new FeedCategoryListAdapter(getActivity(), R.layout.feeds_row, (ArrayList<FeedCategory>)m_cats);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		registerForContextMenu(list);
		
		return view; 
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		

		m_activity = (FeedsActivity)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);

	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		refresh(false);
		
		m_activity.initMenu();
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putParcelable("selectedCat", m_selectedCat);
		out.putParcelable("cats", m_cats);
	}

	/* private void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}
		}
	
		m_activity.setProgressBarIndeterminateVisibility(showProgress);
	} */
	
	@SuppressWarnings("unchecked")
	public void refresh(boolean background) {
		CatsRequest req = new CatsRequest(getActivity().getApplicationContext());
		
		final String sessionId = m_activity.getSessionId();
		final boolean unreadOnly = m_activity.getUnreadOnly();
		
		if (sessionId != null) {
			m_activity.setLoadingStatus(R.string.blank, true);
			m_activity.setProgressBarVisibility(true);
			
			@SuppressWarnings("serial")
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getCategories");
					put("sid", sessionId);
					put("enable_nested", "true");
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
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			m_activity.setProgress(Math.round((((float)progress[0] / (float)progress[1]) * 10000)));
		}
		
		@Override
		protected void onPostExecute(JsonElement result) {
			if (isDetached()) return;
			
			m_activity.setProgressBarVisibility(false);

			if (getView() != null) {
				ListView list = (ListView)getView().findViewById(R.id.feeds);
			
				if (list != null) {
					list.setEmptyView(getView().findViewById(R.id.no_feeds));
				}
			}
			
			if (result != null) {
				try {			
					JsonArray content = result.getAsJsonArray();
					if (content != null) {
						Type listType = new TypeToken<List<FeedCategory>>() {}.getType();
						final List<FeedCategory> cats = new Gson().fromJson(content, listType);
						
						m_cats.clear();
						
						int apiLevel = m_activity.getApiLevel();
						
						// virtual cats implemented in getCategories since api level 1
						if (apiLevel == 0) {
							m_cats.add(new FeedCategory(-1, "Special", 0));
							m_cats.add(new FeedCategory(-2, "Labels", 0));
							m_cats.add(new FeedCategory(0, "Uncategorized", 0));
						}
						
						for (FeedCategory c : cats)
							m_cats.add(c);
						
						sortCats();
						
						/* if (m_cats.size() == 0)
							setLoadingStatus(R.string.no_feeds_to_display, false);
						else */
						
						//m_adapter.notifyDataSetChanged(); (done by sortCats)
						m_activity.setLoadingStatus(R.string.blank, false);
						
						return;
					}
							
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}

			if (m_lastError == ApiError.LOGIN_FAILED) {
				m_activity.login(true);
			} else {
				m_activity.setLoadingStatus(getErrorMessage(), false);
			}
		}

	}

	public void sortCats() {
		Comparator<FeedCategory> cmp;
		
		if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
			cmp = new CatUnreadComparator();
		} else {
			if (m_activity.getApiLevel() >= 3) {
				cmp = new CatOrderComparator();
			} else {
				cmp = new CatTitleComparator();
			}
		}
		
		try {
			Collections.sort(m_cats, cmp);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		try {
			m_adapter.notifyDataSetChanged();
		} catch (NullPointerException e) {
			// adapter missing
		}
		
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
			
			if (!m_activity.isSmallScreen() && m_selectedCat != null && cat.id == m_selectedCat.id) {
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		sortCats();
		
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {
			FeedCategory cat = (FeedCategory)list.getItemAtPosition(position);
			
			if (cat.id < 0) {
				m_activity.onCatSelected(cat, false);				
			} else {
				if ("ARTICLES".equals(m_prefs.getString("default_view_mode", "HEADLINES")) &&
						m_prefs.getBoolean("browse_cats_like_feeds", false)) {
					
					m_activity.openFeedArticles(new Feed(cat.id, cat.title, true));
					
				} else {			
					m_activity.onCatSelected(cat);
				}
			}
			
			//if (!m_activity.isSmallScreen())
			//	m_selectedCat = cat;
			
			m_adapter.notifyDataSetChanged();
		}
	}

	public void setSelectedCategory(FeedCategory cat) {
		m_selectedCat = cat;
		m_adapter.notifyDataSetChanged();
	}
	
	public FeedCategory getSelectedCategory() {
		return m_selectedCat;
	}	
}
