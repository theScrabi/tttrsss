package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import android.app.Application;

public class TinyApplication extends Application {
	private static TinyApplication m_singleton;
	
	public ArticleList m_articles = new ArticleList();
	public Feed m_feed;
	public Article m_selectedArticle;
	
	public static TinyApplication getInstance(){
		return m_singleton;
	}
	
	@Override
	public final void onCreate() {
		super.onCreate();
		m_singleton = this;
	}	
}
