package org.fox.ttrss.types;

import java.util.ArrayList;


import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("serial")
public class FeedList extends ArrayList<Feed> implements Parcelable {

		public FeedList() { }
	
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(this.size());
			for (Feed feed : this) {
				out.writeParcelable(feed, flags);
			}
		}
		
		public void readFromParcel(Parcel in) {
			int length = in.readInt();
			
			for (int i = 0; i < length; i++) {
				Feed feed = in.readParcelable(Feed.class.getClassLoader());
				this.add(feed);
			}
			
		}
				
		public FeedList(Parcel in) {
			readFromParcel(in);
		}
		
		@SuppressWarnings("rawtypes")
		public static final Parcelable.Creator CREATOR =
	    	new Parcelable.Creator() {
	            public FeedList createFromParcel(Parcel in) {
	                return new FeedList(in);
	            }
	 
	            public FeedList[] newArray(int size) {
	                return new FeedList[size];
	            }
	        };
	}
