package org.fox.ttrss;

import org.fox.ttrss.util.DatabaseHelper;

import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class CommonActivity extends FragmentActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";
	
	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode;
	private boolean m_compatMode = false;

	private void initDatabase() {
		DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
		
		m_writableDb = dh.getWritableDatabase();
		m_readableDb = dh.getReadableDatabase();
	}
	
	public synchronized SQLiteDatabase getReadableDb() {
		return m_readableDb;
	}

	public synchronized SQLiteDatabase getWritableDb() {
		return m_writableDb;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_readableDb.close();
		m_writableDb.close();

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		initDatabase();
		
		m_compatMode = android.os.Build.VERSION.SDK_INT <= 10;

		m_smallScreenMode = m_compatMode || (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) != 
				Configuration.SCREENLAYOUT_SIZE_XLARGE;
		
		Log.d(TAG, "m_smallScreenMode=" + m_smallScreenMode);
		Log.d(TAG, "m_compatMode=" + m_compatMode);
		
		super.onCreate(savedInstanceState);
	}
	
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}

	public boolean isCompatMode() {
		return m_compatMode;
	}

	public int getOrientation() {
		return getWindowManager().getDefaultDisplay().getOrientation();
	}
		
}
