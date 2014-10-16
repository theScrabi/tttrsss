package org.fox.ttrss.types;

import java.util.ArrayList;


import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

@SuppressWarnings("serial")
public class ArticleList extends ArrayList<Article> implements Parcelable {
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeList(this);
	}
	
	public Article findById(int id) {
		for (Article a : this) {
			if (a.id == id)
				return a;			
		}			
		return null;
	}
	
	public void readFromParcel(Parcel in) {
		in.readList(this, getClass().getClassLoader());
	}
	
	public ArticleList() { }
	
	public ArticleList(Parcel in) {		
		readFromParcel(in);
	}
	
	public boolean containsId(int id) {
		for (Article a : this) {
			if (a.id == id)
				return true;
		}
		return false;
	}
	
	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public ArticleList createFromParcel(Parcel in) {
                return new ArticleList(in);
            }
 
            public ArticleList[] newArray(int size) {
                return new ArticleList[size];
            }
        };
}