package org.fox.ttrss;

import android.animation.LayoutTransition;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class TransitionHelper {
	public TransitionHelper(ViewGroup layout) {
		LayoutTransition transitioner = new LayoutTransition();
		layout.setLayoutTransition(transitioner);
	}
}
