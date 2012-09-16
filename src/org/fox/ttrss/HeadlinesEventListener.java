package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

public interface HeadlinesEventListener {

	boolean getUnreadArticlesOnly();
	void onArticleListSelectionChange(ArticleList m_selectedArticles);
	void onArticleSelected(Article article);
	void saveArticleUnread(Article article);
	void onArticleSelected(Article article, boolean open);

}
