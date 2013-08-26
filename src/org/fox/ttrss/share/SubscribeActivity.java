package org.fox.ttrss.share;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.ApiRequest.ApiError;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.types.FeedCategoryList;
import org.fox.ttrss.R;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class SubscribeActivity extends CommonShareActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	private Button m_postButton;
	private Button m_catButton;
	private CatListAdapter m_adapter;
	private FeedCategoryList m_cats = new FeedCategoryList();
	
	private static final int REQ_CATS = 1;
	private static final int REQ_POST = 2;
	
	class CatTitleComparator implements Comparator<FeedCategory> {

		@Override
		public int compare(FeedCategory a, FeedCategory b) {
			if (a.id >= 0 && b.id >= 0)
				return a.title.compareTo(b.title);
			else
				return a.id - b.id;
		}
		
	}
	
	public void sortCats() {
		Comparator<FeedCategory> cmp = new CatTitleComparator();
		
		Collections.sort(m_cats, cmp);
		try {
			m_adapter.notifyDataSetChanged();
		} catch (NullPointerException e) {
			// adapter missing
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_LEFT_ICON);

		String urlValue = getIntent().getDataString();
		
		if (savedInstanceState != null) {
			urlValue = savedInstanceState.getString("url");
			
			ArrayList<FeedCategory> list = savedInstanceState.getParcelableArrayList("cats");
			
			for (FeedCategory c : list)
				m_cats.add(c);
		}

		setContentView(R.layout.subscribe);
		
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon);
		
		setSmallScreen(false); 
		
		Spinner catList = (Spinner) findViewById(R.id.category_spinner);

		if (m_cats.size() == 0) m_cats.add(new FeedCategory(0, "Uncategorized", 0));
		
		m_adapter = new CatListAdapter(this, android.R.layout.simple_spinner_dropdown_item, m_cats);
		catList.setAdapter(m_adapter);
		
		EditText feedUrl = (EditText) findViewById(R.id.feed_url);
		feedUrl.setText(urlValue);
		
		m_postButton = (Button) findViewById(R.id.subscribe_button);
		
		m_postButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				login(REQ_POST);
			} 
		});
		
		m_catButton = (Button) findViewById(R.id.cats_button);
		
		m_catButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				login(REQ_CATS);
			} 
		});
		
		login(REQ_CATS);
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		EditText url = (EditText) findViewById(R.id.url);

		if (url != null) {
			out.putString("url", url.getText().toString());
		}
		
		out.putParcelableArrayList("cats", m_cats);

	}
	
	private void subscribeToFeed() {
		m_postButton.setEnabled(false);
		
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				setProgressBarIndeterminateVisibility(false);

				if (m_lastError != ApiError.NO_ERROR) {
					toast(getErrorMessage());
				} else {
					try {					
						switch (m_apiStatusCode) {
						case 0:
							toast(R.string.error_feed_already_exists_);
							finish();
							break;
						case 1:
							toast(R.string.subscribed_to_feed);
							finish();
							break;
						case 2:
							toast(R.string.error_invalid_url);
							break;
						case 3:
							toast(R.string.error_url_is_an_html_page_no_feeds_found);
							break;
						case 4:
							toast(R.string.error_url_contains_multiple_feeds);
							break;
						case 5:
							toast(R.string.error_could_not_download_url);
							break;						
						}
						
					} catch (Exception e) {
						toast(R.string.error_while_subscribing);
						e.printStackTrace();
					}
				}
				
				m_postButton.setEnabled(true);
			}
		};
		
		Spinner catSpinner = (Spinner) findViewById(R.id.category_spinner);
		
		final FeedCategory cat = (FeedCategory) m_adapter.getCategory(catSpinner.getSelectedItemPosition());		
		final EditText feedUrl = (EditText) findViewById(R.id.feed_url);

		if (feedUrl != null ) {
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "subscribeToFeed");
					put("feed_url", feedUrl.getText().toString());

					if (cat != null) {
						put("category_id", String.valueOf(cat.id));
					}
				}
			};
	
			setProgressBarIndeterminateVisibility(true);
			
			req.execute(map);
		}
	}
	
	@Override
	public void onLoggingIn(int requestId) {
		switch (requestId) {
		case REQ_CATS:
			m_catButton.setEnabled(false);
			break;
		case REQ_POST:
			m_postButton.setEnabled(false);
			break;
		}
	}
	
	private void updateCats() {
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				setProgressBarIndeterminateVisibility(false);

				if (m_lastError != ApiError.NO_ERROR) {
					toast(getErrorMessage());
				} else {
					JsonArray content = result.getAsJsonArray();
					
					if (content != null) {
						Type listType = new TypeToken<List<FeedCategory>>() {}.getType();
						final List<FeedCategory> cats = new Gson().fromJson(content, listType);
						
						m_cats.clear();
												
						for (FeedCategory c : cats) {
							if (c.id > 0)
								m_cats.add(c);							
						}
						
						sortCats();
						
						m_cats.add(0, new FeedCategory(0, "Uncategorized", 0));
						
						m_adapter.notifyDataSetChanged();
												
						toast(R.string.category_list_updated);
					}
				}
				
				m_catButton.setEnabled(true);
			}
		};

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", m_sessionId);
				put("op", "getCategories");
			}
		};
	
		setProgressBarIndeterminateVisibility(true);
			
		req.execute(map);
	}
	
	@Override
	protected void onLoggedIn(int requestId) {
		switch (requestId) {
		case REQ_CATS:
			updateCats();			
			break;
		case REQ_POST:			
			m_postButton.setEnabled(true);			
			if (m_apiLevel < 5) {
				toast(R.string.api_too_low);									
			} else {
				subscribeToFeed();									
			}		
			break;
		}	
	}

	private class CatListAdapter extends ArrayAdapter<String> {
		private List<FeedCategory> m_items;
		
		public CatListAdapter(Context context, int resource,
				List<FeedCategory> items) {
			super(context, resource);
			
			m_items = items;
		}

		@Override
		public String getItem(int item) {
			return m_items.get(item).title;
		}
		
		public FeedCategory getCategory(int item) {
			try {
				return m_items.get(item);
			} catch (ArrayIndexOutOfBoundsException e) {
				return null;
			}
		}
		
		@Override
		public int getCount() {
			return m_items.size();
		}
	}
	
}
