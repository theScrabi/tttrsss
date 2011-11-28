package org.fox.ttrss;

import android.os.Parcel;
import android.os.Parcelable;

public class FeedCategory implements Parcelable {
	int id;
	String title;
	int unread;
	
	public FeedCategory(Parcel in) {
		readFromParcel(in);
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
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		title = in.readString();
		unread = in.readInt();
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
