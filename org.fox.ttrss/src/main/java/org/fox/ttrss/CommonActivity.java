package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.fox.ttrss.util.DatabaseHelper;

public class CommonActivity extends ActionBarActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";

	public final static String THEME_DARK = "THEME_DARK";
	public final static String THEME_LIGHT = "THEME_LIGHT";
	public final static String THEME_SEPIA = "THEME_SEPIA";
    public final static String THEME_AMBER = "THEME_AMBER";
	public final static String THEME_DEFAULT = CommonActivity.THEME_LIGHT;

	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode = true;
	private boolean m_compatMode = false;
	private String m_theme;
    private boolean m_fullScreen;

	protected SharedPreferences m_prefs;

	/* protected void enableHttpCaching() {
	   // enable resource caching
	   if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            try {
            	File httpCacheDir = new File(getApplicationContext().getCacheDir(), "http");
            	long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            	HttpResponseCache.install(httpCacheDir, httpCacheSize);
            } catch (IOException e) {
            	e.printStackTrace();
            }        
        }
	} */
	
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
	
		if (!m_theme.equals(m_prefs.getString("theme", CommonActivity.THEME_DEFAULT)) ||
                m_fullScreen != m_prefs.getBoolean("full_screen_mode", false)) {

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
            m_fullScreen = savedInstanceState.getBoolean("fullscreen");
		} else {		
			m_theme = m_prefs.getString("theme", CommonActivity.THEME_DEFAULT);
            m_fullScreen = m_prefs.getBoolean("full_screen_mode", false);
		}
		
		initDatabase();
				
		m_compatMode = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB;

		Log.d(TAG, "m_compatMode=" + m_compatMode);
		
		super.onCreate(savedInstanceState);
	}

    public int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float)dp * density);
    }

	public void setStatusBarTint() {
		if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.KITKAT &&
                !m_prefs.getBoolean("full_screen_mode", false)) {

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

			SystemBarTintManager tintManager = new SystemBarTintManager(this);
		    // enable status bar tint
		    tintManager.setStatusBarTintEnabled(true);

		    TypedValue tv = new TypedValue();
		    getTheme().resolveAttribute(R.attr.statusBarHintColor, tv, true);
		    
		    tintManager.setStatusBarTintColor(tv.data);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("theme", m_theme);
        out.putBoolean("fullscreen", m_fullScreen);
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
		
		return theme.equals(THEME_DARK) || theme.equals(THEME_AMBER);
	}
	
	protected void setAppTheme(SharedPreferences prefs) {
		String theme = prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		
		if (theme.equals(THEME_DARK)) {
            setTheme(R.style.DarkTheme);
        } else if (theme.equals(THEME_AMBER)) {
            setTheme(R.style.AmberTheme);
		} else if (theme.equals(THEME_SEPIA)) {
			setTheme(R.style.SepiaTheme);
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
