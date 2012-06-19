package org.fox.ttrss;

import java.util.ArrayList;

import org.fox.ttrss.types.Article;

import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("serial")
public class ArticleList extends ArrayList<Article> implements Parcelable {
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(this.size());
		for (Article article : this) {
			out.writeParcelable(article, flags);
		}
	}
	
	public void readFromParcel(Parcel in) {
		int length = in.readInt();
		
		for (int i = 0; i < length; i++) {
			Article article = in.readParcelable(Article.class.getClassLoader());
			this.add(article);
		}
	}
	
	public ArticleList() { }
	
	public ArticleList(Parcel in) {
		readFromParcel(in);
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