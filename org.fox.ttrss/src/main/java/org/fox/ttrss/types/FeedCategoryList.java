package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

@SuppressWarnings("serial")
public class FeedCategoryList extends ArrayList<FeedCategory> implements Parcelable {

		public FeedCategoryList() { }
	
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeList(this);
		}
		
		public void readFromParcel(Parcel in) {
			in.readList(this, getClass().getClassLoader());			
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
