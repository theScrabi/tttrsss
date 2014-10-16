package org.fox.ttrss.share;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

public class CommonActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();
	
	private boolean m_smallScreenMode = true;
	private boolean m_compatMode = false;

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_compatMode = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB;

		Log.d(TAG, "m_compatMode=" + m_compatMode);
		
		super.onCreate(savedInstanceState);
	}

	public boolean isSmallScreen() {
		return m_smallScreenMode;
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
	

}
