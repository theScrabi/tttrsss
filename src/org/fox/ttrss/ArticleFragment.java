package org.fox.ttrss;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

public class ArticleFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	protected SharedPreferences m_prefs;
	
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
				title.setMovementMethod(LinkMovementMethod.getInstance());
				title.setText(Html.fromHtml("<a href=\""+m_article.link.replace("\"", "\\\"")+"\">" + m_article.title + "</a>"));
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				// this is ridiculous
				// TODO white on black style for dark theme
				String content;
				try {
					content = URLEncoder.encode("<html>" +
						"<head>" +
						"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" + // wtf, google?
						"<style type=\"text/css\">img { max-width : 90%; }</style>" +
						"</head>" +
						"<body>" + m_article.content + "</body></html>", "utf-8").replace('+', ' ');
				} catch (UnsupportedEncodingException e) {
					content = getString(R.string.could_not_decode_content);
					e.printStackTrace();
				}
				
				web.loadData(content, "text/html", "utf-8");
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date(m_article.updated * 1000L);
				SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy, HH:mm");
				dv.setText(df.format(d));
			}
			
			TextView cv = (TextView)view.findViewById(R.id.comments);
			
			// comments are not currently returned by the API
			if (cv != null) {
				cv.setVisibility(View.GONE);
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
