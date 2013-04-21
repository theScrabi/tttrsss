package org.fox.ttrss.offline;


public interface OfflineHeadlinesEventListener {
	void onArticleSelected(int articleId, boolean open);
	void onArticleSelected(int articleId);
}
