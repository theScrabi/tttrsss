package org.fox.ttrss;

import java.net.URLEncoder;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

public class ArticleFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	protected SharedPreferences m_prefs;
	
	//private int m_articleId;
	private String m_sessionId;
	private Article m_article;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			//m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);
		
		if (m_article != null) {
			
			TextView title = (TextView)view.findViewById(R.id.title);
			
			if (title != null) {
				title.setText(m_article.title);
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				// this is ridiculous
				String content = URLEncoder.encode("<html>" +
					"<head><style type=\"text/css\">img { max-width : 90%; }</style></head>" +
					"<body>" + m_article.content + "</body></html>").replace('+', ' ');
				
				web.loadData(content, "text/html", "utf-8");
			}
		} 
		
		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();		
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putString("sessionId", m_sessionId);
		//out.putInt("articleId", m_articleId);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_sessionId = ((MainActivity)activity).getSessionId();
		m_article = ((MainActivity)activity).getSelectedArticle(); 
		
		//m_prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

	}

}
