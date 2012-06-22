package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;

public interface OnlineServices {
	public enum RelativeArticle { BEFORE, AFTER };
	
	public Article getSelectedArticle();
	public void saveArticleUnread(final Article article);
	public void saveArticleMarked(final Article article);
	public void saveArticlePublished(final Article article);
	public void openArticle(Article article, int compatAnimation);
	public Article getRelativeArticle(Article article, RelativeArticle ra);
	
	public void onCatSelected(FeedCategory cat);
	public void onFeedSelected(Feed feed);
	
	public void initMainMenu();
	public void login();
	public String getSessionId();
	public boolean getUnreadArticlesOnly();
	public boolean isSmallScreen();
	public boolean getUnreadOnly();
	public int getApiLevel();
	public void setSelectedArticle(Article article);
	public int getOrientation();
	
	public void copyToClipboard(String str);
}

