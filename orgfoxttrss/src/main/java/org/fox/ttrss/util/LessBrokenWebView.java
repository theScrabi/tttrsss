package org.fox.ttrss.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

public class LessBrokenWebView extends WebView {

	public LessBrokenWebView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public LessBrokenWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public LessBrokenWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			int temp_ScrollY = getScrollY();
			scrollTo(getScrollX(), getScrollY() + 1);
			scrollTo(getScrollX(), temp_ScrollY);
		}

		return super.onTouchEvent(event);
	}

}
