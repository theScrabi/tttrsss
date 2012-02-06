package org.fox.ttrss;

import android.os.Parcel;
import android.os.Parcelable;

public class Attachment implements Parcelable {
	int id;
	String content_url;
	String content_type;
	String title;
	String duration;
	int post_id;
	
	public Attachment(Parcel in) {
		readFromParcel(in);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeString(content_url);
		out.writeString(content_type);
		out.writeString(title);
		out.writeString(duration);
		out.writeInt(post_id);
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		content_url = in.readString();
		content_type = in.readString();
		title = in.readString();
		duration = in.readString();
		post_id = in.readInt();
	}
	
	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public Attachment createFromParcel(Parcel in) {
                return new Attachment(in);
            }
 
            public Attachment[] newArray(int size) {
                return new Attachment[size];
            }
        };

}
