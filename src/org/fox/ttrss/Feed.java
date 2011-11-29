package org.fox.ttrss;

import android.os.Parcel;
import android.os.Parcelable;

public class Feed implements Comparable<Feed>, Parcelable {
	String feed_url;
	String title;
	int id;
	int unread;
	boolean has_icon;
	int cat_id;
	int last_updated;
	boolean is_cat;
	
	public Feed(int id, String title, boolean is_cat) {
		this.id = id;
		this.title = title;
		this.is_cat = is_cat;
	}
	
	public Feed(Parcel in) {
		readFromParcel(in);
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