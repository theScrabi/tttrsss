package org.fox.ttrss;

import org.fox.ttrss.util.DatabaseHelper;

import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class CommonActivity extends FragmentActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";
	
	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode = true;
	private boolean m_compatMode = false;

	protected void setSmallScreen(boolean smallScreen) {
		Log.d(TAG, "m_smallScreenMode=" + smallScreen);
		m_smallScreenMode = smallScreen;
	}
	
	public boolean getUnreadArticlesOnly() {
		return GlobalState.getInstance().m_unreadArticlesOnly;
	}
	
	public boolean getUnreadOnly() {
		return GlobalState.getInstance().m_unreadOnly;
	}
	
	public void setUnreadOnly(boolean unread) {
		GlobalState.getInstance().m_unreadOnly = unread;
	}

	public void setUnreadArticlesOnly(boolean unread) {
		GlobalState.getInstance().m_unreadArticlesOnly = unread;
	}
	
	public void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(status);
		}
		
		findViewById(R.id.loading_container).setVisibility(status == R.string.blank ? View.GONE : View.VISIBLE);
		
		setProgressBarIndeterminateVisibility(showProgress);
	}
	
	public void toast(int msgId) {
		Toast toast = Toast.makeText(CommonActivity.this, msgId, Toast.LENGTH_SHORT);
		toast.show();
	}

	public void toast(String msg) {
		Toast toast = Toast.makeText(CommonActivity.this, msg, Toast.LENGTH_SHORT);
		toast.show();
	}

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

		Log.d(TAG, "m_compatMode=" + m_compatMode);
		
		super.onCreate(savedInstanceState);
	}
	
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}
	
	public boolean isCompatMode() {
		return m_compatMode;
	}

	@SuppressWarnings("deprecation")
	public boolean isPortrait() {
		Display display = getWindowManager().getDefaultDisplay(); 
		
	    int width = display.getWidth();
	    int height = display.getHeight();
		
	    return width < height;
	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public void copyToClipboard(String str) {
		if (android.os.Build.VERSION.SDK_INT < 11) {				
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		} else {
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		}		

		Toast toast = Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT);
		toast.show();
	}

}
