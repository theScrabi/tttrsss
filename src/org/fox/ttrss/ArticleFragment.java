package org.fox.ttrss;

import java.sql.SQLData;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

public class ArticleFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	protected SharedPreferences m_prefs;
	
	private int m_articleId;
	private String m_sessionId;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);
		
		return view;    	
	}

	public void initialize(String sessionId, int articleId, SharedPreferences prefs) {
		m_articleId = articleId;
		m_prefs = prefs;
		m_sessionId = sessionId;
		
		Log.d(TAG, "Article: " + articleId);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
		out.putInt("articleId", m_articleId);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
	}

}
