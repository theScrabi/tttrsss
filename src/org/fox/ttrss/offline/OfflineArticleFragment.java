package org.fox.ttrss.offline;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.widget.TextView;

public class OfflineArticleFragment extends Fragment {
	@SuppressWarnings("unused")
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private Cursor m_cursor;
	private OfflineServices m_offlineServices;
	
	public OfflineArticleFragment() {
		super();
	}
	
	public OfflineArticleFragment(int articleId) {
		super();
		m_articleId = articleId;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		menu.setHeaderTitle(m_cursor.getString(m_cursor.getColumnIndex("title")));
		
		super.onCreateContextMenu(menu, v, menuInfo);	
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);

		m_cursor = m_offlineServices.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
				new String[] { "articles.*", "feeds.title AS feed_title" }, "articles." + BaseColumns._ID + "=?", 
				new String[] { String.valueOf(m_articleId) }, null, null, null);

		m_cursor.moveToFirst();
		
		if (m_cursor.isFirst()) {
			
			TextView title = (TextView)view.findViewById(R.id.title);
			
			if (title != null) {
				
				String titleStr;
				
				if (m_cursor.getString(m_cursor.getColumnIndex("title")).length() > 200)
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title")).substring(0, 200) + "...";
				else
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title"));
				
				title.setMovementMethod(LinkMovementMethod.getInstance());
				title.setText(Html.fromHtml("<a href=\""+m_cursor.getString(m_cursor.getColumnIndex("link")).trim().replace("\"", "\\\"")+"\">" + titleStr + "</a>"));
				registerForContextMenu(title);
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				String content;
				String cssOverride = "";
				
				WebSettings ws = web.getSettings();
				ws.setSupportZoom(true);
				ws.setBuiltInZoomControls(true);
				
				web.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);

				TypedValue tv = new TypedValue();				
			    getActivity().getTheme().resolveAttribute(R.attr.linkColor, tv, true);
			    
			    // prevent flicker in ics
			    if (android.os.Build.VERSION.SDK_INT >= 11) {
			    	web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			    }

				if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
					cssOverride = "body { background : transparent; color : #e0e0e0}";
					//view.setBackgroundColor(android.R.color.black);
					web.setBackgroundColor(getResources().getColor(android.R.color.transparent));
				} else {
					cssOverride = "";
				}

				String hexColor = String.format("#%06X", (0xFFFFFF & tv.data));
			    cssOverride += " a:link {color: "+hexColor+";} a:visited { color: "+hexColor+";}";
				
				String articleContent = m_cursor.getString(m_cursor.getColumnIndex("content"));
				Document doc = Jsoup.parse(articleContent);
					
				if (doc != null) {
					if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {
						
						Elements images = doc.select("img");
						
						for (Element img : images) {
							String url = img.attr("src");
							
							if (ImageCacheService.isUrlCached(url)) {						
								img.attr("src", "file://" + ImageCacheService.getCacheFileName(url));
							}						
						}
					}
					
					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();
					
					articleContent = doc.toString();
				}
				
				view.findViewById(R.id.attachments_holder).setVisibility(View.GONE);
				
				String align = m_prefs.getBoolean("justify_article_text", true) ? "text-align : justified" : "";
				
				switch (Integer.parseInt(m_prefs.getString("font_size", "0"))) {
				case 0:
					cssOverride += "body { "+align+"; font-size : 14px; } ";
					break;
				case 1:
					cssOverride += "body { "+align+"; font-size : 18px; } ";
					break;
				case 2:
					cssOverride += "body { "+align+"; font-size : 21px; } ";
					break;		
				}
				
				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					"<style type=\"text/css\">" +
					"body { padding : 0px; margin : 0px; }" +
					cssOverride +
					"</style>" +
					"</head>" +
					"<body>" + articleContent + "</body></html>";
					
				try {
					web.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
			
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				Date d = new Date(m_cursor.getInt(m_cursor.getColumnIndex("updated")) * 1000L);
				SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy, HH:mm");
				dv.setText(df.format(d));
			}
			
			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				int feedTitleIndex = m_cursor.getColumnIndex("feed_title");

				if (feedTitleIndex != -1 && m_offlineServices.activeFeedIsCat()) {
					tagv.setText(m_cursor.getString(feedTitleIndex));
				} else {				
					String tagsStr = m_cursor.getString(m_cursor.getColumnIndex("tags"));
					tagv.setText(tagsStr);
				}
			}			
		} 
		
		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();	
		
		m_cursor.close();
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putInt("articleId", m_articleId);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		
		m_offlineServices = (OfflineServices)activity;
	}


}
