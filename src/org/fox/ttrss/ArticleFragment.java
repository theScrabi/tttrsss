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
	protected int m_articleId;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);

		Log.d(TAG, "Opening article #" + m_articleId);
		
		Cursor c = ((MainActivity)getActivity()).getReadableDb().query("articles", null, BaseColumns._ID + "=?", 
				new String[] { String.valueOf(m_articleId) }, null, null, null);
		
		c.moveToFirst();
		
		Log.d(TAG, "Cursor count: " + c.getCount());
		
		TextView title = (TextView)view.findViewById(R.id.title);
		
		if (title != null) {
			title.setText(c.getString(c.getColumnIndex("title")));
		}

		WebView content = (WebView)view.findViewById(R.id.content);
		
		if (content != null) {
			String contentData = "<html><body>" + c.getString(c.getColumnIndex("content")) + "</body></html>";
			
			Log.d(TAG, "content=" + contentData);
			
			 content.loadData(contentData, "text/html", "utf-8");
		}
		
		c.close();
		
		return view;    	
	}

	public void initialize(int articleId) {
		m_articleId = articleId;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putInt("articleId", m_articleId);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
	}

}
