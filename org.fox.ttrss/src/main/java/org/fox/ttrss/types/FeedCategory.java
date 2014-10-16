package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

public class FeedCategory implements Parcelable {
	public int id;
	public String title;
	public int unread;
	public int order_id;
	
	public FeedCategory(Parcel in) {
		readFromParcel(in);
	}
	
	public FeedCategory(int id, String title, int unread) {
		this.id = id;
		this.title = title;
		this.unread = unread;
		this.order_id = 0;
	}

	public FeedCategory() {
		
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeString(title);
		out.writeInt(unread);
		out.writeInt(order_id);
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		title = in.readString();
		unread = in.readInt();
		order_id = in.readInt();
	}
	
	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public FeedCategory createFromParcel(Parcel in) {
                return new FeedCategory(in);
            }
 
            public FeedCategory[] newArray(int size) {
                return new FeedCategory[size];
            }
        };
}
