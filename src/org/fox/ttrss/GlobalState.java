package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import android.app.Application;

public class GlobalState extends Application {
	private static GlobalState m_singleton;
	
	public ArticleList m_loadedArticles = new ArticleList();
	public Feed m_activeFeed;
	public Article m_activeArticle;
	public int m_selectedArticleId;
	public boolean m_unreadOnly = true;
	public boolean m_unreadArticlesOnly = true;
	public String m_sessionId;
	public int m_apiLevel;
	
	public static GlobalState getInstance(){
		return m_singleton;
	}
	
	@Override
	public final void onCreate() {
		super.onCreate();
		m_singleton = this;
	}
}
