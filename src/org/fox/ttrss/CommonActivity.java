package org.fox.ttrss;

import org.fox.ttrss.util.DatabaseHelper;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class CommonActivity extends ActionBarActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";

	public final static String THEME_DARK = "THEME_DARK";
	public final static String THEME_LIGHT = "THEME_LIGHT";
	public final static String THEME_SEPIA = "THEME_SEPIA";
	public final static String THEME_HOLO = "THEME_HOLO";
	public final static String THEME_DEFAULT = CommonActivity.THEME_LIGHT;
	
	public static final int EXCERPT_MAX_SIZE = 200;
	
	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode = true;
	private boolean m_compatMode = false;
	private String m_theme;

	protected SharedPreferences m_prefs;

	protected void setSmallScreen(boolean smallScreen) {
		Log.d(TAG, "m_smallScreenMode=" + smallScreen);
		m_smallScreenMode = smallScreen;
	}
	
	public boolean getUnreadOnly() {
		return m_prefs.getBoolean("show_unread_only", true);
	}

	public void setUnreadOnly(boolean unread) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("show_unread_only", unread);
		editor.commit();
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
	public void onResume() {
		super.onResume();
	
		if (!m_theme.equals(m_prefs.getString("theme", CommonActivity.THEME_DEFAULT))) {
			Log.d(TAG, "theme changed, restarting");
			
			finish();
			startActivity(getIntent());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_readableDb.close();
		m_writableDb.close();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		
		if (savedInstanceState != null) {
			m_theme = savedInstanceState.getString("theme");
		} else {		
			m_theme = m_prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		}
		
		initDatabase();
		
		m_compatMode = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB;

		Log.d(TAG, "m_compatMode=" + m_compatMode);
		
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("theme", m_theme);
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

	@SuppressLint({ "NewApi", "ServiceCast" })
	@SuppressWarnings("deprecation")
	public void copyToClipboard(String str) {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {				
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		} else {
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		}		

		Toast toast = Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT);
		toast.show();
	}

	public boolean isDarkTheme() {
		String theme = m_prefs.getString("theme", THEME_DEFAULT);
		
		return theme.equals(THEME_DARK) || theme.equals(THEME_HOLO);
	}
	
	protected void setAppTheme(SharedPreferences prefs) {
		String theme = prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		
		if (theme.equals(THEME_DARK)) {
			setTheme(R.style.DarkTheme);
		} else if (theme.equals(THEME_SEPIA)) {
			setTheme(R.style.SepiaTheme);
		} else if (theme.equals(THEME_HOLO)) {
			setTheme(R.style.HoloTheme);
		} else {
			setTheme(R.style.LightTheme);
		}
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	protected int getScreenWidthInPixel() {
	    Display display = getWindowManager().getDefaultDisplay();

	    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
	        Point size = new Point();
	        display.getSize(size);
	        int width = size.x;
	        return width;       
	    } else {
	        return display.getWidth();
	    }
	}
	
}
