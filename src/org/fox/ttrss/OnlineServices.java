package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;

public interface OnlineServices {
	public enum RelativeArticle { BEFORE, AFTER };
	
	public void saveArticleUnread(final Article article);
	public void saveArticleMarked(final Article article);
	public void saveArticlePublished(final Article article);
	public void setSelectedArticle(Article article);
	public boolean getUnreadArticlesOnly();
	
	public void onCatSelected(FeedCategory cat);
	public void onFeedSelected(Feed feed);
	public void onArticleSelected(Article article);
	public void onArticleListSelectionChange(ArticleList selection);
	
	//public void initMainMenu();
	public void login();
	public String getSessionId();
	public boolean isSmallScreen();
	public boolean getUnreadOnly();
	public int getApiLevel();
	public boolean isPortrait();
	
	public void copyToClipboard(String str);
}

