package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.FeedsFragment.OnFeedSelectedListener;
import org.jsoup.Jsoup;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

public class HeadlinesFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	protected SharedPreferences m_prefs;
	
	private String m_sessionId;
	private Feed m_feed;
	//private int m_activeArticleId;
	
	private ArticleListAdapter m_adapter;
	private List<Article> m_articles = new ArrayList<Article>();
	private OnArticleSelectedListener m_articleSelectedListener;
	
	public interface OnArticleSelectedListener {
		public void onArticleSelected(Article article);
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			//m_feedId = savedInstanceState.getInt("feedId");
			//m_activeArticleId = savedInstanceState.getInt("activeArticleId");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		ListView list = (ListView)view.findViewById(R.id.headlines);		
		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		
		if (m_feed != null) refresh();
		
		return view;    	
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
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		
		m_sessionId = ((MainActivity)activity).getSessionId();
		m_feed = ((MainActivity)activity).getActiveFeed();
		
		m_articleSelectedListener = (OnArticleSelectedListener) activity;
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Article article = (Article)list.getItemAtPosition(position);
			m_articleSelectedListener.onArticleSelected(article);
		}
	}

	public void refresh() {
		HeadlinesRequest req = new HeadlinesRequest();
		
		req.setApi(m_prefs.getString("ttrss_url", null));

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", m_sessionId);
				put("feed_id", String.valueOf(m_feed.id));
				put("show_content", "true");
				put("limit", String.valueOf(30));
				put("offset", String.valueOf(0));
				put("view_mode", "adaptive");
			}			 
		};

		req.execute(map);
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
		//out.putInt("feedId", m_feedId);		
		//out.putInt("activeArticleId", m_activeArticleId);
	}

	private class HeadlinesRequest extends ApiRequest {
		
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonObject rv = result.getAsJsonObject();

					Gson gson = new Gson();
					
					int status = rv.get("status").getAsInt();
					
					if (status == 0) {
						JsonArray content = rv.get("content").getAsJsonArray();
						if (content != null) {
							Type listType = new TypeToken<List<Article>>() {}.getType();
							final List<Article> articles = gson.fromJson(content, listType);
							
							getActivity().runOnUiThread(new Runnable() {
								public void run() {
									m_articles.clear();
									
									for (Article f : articles) 
										m_articles.add(f);
									
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
					ma.toast("Error parsing headlines: incorrect format");
				}
			} else {
				MainActivity ma = (MainActivity)getActivity();
				ma.toast("Error parsing headlines: null object.");
			}
			
			return;

	    }
	}
	
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;

		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			Article article = items.get(position);

			if (v == null) {
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.headlines_row, null);
			}

			TextView tt = (TextView) v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(article.title);
				//tt.setTextAppearance(getContext(), R.style.Connection);
				
				if (article.unread)
					tt.setTextAppearance(getContext(), R.style.UnreadArticle);
				else
					tt.setTextAppearance(getContext(), R.style.Article);

			}

			TextView te = (TextView) v.findViewById(R.id.excerpt);

			if (te != null) {
				String excerpt = Jsoup.parse(article.content).text(); 
				
				if (excerpt.length() > 250)
					excerpt = excerpt.substring(0, 250) + "...";
				
				te.setText(excerpt);
			}       	

			return v;
		}
	}

}
