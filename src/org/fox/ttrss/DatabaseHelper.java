package org.fox.ttrss;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;


public class DatabaseHelper extends SQLiteOpenHelper {

	private final String TAG = this.getClass().getSimpleName();
	public static final String DATABASE_NAME = "LocalStorage";
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, 1);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS feeds (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "feed_url TEXT, " +
                "title TEXT, " +
                "unread INTEGER, " +
                "has_icon BOOLEAN, " +
                "cat_id INTEGER, " +
                "last_updated INTEGER)");                

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub

	}

}
