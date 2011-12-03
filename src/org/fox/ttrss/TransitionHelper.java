package org.fox.ttrss;

import android.animation.LayoutTransition;
import android.widget.LinearLayout;

public class TransitionHelper {
	public TransitionHelper(LinearLayout layout) {
		LayoutTransition transitioner = new LayoutTransition();
		layout.setLayoutTransition(transitioner);
	}
}
