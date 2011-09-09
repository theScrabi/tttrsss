package org.fox.ttrss;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;


public class DatabaseHelper extends SQLiteOpenHelper {

	private final String TAG = this.getClass().getSimpleName();
	public static final String DATABASE_NAME = "LocalStorage";
	public static final int DATABASE_VERSION = 4;
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS feeds;");
		db.execSQL("DROP TABLE IF EXISTS articles;");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS feeds (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "feed_url TEXT, " +
                "title TEXT, " +
                "unread INTEGER, " +
                "has_icon BOOLEAN, " +
                "cat_id INTEGER, " +
                "last_updated INTEGER, " +
                "count INTEGER" +
                ");");                

		db.execSQL("CREATE TABLE IF NOT EXISTS articles (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "unread BOOLEAN, " +
                "marked BOOLEAN, " +
                "published BOOLEAN, " +
                "updated INTEGER, " +
                "is_updated BOOLEAN, " +
                "title TEXT, " +
                "link TEXT, " +
                "feed_id INTEGER, " +
                "tags TEXT, " +
                "content TEXT" +
                ");");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onCreate(db);
	}

}
