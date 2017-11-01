package org.fox.ttrss.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;


public class DatabaseHelper extends SQLiteOpenHelper {

	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();
	public static final String DATABASE_NAME = "OfflineStorage.db";
	public static final int DATABASE_VERSION = 5;
	private static DatabaseHelper m_instance;
	private Context m_context;

	private DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		m_context = context;
	}

	public static synchronized DatabaseHelper getInstance(Context context) {

		if (m_instance == null) {
			m_instance = new DatabaseHelper(context.getApplicationContext());
		}

		return m_instance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("DROP VIEW IF EXISTS cats_unread;");
		db.execSQL("DROP VIEW IF EXISTS feeds_unread;");
		db.execSQL("DROP TRIGGER IF EXISTS articles_set_modified;");
		db.execSQL("DROP TABLE IF EXISTS categories;");
		db.execSQL("DROP TABLE IF EXISTS feeds;");
		db.execSQL("DROP TABLE IF EXISTS articles;");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS feeds (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "feed_url TEXT, " +
                "title TEXT, " +
                "has_icon BOOLEAN, " +
                "cat_id INTEGER" +
                ");");                

		db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT" +
                ");");                

		db.execSQL("CREATE TABLE IF NOT EXISTS articles (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                "unread BOOLEAN, " +
                "marked BOOLEAN, " +
                "published BOOLEAN, " +
                "score INTEGER, " +
                "updated INTEGER, " +
                "is_updated BOOLEAN, " +
                "title TEXT, " +
                "link TEXT, " +
                "feed_id INTEGER, " +
                "tags TEXT, " +
                "content TEXT, " +
                "author TEXT, " +
                "selected BOOLEAN, " +
                "modified BOOLEAN," +
				"modified_published BOOLEAN," +
				"modified_marked BOOLEAN" +
                ");");
		
		db.execSQL("CREATE TRIGGER articles_set_modified UPDATE OF marked, published, unread ON articles " +
		"BEGIN " +
		" UPDATE articles SET modified = 1 WHERE " + BaseColumns._ID + " = " + "OLD." + BaseColumns._ID + "; " +
		"END;");
		
		db.execSQL("CREATE VIEW feeds_unread AS SELECT feeds."+BaseColumns._ID+" AS "+BaseColumns._ID+", " +
				"feeds.title AS title, " +
				"cat_id, " +
				"SUM(articles.unread) AS unread FROM feeds " +
				"LEFT JOIN articles ON (articles.feed_id = feeds."+BaseColumns._ID+") " +
				"GROUP BY feeds."+BaseColumns._ID+", feeds.title;");
		
		//sqlite> select categories._id,categories.title,sum(articles.unread) from categories left j
		//oin feeds on (feeds.cat_id = categories._id) left join articles on (articles.feed_id = fee
		//ds._id) group by categories._id;
		
		db.execSQL("CREATE VIEW cats_unread AS SELECT categories."+BaseColumns._ID+" AS "+BaseColumns._ID+", " +
				"categories.title AS title, " +
				"SUM(articles.unread) AS unread FROM categories " +
				"LEFT JOIN feeds ON (feeds.cat_id = categories."+BaseColumns._ID+") "+
				"LEFT JOIN articles ON (articles.feed_id = feeds."+BaseColumns._ID+") " +
				"GROUP BY categories."+BaseColumns._ID+", categories.title;");

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onCreate(db);
	}

	public boolean hasPendingOfflineData() {
		try {
			Cursor c = getReadableDatabase().query("articles",
					new String[] { "COUNT(*)" }, "modified = 1", null, null, null,
					null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();

				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed? ugh
		}

		return false;
	}

	public boolean hasOfflineData() {
		try {
			Cursor c = getReadableDatabase().query("articles",
					new String[] { "COUNT(*)" }, null, null, null, null, null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();

				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed?
		}

		return false;
	}

}
