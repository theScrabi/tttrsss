package org.fox.ttrss;

public interface ArticleEventListener {

	void copyToClipboard(String content_url);

	boolean isSmallScreen();

}
