package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.FeedCategoryList;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import icepick.State;

public class FeedCategoriesFragment extends BaseFeedlistFragment implements OnItemClickListener, OnSharedPreferenceChangeListener,
		LoaderManager.LoaderCallbacks<JsonElement> {
	private final String TAG = this.getClass().getSimpleName();
	private FeedCategoryListAdapter m_adapter;
	private FeedCategoryList m_cats = new FeedCategoryList();
	@State FeedCategory m_selectedCat;
	private MasterActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
    private ListView m_list;
	protected SharedPreferences m_prefs;

	@Override
	public Loader<JsonElement> onCreateLoader(int id, Bundle args) {
		final String sessionId = m_activity.getSessionId();
		final boolean unreadOnly = m_activity.getUnreadOnly();

		@SuppressWarnings("serial")
		HashMap<String, String> params = new HashMap<String, String>() {
			{
				put("op", "getCategories");
				put("sid", sessionId);
				put("enable_nested", "true");
				if (unreadOnly) {
					put("unread_only", String.valueOf(unreadOnly));
				}
			}
		};

		return new ApiLoader(getContext(), params);
	}

	@Override
	public void onLoadFinished(Loader<JsonElement> loader, JsonElement result) {
		Log.d(TAG, "onLoadFinished: " + loader + " " + result);

		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {
					Type listType = new TypeToken<List<FeedCategory>>() {}.getType();
					final List<FeedCategory> cats = new Gson().fromJson(content, listType);

					m_cats.clear();

					int apiLevel = m_activity.getApiLevel();

					boolean specialCatFound = false;

					// virtual cats implemented in getCategories since api level 1
					if (apiLevel == 0) {
						m_cats.add(new FeedCategory(-1, "Special", 0));
						m_cats.add(new FeedCategory(-2, "Labels", 0));
						m_cats.add(new FeedCategory(0, "Uncategorized", 0));

						specialCatFound = true;
					}

					for (FeedCategory c : cats) {
						if (c.id == -1) {
							specialCatFound = true;
						}
					}

					m_cats.addAll(cats);

					sortCats();

					if (!specialCatFound) {
						m_cats.add(0, new FeedCategory(-1, "Special", 0));
					}

					m_adapter.notifyDataSetChanged();

					return;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		ApiLoader al = (ApiLoader) loader;

		if (al.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
			m_activity.login(true);
		} else {
			if (al.getLastErrorMessage() != null) {
				m_activity.toast(getString(al.getErrorMessage()) + "\n" + al.getLastErrorMessage());
			} else {
				m_activity.toast(al.getErrorMessage());
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

	@Override
	public void onLoaderReset(Loader<JsonElement> loader) {
		Log.d(TAG, "onLoaderReset: " + loader);

		/*m_cats.clear();
		m_adapter.notifyDataSetChanged();*/
	}

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
		case R.id.create_shortcut:
			if (true) {
				FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
					m_activity.createCategoryShortcut(cat);
					//cf.setSelectedCategory(cat);
				}
			}
			return true;
		case R.id.catchup_category:
			if (true) {
				final FeedCategory cat = getCategoryAtPosition(info.position);
				if (cat != null) {
										
					if (m_prefs.getBoolean("confirm_headlines_catchup", true)) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								m_activity)
								.setMessage(getString(R.string.context_confirm_catchup, cat.title))
								.setPositiveButton(R.string.catchup,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {
	
												m_activity.catchupFeed(new Feed(cat.id, cat.title, true));											
												
											}
										})
								.setNegativeButton(R.string.dialog_cancel,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {
		
											}
										});
		
						AlertDialog dlg = builder.create();
						dlg.show();						
					} else {
						m_activity.catchupFeed(new Feed(cat.id, cat.title, true));
					}

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
		
		m_activity.getMenuInflater().inflate(R.menu.context_category, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		FeedCategory cat = (FeedCategory) m_list.getItemAtPosition(info.position);
		
		if (cat != null) 
			menu.setHeaderTitle(cat.title);

		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	public FeedCategory getCategoryAtPosition(int position) {
        try {
		    return (FeedCategory) m_list.getItemAtPosition(position);
        } catch (NullPointerException e) {
            return null;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {  

		View view = inflater.inflate(R.layout.fragment_cats, container, false);
		
		m_swipeLayout = view.findViewById(R.id.feeds_swipe_container);
		
	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh();
			}
		});

		m_list = view.findViewById(R.id.feeds);
		m_adapter = new FeedCategoryListAdapter(getActivity(), R.layout.feeds_row, m_cats);

		initDrawerHeader(inflater, view, m_list, m_activity, m_prefs, true);

        m_list.setAdapter(m_adapter);
        m_list.setOnItemClickListener(this);
        registerForContextMenu(m_list);

		return view; 
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		

		m_activity = (MasterActivity)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);

	}
	
	@Override
	public void onResume() {
		super.onResume();

		getLoaderManager().initLoader(0, null, this).forceLoad();

		m_activity.invalidateOptionsMenu();
	}
	
	public void refresh() {
		if (!isAdded()) return;

        if (m_swipeLayout != null) {
			m_swipeLayout.setRefreshing(true);
		}

		getLoaderManager().restartLoader(0, null, this).forceLoad();
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
			
			if (/*!m_activity.isSmallScreen() &&*/ m_selectedCat != null && cat.id == m_selectedCat.id) {
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

			ImageView icon = v.findViewById(R.id.icon);

			if (icon != null) {
				TypedValue tv = new TypedValue();

				m_activity.getTheme().resolveAttribute(R.attr.ic_folder_outline, tv, true);
				icon.setImageResource(tv.resourceId);

			}

			TextView tt = v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(cat.title);
			}

			TextView tu = v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(cat.unread));
				tu.setVisibility((cat.unread > 0) ? View.VISIBLE : View.INVISIBLE);
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

			m_selectedCat = null;
			m_adapter.notifyDataSetChanged();

			if (cat != null) {
				if (cat.id < 0) {
					m_activity.onCatSelected(cat, false);
				} else {
					m_activity.onCatSelected(cat);
				}

			}
		}
	}

	public void setSelectedCategory(FeedCategory cat) {	
		m_selectedCat = cat;
		
		if (m_adapter != null) {
			m_adapter.notifyDataSetChanged();
		}
	}


}
