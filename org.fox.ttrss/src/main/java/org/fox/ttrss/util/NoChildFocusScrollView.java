package org.fox.ttrss.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

public class NoChildFocusScrollView extends NotifyingScrollView {

	public NoChildFocusScrollView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}


	public NoChildFocusScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public NoChildFocusScrollView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	@Override 
	public void requestChildFocus(View child, View focused) { 
	    if (focused instanceof WebView ) 
	       return;
	    super.requestChildFocus(child, focused);
	}
}
