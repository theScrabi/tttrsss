package org.fox.ttrss.util;

import java.lang.reflect.Type;
import java.util.List;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.GlobalState;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

public class HeadlinesRequest extends ApiRequest {
	public static final int HEADLINES_REQUEST_SIZE = 30;
	public static final int HEADLINES_BUFFER_MAX = 500;

	private int m_offset = 0;
	private OnlineActivity m_activity;
	private ArticleList m_articles = GlobalState.getInstance().m_loadedArticles;
	
	public HeadlinesRequest(Context context, OnlineActivity activity) {
		super(context);
		
		m_activity = activity;
	}
	
	protected void onPostExecute(JsonElement result) {
		if (result != null) {
			try {			
				JsonArray content = result.getAsJsonArray();
				if (content != null) {
					Type listType = new TypeToken<List<Article>>() {}.getType();
					final List<Article> articles = new Gson().fromJson(content, listType);
					
					while (m_articles.size() > HEADLINES_BUFFER_MAX)
						m_articles.remove(0);
					
					if (m_offset == 0)
						m_articles.clear();
					else
						if (m_articles.get(m_articles.size()-1).id == -1)
							m_articles.remove(m_articles.size()-1); // remove previous placeholder
					
					for (Article f : articles) 
						m_articles.add(f);

					if (articles.size() == HEADLINES_REQUEST_SIZE) {
						Article placeholder = new Article(-1);
						m_articles.add(placeholder);
					}

					/* if (m_articles.size() == 0)
						m_activity.setLoadingStatus(R.string.no_headlines_to_display, false);
					else */
					
					m_activity.setLoadingStatus(R.string.blank, false);
					
					return;
				}
						
			} catch (Exception e) {
				e.printStackTrace();						
			}
		}

		if (m_lastError == ApiError.LOGIN_FAILED) {
			m_activity.login();
		} else {
			m_activity.setLoadingStatus(getErrorMessage(), false);
		}
    }

	public void setOffset(int skip) {
		m_offset = skip;			
	}
}
