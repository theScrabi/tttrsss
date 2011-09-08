package org.fox.ttrss;

public class Feed implements Comparable<Feed> {
	String feed_url;
	String title;
	int id;
	int unread;
	boolean has_icon;
	int cat_id;
	int last_updated;
	
	@Override
	public int compareTo(Feed feed) {
		if (feed.unread != this.unread)
			return feed.unread - this.unread;
		else
			return this.title.compareTo(feed.title);
	}
}