package org.fox.ttrss;

import org.fox.ttrss.util.DatabaseHelper;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
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
	private boolean m_smallTablet = false;

	protected void setSmallScreen(boolean smallScreen) {
		Log.d(TAG, "m_smallScreenMode=" + smallScreen);
		m_smallScreenMode = smallScreen;
	}
	
	public void toast(int msgId) {
		Toast toast = Toast.makeText(CommonActivity.this, msgId, Toast.LENGTH_SHORT);
		toast.show();
	}

	public void toast(String msg) {
		Toast toast = Toast.makeText(CommonActivity.this, msg, Toast.LENGTH_SHORT);
		toast.show();
	}

	protected void detectSmallTablet() {

		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

		float inHeight = displayMetrics.heightPixels / displayMetrics.ydpi;
		float inWidth = displayMetrics.widthPixels / displayMetrics.xdpi;
		
		float inDiag = FloatMath.sqrt(inHeight * inHeight + inWidth * inWidth);
		
		if (inDiag < 9) {
			m_smallTablet = true;
		}
		
		Log.d(TAG, "m_smallTabletMode=" + m_smallTablet + " " + inDiag);
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
		
		detectSmallTablet();
		
		super.onCreate(savedInstanceState);
	}
	
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}
	
	public boolean isSmallTablet() {
		return m_smallTablet;
	}

	public boolean isCompatMode() {
		return m_compatMode;
	}

	public boolean isPortrait() {
		Display display = getWindowManager().getDefaultDisplay(); 
		
	    int width = display.getWidth();
	    int height = display.getHeight();
		
	    return width < height;
	}

	public void copyToClipboard(String str) {
		if (android.os.Build.VERSION.SDK_INT < 11) {				
			@SuppressWarnings("deprecation")
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
