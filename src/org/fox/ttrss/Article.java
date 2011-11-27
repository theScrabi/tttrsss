package org.fox.ttrss;
import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;


public class Article implements Parcelable {
	int id;
	boolean unread; 
	boolean marked; 
	boolean published; 
	int updated; 
	boolean is_updated; 
	String title; 
	String link; 
	int feed_id; 
	List<String> tags; 
	String content;
	
	public Article(Parcel in) {
		readFromParcel(in);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeInt(unread ? 1 : 0);
		out.writeInt(marked ? 1 : 0);
		out.writeInt(published ? 1 : 0);
		out.writeInt(updated);
		out.writeInt(is_updated ? 1 : 0);
		out.writeString(title);
		out.writeString(link);
		out.writeInt(feed_id);
		out.writeStringList(tags);
		out.writeString(content);
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		unread = in.readInt() == 1;
		marked = in.readInt() == 1;
		published = in.readInt() == 1;
		updated = in.readInt();
		is_updated = in.readInt() == 1;
		title = in.readString();
		link = in.readString();
		feed_id = in.readInt();
		
		if (tags == null) tags = new ArrayList<String>();
		in.readStringList(tags);
		
		content = in.readString();
	}
	
	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public Article createFromParcel(Parcel in) {
                return new Article(in);
            }
 
            public Article[] newArray(int size) {
                return new Article[size];
            }
        };
}
