package org.fox.ttrss;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.FeedList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Base64;
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

public class FeedsFragment extends Fragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private FeedList m_feeds = new FeedList();
	private FeedsActivity m_activity;
	private Feed m_selectedFeed;
	private FeedCategory m_activeCategory;
	private static final String ICON_PATH = "/icons/";
	private boolean m_enableFeedIcons;
	private boolean m_feedIconsChecked = false;
	
	public void initialize(FeedCategory cat) {
		m_activeCategory = cat;
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
			if (a.id >= 0 && b.id >= 0)
				if (a.is_cat && b.is_cat)
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
		case R.id.browse_articles:
			if (true) {
				Feed feed = getFeedAtPosition(info.position);
				if (feed != null) {
					m_activity.openFeedArticles(feed);
				}
			}
			return true;		
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
		case R.id.catchup_feed:
			if (true) {
				Feed feed = getFeedAtPosition(info.position);
				if (feed != null) {
					m_activity.catchupFeed(feed);
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
		
		getActivity().getMenuInflater().inflate(R.menu.feed_menu, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Feed feed = m_adapter.getItem(info.position);
		
		if (feed != null) 
			menu.setHeaderTitle(feed.title);

		if (!m_activity.isSmallScreen()) {
			menu.findItem(R.id.browse_articles).setVisible(false);
		}
		
		if (!feed.is_cat) {
			menu.findItem(R.id.browse_feeds).setVisible(false);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_selectedFeed = savedInstanceState.getParcelable("selectedFeed");
			m_feeds = savedInstanceState.getParcelable("feeds");
			m_feedIconsChecked = savedInstanceState.getBoolean("feedIconsChecked");
			m_activeCategory = savedInstanceState.getParcelable("activeCat");
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);
		
		ListView list = (ListView)view.findViewById(R.id.feeds);		
		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, (ArrayList<Feed>)m_feeds);
		list.setAdapter(m_adapter);
		//list.setEmptyView(view.findViewById(R.id.no_feeds));
		list.setOnItemClickListener(this);
		
		registerForContextMenu(list);
		
		m_enableFeedIcons = m_prefs.getBoolean("download_feed_icons", false);
		
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
		
		m_activity = (FeedsActivity)activity;
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

		out.putParcelable("selectedFeed", m_selectedFeed);
		out.putParcelable("feeds", m_feeds);
		out.putBoolean("feedIconsChecked", m_feedIconsChecked);
		out.putParcelable("activeCat", m_activeCategory);	
	}
	
	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Feed feed = (Feed)list.getItemAtPosition(position);

			if (feed.is_cat) {
				if (m_activity.isSmallScreen() && "ARTICLES".equals(m_prefs.getString("default_view_mode", "HEADLINES")) &&
						m_prefs.getBoolean("browse_cats_like_feeds", false)) {
					
					m_activity.openFeedArticles(feed);
					
				} else {
					m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread));
				}
			} else {
				if ("ARTICLES".equals(m_prefs.getString("default_view_mode", "HEADLINES"))) {
					m_activity.openFeedArticles(feed);
				} else {
					m_activity.onFeedSelected(feed);
				}
			}
			
			if (!m_activity.isSmallScreen())
				m_selectedFeed = feed;
			
			m_adapter.notifyDataSetChanged();
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean background) {
		//FeedCategory cat = m_onlineServices.getActiveCategory();
		m_activity.setProgressBarVisibility(true);
		
		final int catId = (m_activeCategory != null) ? m_activeCategory.id : -4;
		
		final String sessionId = m_activity.getSessionId();
		final boolean unreadOnly = m_activity.getUnreadOnly();

		FeedsRequest req = new FeedsRequest(getActivity().getApplicationContext(), catId);
		
		if (sessionId != null) {
			m_activity.setLoadingStatus(R.string.blank, true);
			
			HashMap<String,String> map = new HashMap<String,String>() {
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

			req.execute(map);
		
		}
	}
	
	/* private void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}
		}
		
		if (getActivity() != null)
			getActivity().setProgressBarIndeterminateVisibility(showProgress);
	} */
	
	@SuppressWarnings({ "unchecked", "serial" })
	public void getFeedIcons() {
		
		ApiRequest req = new ApiRequest(getActivity().getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				if (isDetached()) return;
				
				if (result != null) {

					try {
						JsonElement iconsUrl = result.getAsJsonObject().get("icons_dir");

						if (iconsUrl != null) {
							String iconsStr = iconsUrl.getAsString();
							String baseUrl = "";
							
							if (!iconsStr.contains("://")) {
								baseUrl = m_prefs.getString("ttrss_url", "").trim() + "/" + iconsStr;									
							} else {
								baseUrl = iconsStr;
							}

							GetIconsTask git = new GetIconsTask(baseUrl);
							
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
								git.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, m_feeds);
							else
								git.execute(m_feeds);
							
							m_feedIconsChecked = true;
						}
					} catch (Exception e) {
						Log.d(TAG, "Error receiving icons configuration");
						e.printStackTrace();
					}
					
				}
			}
		};
		
		final String sessionId = m_activity.getSessionId();
		
		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("sid", sessionId);
				put("op", "getConfig");
			}			 
		};

		req.execute(map);
	}
	
	private class FeedsRequest extends ApiRequest {
		private int m_catId;
			
		public FeedsRequest(Context context, int catId) {
			super(context);
			m_catId = catId;
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			m_activity.setProgress(Math.round((((float)progress[0] / (float)progress[1]) * 10000)));
		}

		@Override
		protected void onPostExecute(JsonElement result) {
			if (isDetached()) return;

			if (getView() != null) {
				ListView list = (ListView)getView().findViewById(R.id.feeds);
			
				if (list != null) {
					list.setEmptyView(getView().findViewById(R.id.no_feeds));
				}
			}
			
			m_activity.setProgressBarVisibility(false);

			if (result != null) {
				try {			
					JsonArray content = result.getAsJsonArray();
					if (content != null) {

						Type listType = new TypeToken<List<Feed>>() {}.getType();
						final List<Feed> feeds = new Gson().fromJson(content, listType);
						
						m_feeds.clear();
						
						for (Feed f : feeds)
							if (f.id > -10 || m_catId != -4) // skip labels for flat feedlist for now
								m_feeds.add(f);
						
						sortFeeds();
						
						/*if (m_feeds.size() == 0)
							setLoadingStatus(R.string.no_feeds_to_display, false);
						else */
						
						m_activity.setLoadingStatus(R.string.blank, false);
						//m_adapter.notifyDataSetChanged(); (done by sortFeeds)
						
						if (m_enableFeedIcons && !m_feedIconsChecked && 
								Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) 
							getFeedIcons();

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
			
			if (!m_activity.isSmallScreen() && m_selectedFeed != null && feed.id == m_selectedFeed.id) {
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
			
			ImageView icon = (ImageView)v.findViewById(R.id.icon);
			
			if (icon != null) {
				
				if (m_enableFeedIcons) {
					
					try {
						File storage = m_activity.getExternalCacheDir();
						
						File iconFile = new  File(storage.getAbsolutePath() + ICON_PATH + feed.id + ".ico");
						if (iconFile.exists()) {
							Bitmap bmpOrig = BitmapFactory.decodeFile(iconFile.getAbsolutePath());		
							if (bmpOrig != null) {
								icon.setImageBitmap(bmpOrig);
							}
						} else {
							icon.setImageResource(feed.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
						}
					} catch (NullPointerException e) {
						icon.setImageResource(feed.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
					}
					
				} else {
					icon.setImageResource(feed.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
				}
				
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
	
	public class GetIconsTask extends AsyncTask<FeedList, Integer, Integer> {

		private String m_baseUrl;
		
		public GetIconsTask(String baseUrl) {
			m_baseUrl = baseUrl.trim();
		}

		@Override
		protected Integer doInBackground(FeedList... params) {

			FeedList localList = new FeedList();			
			
			try {
				localList.addAll(params[0]);

				File storage = m_activity.getExternalCacheDir();
				final File iconPath = new File(storage.getAbsolutePath() + ICON_PATH);
				if (!iconPath.exists()) iconPath.mkdirs();
			
				if (iconPath.exists()) {
					for (Feed feed : localList)	 {
						if (feed.id > 0 && feed.has_icon && !feed.is_cat) {
							File outputFile = new File(iconPath.getAbsolutePath() + "/" + feed.id + ".ico");
							String fetchUrl = m_baseUrl + "/" + feed.id + ".ico";
							
							if (!outputFile.exists()) {
								downloadFile(fetchUrl, outputFile.getAbsolutePath());
								Thread.sleep(2000);
							}											
						}
					}						
				}
			} catch (Exception e) {
				Log.d(TAG, "Error while downloading feed icons");
				e.printStackTrace();
			}
			return null;
		}
		
		protected void downloadFile(String fetchUrl, String outputFile) {
			AndroidHttpClient client = AndroidHttpClient.newInstance("Tiny Tiny RSS");
			
			/* ApiRequest.disableConnectionReuseIfNecessary(); */
			
			/* ApiRequest.trustAllHosts(m_prefs.getBoolean("ssl_trust_any", false),
					m_prefs.getBoolean("ssl_trust_any_host", false)); */				

			try {
				URL url = new URL(fetchUrl);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				
				conn.setConnectTimeout(1000);
				conn.setReadTimeout(5000);
				
				Log.d(TAG, "[downloadFile] " + url);

				String httpLogin = m_prefs.getString("http_login", "");
				String httpPassword = m_prefs.getString("http_password", "");
				
				if (httpLogin.length() > 0) {
					conn.setRequestProperty("Authorization", "Basic " + 
						Base64.encodeToString((httpLogin + ":" + httpPassword).getBytes("UTF-8"), Base64.NO_WRAP)); 				
				}

				InputStream content = conn.getInputStream();

				BufferedInputStream is = new BufferedInputStream(content, 1024);
				FileOutputStream fos = new FileOutputStream(outputFile);
				
				byte[] buffer = new byte[1024];
				int len = 0;
				while ((len = is.read(buffer)) != -1) {
				    fos.write(buffer, 0, len);
				}
				
				fos.close();
				is.close();

			} catch (Exception e) {
				e.printStackTrace();
			}

			client.close();
		}
		
		protected void onPostExecute(Integer result) {
			if (isDetached()) return;
			
			m_adapter.notifyDataSetChanged();
		}
		
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		sortFeeds();
		m_enableFeedIcons = m_prefs.getBoolean("download_feed_icons", false);
		
	}

	public Feed getFeedAtPosition(int position) {
		try {
			return m_adapter.getItem(position);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}
	
	public Feed getSelectedFeed() {
		return m_selectedFeed;
	}	
	
	public void setSelectedFeed(Feed feed) {
		m_selectedFeed = feed;
		m_adapter.notifyDataSetChanged();
	}

}
