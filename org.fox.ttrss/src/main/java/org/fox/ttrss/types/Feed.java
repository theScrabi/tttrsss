package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

public class Feed implements Comparable<Feed>, Parcelable {
	public String feed_url;
	public String title;
	public int id;
	public int unread;
	public boolean has_icon;
	public int cat_id;
	public int last_updated;
	public int order_id;
	public boolean is_cat;
    public boolean always_display_as_feed;
    public String display_title;
	
	public Feed(int id, String title, boolean is_cat) {
		this.id = id;
		this.title = title;
		this.is_cat = is_cat;
	}
	
	public Feed(Parcel in) {
		readFromParcel(in);
	}
	
	public Feed() {
		
	}
	
	public boolean equals(Feed feed) {
		if (feed == this) 
			return true;
		
		if (feed == null)
			return false;
		
		return feed.id == this.id && (this.title == null || this.title.equals(feed.title)) && this.is_cat == feed.is_cat;
	}
	
	@Override
	public int compareTo(Feed feed) {
		if (feed.unread != this.unread)
			return feed.unread - this.unread;
		else
			return this.title.compareTo(feed.title);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(feed_url);
		out.writeString(title);
		out.writeInt(id);
		out.writeInt(unread);
		out.writeInt(has_icon ? 1 : 0);
		out.writeInt(cat_id);
		out.writeInt(last_updated);
		out.writeInt(is_cat ? 1 : 0);
		out.writeInt(order_id);
        out.writeInt(always_display_as_feed ? 1 : 0);
        out.writeString(display_title);
	}
	
	public void readFromParcel(Parcel in) {
		feed_url = in.readString();
		title = in.readString();
		id = in.readInt();
		unread = in.readInt();
		has_icon = in.readInt() == 1;
		cat_id = in.readInt();
		last_updated = in.readInt();
		is_cat = in.readInt() == 1;
		order_id = in.readInt();
        always_display_as_feed = in.readInt() == 1;
        display_title = in.readString();
	}
	
	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public Feed createFromParcel(Parcel in) {
                return new Feed(in);
            }
 
            public Feed[] newArray(int size) {
                return new Feed[size];
            }
        };
}