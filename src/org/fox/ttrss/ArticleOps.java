package org.fox.ttrss;

public interface ArticleOps {
	public Article getSelectedArticle();
	public void saveArticleUnread(final Article article);
	public void saveArticleMarked(final Article article);
	public void saveArticlePublished(final Article article);
	public void onArticleOpened(Article article);
	public void updateHeadlines();
}

