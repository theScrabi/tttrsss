package org.fox.ttrss;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import org.jsoup.Jsoup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class HeadlinesFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	
	private Feed m_feed;
	private int m_selectedArticleId;
	
	private ArticleListAdapter m_adapter;
	private ArticleList m_articles = new ArticleList();
	private ArticleList m_selectedArticles = new ArticleList();
	
	private OnArticleSelectedListener m_articleSelectedListener;
	
	public interface OnArticleSelectedListener {
		public void onArticleSelected(Article article);
	}
	
	private class ArticleList extends ArrayList<Article> implements Parcelable {
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(this.size());
			for (Article article : this) {
				out.writeParcelable(article, flags);
			}
		}
		
		public void readFromParcel(Parcel in) {
			int length = in.readInt();
			
			for (int i = 0; i < length; i++) {
				Article article = in.readParcelable(Article.class.getClassLoader());
				this.add(article);
			}
			
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_feed = savedInstanceState.getParcelable("feed");
			m_articles = savedInstanceState.getParcelable("articles");
			m_selectedArticleId = savedInstanceState.getInt("selectedArticleId");
			m_selectedArticles = savedInstanceState.getParcelable("selectedArticles");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		ListView list = (ListView)view.findViewById(R.id.headlines);		
		m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, (ArrayList<Article>)m_articles);
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
		
		Log.d(TAG, "onCreateView, feed=" + m_feed);
		
		if (m_feed != null && (m_articles == null || m_articles.size() == 0)) 
			refresh();
		else
			view.findViewById(R.id.loading_container).setVisibility(View.GONE);

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
		m_feed = ((MainActivity)activity).getActiveFeed();
		m_articleSelectedListener = (OnArticleSelectedListener) activity;
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		if (list != null) {
			Article article = (Article)list.getItemAtPosition(position);
			m_articleSelectedListener.onArticleSelected(article);
			
			article.unread = false;
			m_selectedArticleId = article.id;
			m_adapter.notifyDataSetChanged();
			
			catchupArticle(article);
		}
	}

	public void refresh() {
		HeadlinesRequest req = new HeadlinesRequest();
		
		req.setApi(m_prefs.getString("ttrss_url", null));
		
		final String sessionId = ((MainActivity)getActivity()).getSessionId();

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("op", "getHeadlines");
				put("sid", sessionId);
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
		
		out.putParcelable("feed", m_feed);
		out.putParcelable("articles", m_articles);
		out.putInt("selectedArticleId", m_selectedArticleId);
		out.putParcelable("selectedArticles", m_selectedArticles);
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
						MainActivity activity = (MainActivity)getActivity();							
						activity.login();
						showLoading(false);
					}
				} catch (Exception e) {
					e.printStackTrace();
					// report invalid object
				}
			} else {
				// report null object
			}
			
			return;

	    }
	}
	
	public void catchupArticle(final Article article) {
		ApiRequest ar = new ApiRequest();
		ar.setApi(m_prefs.getString("ttrss_url", null));

		final String sessionId = ((MainActivity)getActivity()).getSessionId();

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("sid", sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", "0");
				put("field", "2");
			}			 
		};

		ar.execute(map);
	}

	public void setArticleMarked(final Article article) {
		ApiRequest ar = new ApiRequest();
		ar.setApi(m_prefs.getString("ttrss_url", null));

		final String sessionId = ((MainActivity)getActivity()).getSessionId();

		HashMap<String,String> map = new HashMap<String,String>() {
			{
				put("sid", sessionId);
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.marked ? "1" : "0");
				put("field", "0");
			}			 
		};

		ar.execute(map);
	}
	private class ArticleListAdapter extends ArrayAdapter<Article> {
		private ArrayList<Article> items;
		
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		
		public static final int VIEW_COUNT = VIEW_SELECTED+1;
		
		public ArticleListAdapter(Context context, int textViewResourceId, ArrayList<Article> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}
		
		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);
			
			if (a.id == m_selectedArticleId) {
				return VIEW_SELECTED;
			} else if (a.unread) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			final Article article = items.get(position);

			if (v == null) {
				int layoutId = R.layout.headlines_row;
				
				switch (getItemViewType(position)) {
				case VIEW_UNREAD:
					layoutId = R.layout.headlines_row_unread;
					break;
				case VIEW_SELECTED:
					layoutId = R.layout.headlines_row_selected;
					break;
				}
				
				LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutId, null);
			}

			TextView tt = (TextView)v.findViewById(R.id.title);

			if (tt != null) {
				tt.setText(article.title);
			}

			ImageView marked = (ImageView)v.findViewById(R.id.marked);
			
			if (marked != null) {
				marked.setImageResource(article.marked ? android.R.drawable.star_on : android.R.drawable.star_off);
				
				marked.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						article.marked = !article.marked;
						m_adapter.notifyDataSetChanged();
						
						setArticleMarked(article);
					}
				});
			}
			
			TextView te = (TextView)v.findViewById(R.id.excerpt);

			if (te != null) {
				String excerpt = Jsoup.parse(article.content).text(); 
				
				if (excerpt.length() > 250)
					excerpt = excerpt.substring(0, 100) + "...";
				
				te.setText(excerpt);
			}       	

			TextView dv = (TextView) v.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date((long)article.updated * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				dv.setText(df.format(d));
			}
			
			CheckBox cb = (CheckBox) v.findViewById(R.id.selected);

			if (cb != null) {
				cb.setChecked(m_selectedArticles.contains(article));
				
				cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {

						if (isChecked) {
							m_selectedArticles.add(article);
						} else {
							m_selectedArticles.remove(article);
						}
						
						Log.d(TAG, "num selected: " + m_selectedArticles.size());
						
					}
				});
			}
			
			return v;
		}
	}

}
