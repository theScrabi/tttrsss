package org.fox.ttrss;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.FeedList;
import org.fox.ttrss.util.EasySSLSocketFactory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
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

public class FeedsFragment extends Fragment implements OnItemClickListener, OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private FeedListAdapter m_adapter;
	private FeedList m_feeds = new FeedList();
	private OnlineServices m_onlineServices;
	private Feed m_selectedFeed;
	private FeedCategory m_activeCategory;
	private static final String ICON_PATH = "/data/org.fox.ttrss/icons/";
	private boolean m_enableFeedIcons;
	private boolean m_feedIconsChecked = false;
	
	public FeedsFragment() {
		
		
	}
	
	public FeedsFragment(FeedCategory cat) {
		m_activeCategory = cat;
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

	class FeedOrderComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.id >= 0 && b.id >= 0)
				if (a.order_id != 0 && b.order_id != 0)
					return a.order_id - b.order_id;
				else
					return a.title.compareTo(b.title);
			else
				return a.id - b.id;
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
		
		if (m_feeds == null || m_feeds.size() == 0)
			refresh(false);
		else
			getActivity().setProgressBarIndeterminateVisibility(false);
		
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
		
		m_onlineServices = (OnlineServices)activity;
		
		//m_selectedFeed = m_onlineServices.getActiveFeed();
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
			m_onlineServices.onFeedSelected(feed);
			
			if (!m_onlineServices.isSmallScreen())
				m_selectedFeed = feed;
			
			m_adapter.notifyDataSetChanged();
		}
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void refresh(boolean background) {
		//FeedCategory cat = m_onlineServices.getActiveCategory();

		final int catId = (m_activeCategory != null) ? m_activeCategory.id : -4;
		
		final String sessionId = m_onlineServices.getSessionId();
		final boolean unreadOnly = m_onlineServices.getUnreadOnly();

		FeedsRequest req = new FeedsRequest(getActivity().getApplicationContext(), catId);
		
		if (sessionId != null) {
			
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setLoadingStatus(R.string.blank, true);
				}
			});
			
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "getFeeds");
					put("sid", sessionId);
					put("cat_id", String.valueOf(catId));
					if (unreadOnly) {
						put("unread_only", String.valueOf(unreadOnly));
					}
				}			 
			};

			req.execute(map);
		
		}
	}
	
	private void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}
		}
		
		if (getActivity() != null)
			getActivity().setProgressBarIndeterminateVisibility(showProgress);
	}
	
	@SuppressWarnings({ "unchecked", "serial" })
	public void getFeedIcons() {
		
		ApiRequest req = new ApiRequest(getActivity().getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				if (result != null) {

					try {
						JsonElement iconsUrl = result.getAsJsonObject().get("icons_dir");

						if (iconsUrl != null) {
							String iconsStr = iconsUrl.getAsString();
							String baseUrl = "";
							
							if (!iconsStr.contains("://")) {
								baseUrl = m_prefs.getString("ttrss_url", "") + "/" + iconsStr;									
							} else {
								baseUrl = iconsStr;
							}

							GetIconsTask git = new GetIconsTask(baseUrl);
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
		
		final String sessionId = m_onlineServices.getSessionId();
		
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
		
		protected void onPostExecute(JsonElement result) {
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
						
						if (m_feeds.size() == 0)
							setLoadingStatus(R.string.no_feeds_to_display, false);
						else
							setLoadingStatus(R.string.blank, false);

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
				m_onlineServices.restart();
			} else {
				setLoadingStatus(getErrorMessage(), false);
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
			
			if (!m_onlineServices.isSmallScreen() && m_selectedFeed != null && feed.id == m_selectedFeed.id) {
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
					
					File storage = Environment.getExternalStorageDirectory();
					
					File iconFile = new  File(storage.getAbsolutePath() + ICON_PATH + feed.id + ".ico");
					if (iconFile.exists()) {
						Bitmap bmpOrig = BitmapFactory.decodeFile(iconFile.getAbsolutePath());		
						if (bmpOrig != null) {
							icon.setImageBitmap(bmpOrig);
						}
					} else {
						icon.setImageResource(feed.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
					}
					
				} else {
					icon.setImageResource(feed.unread > 0 ? R.drawable.ic_rss : R.drawable.ic_rss_bw);
				}
				
			}

			return v;
		}
	}

	public void sortFeeds() {
		Comparator<Feed> cmp;
		
		if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
			cmp = new FeedUnreadComparator();
		} else {
			if (m_onlineServices.getApiLevel() >= 3) {
				cmp = new FeedOrderComparator();				
			} else {
				cmp = new FeedTitleComparator();
			}
		}
		
		Collections.sort(m_feeds, cmp);
		m_adapter.notifyDataSetInvalidated();
	}
	
	public class GetIconsTask extends AsyncTask<FeedList, Integer, Integer> {

		private String m_baseUrl;
		
		public GetIconsTask(String baseUrl) {
			m_baseUrl = baseUrl;
		}

		@Override
		protected Integer doInBackground(FeedList... params) {

			try {
				File storage = Environment.getExternalStorageDirectory();
				final File iconPath = new File(storage.getAbsolutePath() + ICON_PATH);
				if (!iconPath.exists()) iconPath.mkdirs();
			
				if (iconPath.exists()) {
					for (Feed feed : params[0])	 {
						if (feed.id > 0 && feed.has_icon) {
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
			
			if (m_prefs.getBoolean("ssl_trust_any", false)) {
				client.getConnectionManager().getSchemeRegistry().register(new Scheme("https", new EasySSLSocketFactory(), 443));
			}

			HttpGet httpGet = new HttpGet(fetchUrl);
			HttpContext context = null;

			String httpLogin = m_prefs.getString("http_login", "");
			String httpPassword = m_prefs.getString("http_password", "");
			
			if (httpLogin.length() > 0) {

				URL targetUrl;
				try {
					targetUrl = new URL(fetchUrl);
				} catch (MalformedURLException e) {
					e.printStackTrace();
					client.close();
					return;
				}
				
				HttpHost targetHost = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getProtocol());
				CredentialsProvider cp = new BasicCredentialsProvider();
				context = new BasicHttpContext();
				
				cp.setCredentials(
		                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
		                new UsernamePasswordCredentials(httpLogin, httpPassword));

				context.setAttribute(ClientContext.CREDS_PROVIDER, cp);
			}
			

			try {
				HttpResponse execute = client.execute(httpGet, context);
				
				InputStream content = execute.getEntity().getContent();

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
			m_adapter.notifyDataSetInvalidated();
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
