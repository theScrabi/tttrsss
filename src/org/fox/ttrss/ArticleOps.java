package org.fox.ttrss;

public interface ArticleOps {
	public enum RelativeArticle { BEFORE, AFTER };
	
	public Article getSelectedArticle();
	public void saveArticleUnread(final Article article);
	public void saveArticleMarked(final Article article);
	public void saveArticlePublished(final Article article);
	public void updateHeadlines();
	public void openArticle(Article article, int compatAnimation);
	public Article getRelativeArticle(Article article, RelativeArticle ra);
}

