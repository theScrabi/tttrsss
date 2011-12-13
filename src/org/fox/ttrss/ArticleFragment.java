package org.fox.ttrss;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.OnlineServices.RelativeArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

public class ArticleFragment extends Fragment {
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private Article m_article;
	private OnlineServices m_onlineServices;
	//private Article m_nextArticle;
	//private Article m_prevArticle;

	public ArticleFragment() {
		super();
	}
	
	public ArticleFragment(Article article) {
		super();
		
		m_article = article;
	}
	
	private View.OnTouchListener m_gestureListener;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);
		
		Activity activity = (Activity)getActivity();
		
		if (activity != null) {		
			int orientation = activity.getWindowManager().getDefaultDisplay().getOrientation();
			
			if (!m_onlineServices.isSmallScreen()) {			
				if (orientation % 2 == 0) {
					view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
				} else {
					view.findViewById(R.id.splitter_vertical).setVisibility(View.GONE);
				}
			} else {
				view.findViewById(R.id.splitter_vertical).setVisibility(View.GONE);
				view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
			}
		} else {
			view.findViewById(R.id.splitter_horizontal).setVisibility(View.GONE);
		}
		
		if (m_article != null) {
			
			TextView title = (TextView)view.findViewById(R.id.title);
			
			if (title != null) {
				
				String titleStr;
				
				if (m_article.title.length() > 200)
					titleStr = m_article.title.substring(0, 200) + "...";
				else
					titleStr = m_article.title;
				
				title.setMovementMethod(LinkMovementMethod.getInstance());
				title.setText(Html.fromHtml("<a href=\""+m_article.link.replace("\"", "\\\"")+"\">" + titleStr + "</a>"));
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				String content;
				String cssOverride = "";
				
				
				//WebSettings ws = web.getSettings();
				//ws.setSupportZoom(true);
				//ws.setBuiltInZoomControls(true);

				if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
					cssOverride = "body { background : black; color : #e0e0e0}\n";			
					web.setBackgroundColor(android.R.color.black);
				} else {
					cssOverride = "";
				}

				String articleContent = m_article.content != null ? m_article.content : "";
				
				Document doc = Jsoup.parse(articleContent);
				
				if (doc != null) {
					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();
					
					articleContent = doc.toString();
				}
				
				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					//"<meta name=\"viewport\" content=\"target-densitydpi=device-dpi\" />" +
					"<style type=\"text/css\">" +
					cssOverride +
					"img { max-width : 98%; height : auto; }" +
					"body { text-align : justify; }" +
					"</style>" +
					"</head>" +
					"<body>" + articleContent + "</body></html>";
					
				try {
					web.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
				if (m_onlineServices.isSmallScreen())
					web.setOnTouchListener(m_gestureListener);
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date(m_article.updated * 1000L);
				SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy, HH:mm");
				dv.setText(df.format(d));
			}
			
			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				if (m_article.tags != null) {
					String tagsStr = "";
				
					for (String tag : m_article.tags)
						tagsStr += tag + ", ";
					
					tagsStr = tagsStr.replaceAll(", $", "");
				
					tagv.setText(tagsStr);
				} else {
					tagv.setVisibility(View.GONE);
				}
			}			
			
			AdView av = (AdView)view.findViewById(R.id.ad);
			
			if (av != null) {
				if (!m_onlineServices.getLicensed()) {
					AdRequest request = new AdRequest();
					request.addTestDevice(AdRequest.TEST_EMULATOR);
					av.loadAd(request);
				} else {
					av.setVisibility(View.GONE);
				}
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

		out.putParcelable("article", m_article);
	}

	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_onlineServices = (OnlineServices)activity;
		//m_article = m_onlineServices.getSelectedArticle(); 
	}

}
