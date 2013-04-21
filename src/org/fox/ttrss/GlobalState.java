package org.fox.ttrss;

import java.util.ArrayList;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import android.app.Application;
import android.os.Bundle;
import android.os.Parcelable;

public class GlobalState extends Application {
	private static GlobalState m_singleton;
	
	public ArticleList m_loadedArticles = new ArticleList();
	public Feed m_activeFeed;
	public Article m_activeArticle;
	public int m_selectedArticleId;
	public String m_sessionId;
	public int m_apiLevel;
	public boolean m_canUseProgress;
	
	public static GlobalState getInstance(){
		return m_singleton;
	}
	
	@Override
	public final void onCreate() {
		super.onCreate();
		m_singleton = this;
	}
	
	public void save(Bundle out) {
		out.putParcelableArrayList("gs:loadedArticles", m_loadedArticles);
		out.putParcelable("gs:activeFeed", m_activeFeed);
		out.putParcelable("gs:activeArticle", m_activeArticle);
		out.putString("gs:sessionId", m_sessionId);
		out.putInt("gs:apiLevel", m_apiLevel);
		out.putBoolean("gs:canUseProgress", m_canUseProgress);
		out.putInt("gs:selectedArticleId", m_selectedArticleId);
	}
	
	public void load(Bundle in) {
		if (m_loadedArticles.size() == 0 && in != null) {
			ArrayList<Parcelable> list = in.getParcelableArrayList("gs:loadedArticles");
			
			for (Parcelable p : list) {
				m_loadedArticles.add((Article)p);
			}
			
			m_activeFeed = (Feed) in.getParcelable("gs:activeFeed");
			m_activeArticle = (Article) in.getParcelable("gs:activeArticle");
			m_sessionId = in.getString("gs:sessionId");
			m_apiLevel = in.getInt("gs:apiLevel");
			m_canUseProgress = in.getBoolean("gs:canUseProgress");
			m_selectedArticleId = in.getInt("gs:selectedArticleId");
		}
				
	}
}
