package org.fox.ttrss.util;

import java.util.Hashtable;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

public class TypefaceCache {
	private static final String TAG = "TypefaceCache";	
	private static final Hashtable<String, Typeface> cache = new Hashtable<String, Typeface>();

	public static Typeface get(Context c, String typefaceName, int style) {
		synchronized (cache) {
			String key = typefaceName + ":" + style;
			
			if (!cache.containsKey(key)) {
				try {
					Typeface t = Typeface.create(typefaceName, style);
					cache.put(key, t);
				} catch (Exception e) {
					Log.e(TAG, "Could not get typeface '" + typefaceName + "' because " + e.getMessage());
					return null;
				}
			}
			return cache.get(key);
		}
	}
}