package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class Attachment implements Parcelable {
	public int id;
	public String content_url;
	public String content_type;
	public String title;
	public String duration;
	public int post_id;
	
	public Attachment(Parcel in) {
		readFromParcel(in);
	}
	
	public Attachment() {
		
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
	
	public String toString() {
		if (title != null && title.length() > 0) {
			return title;
		} else {		
			try {
				URL url = new URL(content_url.trim());
				return new File(url.getFile()).getName();
			} catch (MalformedURLException e) {
				return content_url;
			}
		}
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
