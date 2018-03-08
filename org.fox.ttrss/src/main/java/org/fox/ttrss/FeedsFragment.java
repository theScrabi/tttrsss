package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
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
import android.view.View.OnClickListener;
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
import org.fox.ttrss.types.FeedList;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import icepick.State;

public class FeedsFragment extends BaseFeedlistFragment implements OnItemClickListener, OnSharedPreferenceChangeListener,
		LoaderManager.LoaderCallbacks<JsonElement> {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private FeedList m_feeds = new FeedList();
	private MasterActivity m_activity;
	@State Feed m_selectedFeed;
	@State FeedCategory m_activeCategory;
	private SwipeRefreshLayout m_swipeLayout;
    @State boolean m_enableParentBtn = false;
    private ListView m_list;

    public void initialize(FeedCategory cat, boolean enableParentBtn) {
        m_activeCategory = cat;
        m_enableParentBtn = enableParentBtn;
	}

	@Override
	public Loader<JsonElement> onCreateLoader(int id, Bundle args) {
		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);

		final int catId = (m_activeCategory != null) ? m_activeCategory.id : -4;

		final String sessionId = m_activity.getSessionId();
		final boolean unreadOnly = m_activity.getUnreadOnly() && (m_activeCategory == null || m_activeCategory.id != -1);

		HashMap<String,String> params = new HashMap<String,String>() {
			{
				put("op", "getFeeds");
				put("sid", sessionId);
				put("include_nested", "true");
				put("cat_id", String.valueOf(catId));
				if (unreadOnly) {
					put("unread_only", String.valueOf(unreadOnly));
				}
			}
		};

		return new FeedsLoader(getActivity().getApplicationContext(), params, catId);
	}

	@Override
	public void onLoadFinished(Loader<JsonElement> loader, JsonElement result) {
		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {

					Type listType = new TypeToken<List<Feed>>() {}.getType();
					final List<Feed> feeds = new Gson().fromJson(content, listType);

					m_feeds.clear();

					int catUnread = 0;

					int catId = ((FeedsLoader) loader).getCatId();

					for (Feed f : feeds)
						if (f.id > -10 || catId != -4) { // skip labels for flat feedlist for now
							if (m_activeCategory != null || f.id >= 0) {
								m_feeds.add(f);
								catUnread += f.unread;
							}
						}

					sortFeeds();

					if (m_activeCategory == null) {
						Feed feed = new Feed(-1, "Special", true);
						feed.unread = catUnread;

						m_feeds.add(0, feed);

					}

					if (m_enableParentBtn && m_activeCategory != null && m_activeCategory.id >= 0 && m_feeds.size() > 0) {
						Feed feed = new Feed(m_activeCategory.id, m_activeCategory.title, true);
						feed.unread = catUnread;
						feed.always_display_as_feed = true;
						feed.display_title = getString(R.string.feed_all_articles);

						m_feeds.add(0, feed);
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

			//m_activity.setLoadingStatus(getErrorMessage(), false);
		}
	}

	@Override
	public void onLoaderReset(Loader<JsonElement> loader) {
		/*m_feeds.clear();
		m_adapter.notifyDataSetChanged();*/
	}

	@SuppressLint("DefaultLocale")
	class FeedUnreadComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.unread != b.unread)
					return b.unread - a.unread;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			}
		
	}
	

	@SuppressLint("DefaultLocale")
	class FeedTitleComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.is_cat && b.is_cat)
				return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else if (a.is_cat && !b.is_cat)
				return -1;
			else if (!a.is_cat && b.is_cat)
				return 1;
			else if (a.id >= 0 && b.id >= 0)
				return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;			
		}
		
	}

	@SuppressLint("DefaultLocale")
	class FeedOrderComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			Log.d(TAG, "A:" + a.title + " " + a.is_cat + " " + a.order_id);
			Log.d(TAG, "B:" + b.title + " " + b.is_cat + " " + b.order_id);

			if (a.id >= 0 && b.id >= 0)
				if (a.is_cat && b.is_cat)
					if (a.order_id != 0 && b.order_id != 0)
						return a.order_id - b.order_id;
					else
						return a.title.toUpperCase().compareTo(b.title.toUpperCase());
				else if (a.is_cat && !b.is_cat)
					return -1;
				else if (!a.is_cat && b.is_cat) 
					return 1;
				else if (a.order_id != 0 && b.order_id != 0)
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
				Feed feed = getFeedAtPosition(info.position);
				if (feed != null) {
					m_activity.onFeedSelected(feed);
				}
			}
			return true;
		case R.id.browse_feeds:
			if (true) {
				Feed feed = getFeedAtPosition(info.position);
				if (feed != null) {
					m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), false);
				}
			}
			return true;
		case R.id.unsubscribe_feed:
			if (true) {
				final Feed feed = getFeedAtPosition(info.position);

				AlertDialog.Builder builder = new AlertDialog.Builder(
						m_activity)
						.setMessage(getString(R.string.unsubscribe_from_prompt, feed.title))
						.setPositiveButton(R.string.unsubscribe,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {

										m_activity.unsubscribeFeed(feed);											
										
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
			}			
			
			return true;
		case R.id.create_shortcut:
			if (true) {
				Feed feed = getFeedAtPosition(info.position);
				if (feed != null) {
					m_activity.createFeedShortcut(feed);
				}
			}
			return true;
		case R.id.catchup_feed:
			if (true) {
				final Feed feed = getFeedAtPosition(info.position);
				
				if (feed != null) {
					if (m_prefs.getBoolean("confirm_headlines_catchup", true)) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								m_activity)
								.setMessage(getString(R.string.context_confirm_catchup, feed.title))
								.setPositiveButton(R.string.catchup,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {
	
												m_activity.catchupFeed(feed);											
												
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
						m_activity.catchupFeed(feed);
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
		
		getActivity().getMenuInflater().inflate(R.menu.context_feed, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;

        Feed feed = (Feed) m_list.getItemAtPosition(info.position);
		
		menu.setHeaderTitle(feed.display_title != null ? feed.display_title : feed.title);

		if (!feed.is_cat) {
			menu.findItem(R.id.browse_feeds).setVisible(false);
		}

		if (feed.id <= 0) {
			menu.findItem(R.id.unsubscribe_feed).setVisible(false);
		}

		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		View view = inflater.inflate(R.layout.fragment_feeds, container, false);
		
		m_swipeLayout = view.findViewById(R.id.feeds_swipe_container);
		
	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh();
			}
		});

		m_list = view.findViewById(R.id.feeds);

		initDrawerHeader(inflater, view, m_list, m_activity, m_prefs, !m_enableParentBtn);

		if (m_enableParentBtn) {
			View layout = inflater.inflate(R.layout.feeds_goback, m_list, false);

			layout.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					m_activity.getSupportFragmentManager().popBackStack();
				}
			});

			m_list.addHeaderView(layout, null, false);
		}

		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, m_feeds);
		m_list.setAdapter(m_adapter);
		m_list.setOnItemClickListener(this);

		registerForContextMenu(m_list);

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
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		
		m_activity = (MasterActivity)activity;
				
	}

	@Override
	public void onResume() {
		super.onResume();

		getLoaderManager().initLoader(0, null, this).forceLoad();
		
		m_activity.invalidateOptionsMenu();
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;

		if (list != null) {
            Feed feed = (Feed)list.getItemAtPosition(position);

			if (feed != null) {
				if (feed.is_cat) {
					if (feed.always_display_as_feed) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), true);
					} else if (feed.id < 0) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), false);
					} else {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread));
					}
				} else {
					m_activity.onFeedSelected(feed);
				}
			}
			
    		//m_selectedFeed = feed;
			//m_adapter.notifyDataSetChanged();
		}
	}

	@SuppressWarnings({ "serial" })
	public void refresh() {
		if (!isAdded()) return;

		if (m_swipeLayout != null) {
            m_swipeLayout.setRefreshing(true);
        }

		getLoaderManager().restartLoader(0, null, this).forceLoad();
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

        @Override
        public boolean isEmpty() {
            return !m_enableParentBtn && super.isEmpty();
        }

        @Override
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Feed feed = items.get(position);

            if (/*!m_activity.isSmallScreen() &&*/ m_selectedFeed != null && feed.id == m_selectedFeed.id) {
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

			ImageView icon = v.findViewById(R.id.icon);

			if (icon != null) {
				TypedValue tv = new TypedValue();

				if (feed.id == 0 && !feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_archive, tv, true);
					icon.setImageResource(tv.resourceId);
				} else if (feed.id == -1 && !feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_star, tv, true);
					icon.setImageResource(tv.resourceId);
				} else if (feed.id == -2 && !feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_checkbox_marked, tv, true);
					icon.setImageResource(tv.resourceId);
				} else if (feed.id == -3 && !feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_coffee, tv, true);
					icon.setImageResource(tv.resourceId);
				} else if (feed.id == -4 && !feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_folder_outline, tv, true);
					icon.setImageResource(tv.resourceId);
				} else if (feed.is_cat) {
					m_activity.getTheme().resolveAttribute(R.attr.ic_folder_outline, tv, true);
					icon.setImageResource(tv.resourceId);
				} else {
					m_activity.getTheme().resolveAttribute(R.attr.ic_rss_box, tv, true);
					icon.setImageResource(tv.resourceId);
				}

			}

			TextView tt = v.findViewById(R.id.title);

			if (tt != null) {
                tt.setText(feed.display_title != null ? feed.display_title : feed.title);

                if (feed.always_display_as_feed || (!feed.is_cat && feed.id == -4)) {
                    tt.setTypeface(null, Typeface.BOLD);
                } else {
                    tt.setTypeface(null, Typeface.NORMAL);
                }

			}

			TextView tu = v.findViewById(R.id.unread_counter);

			if (tu != null) {
				tu.setText(String.valueOf(feed.unread));
				tu.setVisibility((feed.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}

			/*ImageButton ib = (ImageButton) v.findViewById(R.id.feed_menu_button);

			if (ib != null) {
				ib.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						getActivity().openContextMenu(v);
					}
				});
			}*/

			return v;
		}
	}

	public void sortFeeds() {
		Comparator<Feed> cmp;
		
		if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
			cmp = new FeedUnreadComparator();
		} else {
			if (m_activity.getApiLevel() >= 3) {
				cmp = new FeedOrderComparator();				
			} else {
				cmp = new FeedTitleComparator();
			}
		}
		
		try {
			Collections.sort(m_feeds, cmp);
		} catch (IllegalArgumentException e) {
			// sort order got changed in prefs or something
			e.printStackTrace();
		}

		try {
			m_adapter.notifyDataSetChanged();
		} catch (NullPointerException e) {
			// adapter missing
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		sortFeeds();
		//m_enableFeedIcons = m_prefs.getBoolean("download_feed_icons", false);
		
	}

	public Feed getFeedAtPosition(int position) {
		try {
            return (Feed) m_list.getItemAtPosition(position);
        } catch (NullPointerException e) {
            return null;
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	public void setSelectedfeed(Feed feed) {
        m_selectedFeed = feed;

        if (m_adapter != null) {
            m_adapter.notifyDataSetChanged();
        }
    }
}
