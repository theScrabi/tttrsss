package org.fox.ttrss;

import java.util.ArrayList;

import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;

import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("serial")
public class FeedCategoryList extends ArrayList<FeedCategory> implements Parcelable {

		public FeedCategoryList() { }
	
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(this.size());
			for (FeedCategory feed : this) {
				out.writeParcelable(feed, flags);
			}
		}
		
		public void readFromParcel(Parcel in) {
			int length = in.readInt();
			
			for (int i = 0; i < length; i++) {
				FeedCategory feed = in.readParcelable(Feed.class.getClassLoader());
				this.add(feed);
			}
			
		}
				
		public FeedCategoryList(Parcel in) {
			readFromParcel(in);
		}
		
		@SuppressWarnings("rawtypes")
		public static final Parcelable.Creator CREATOR =
	    	new Parcelable.Creator() {
	            public FeedCategoryList createFromParcel(Parcel in) {
	                return new FeedCategoryList(in);
	            }
	 
	            public FeedCategoryList[] newArray(int size) {
	                return new FeedCategoryList[size];
	            }
	        };
	}
