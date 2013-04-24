package org.fox.ttrss.types;
import java.util.ArrayList;
import java.util.List;


import android.os.Parcel;
import android.os.Parcelable;

// TODO: serialize Labels
public class Article implements Parcelable {
	public int id;
	public boolean unread; 
	public boolean marked; 
	public boolean published; 
	public int score;
	public int updated; 
	public boolean is_updated; 
	public String title; 
	public String link; 
	public int feed_id; 
	public List<String> tags;
	public List<Attachment> attachments;
	public String content;
	public List<List<String>> labels;
	public String feed_title;	
	public int comments_count;
	public String comments_link;
	public boolean always_display_attachments;
	public String author;
	
	public Article(Parcel in) {
		readFromParcel(in);
	}
	
	public Article() {
		
	}
	
	public Article(int id) {
		this.id = id;
		this.title = "";
		this.link = "";
		this.tags = new ArrayList<String>();
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
		out.writeInt(score);
		out.writeInt(updated);
		out.writeInt(is_updated ? 1 : 0);
		out.writeString(title);
		out.writeString(link);
		out.writeInt(feed_id);
		out.writeStringList(tags);
		out.writeString(content);
		out.writeList(attachments);
		out.writeString(feed_title);
		out.writeInt(comments_count);
		out.writeString(comments_link);
		out.writeInt(always_display_attachments ? 1 : 0);
		out.writeString(author);
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		unread = in.readInt() == 1;
		marked = in.readInt() == 1;
		published = in.readInt() == 1;
		score = in.readInt();
		updated = in.readInt();
		is_updated = in.readInt() == 1;
		title = in.readString();
		link = in.readString();
		feed_id = in.readInt();
		
		if (tags == null) tags = new ArrayList<String>();
		in.readStringList(tags);
		
		content = in.readString();
		
		attachments = new ArrayList<Attachment>();
		in.readList(attachments, Attachment.class.getClassLoader());
		
		feed_title = in.readString();
		
		comments_count = in.readInt();
		comments_link = in.readString();
		always_display_attachments = in.readInt() == 1;
		author = in.readString();
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
