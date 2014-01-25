package org.fox.ttrss.offline;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.fox.ttrss.util.TypefaceCache;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.widget.TextView;

public class OfflineArticleFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private boolean m_isCat = false; // FIXME use
	private Cursor m_cursor;
	private OfflineActivity m_activity;
	
	public void initialize(int articleId) {
		m_articleId = articleId;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		/* AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo(); */
		
		switch (item.getItemId()) {
		case R.id.article_link_share:
			m_activity.shareArticle(m_articleId);
			return true;
		case R.id.article_link_copy:
			if (true) {
				Cursor article = m_activity.getArticleById(m_articleId);
				
				if (article != null) {				
					m_activity.copyToClipboard(article.getString(article.getColumnIndex("link")));
					article.close();
				}
			}
			return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		//getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		//menu.setHeaderTitle(m_cursor.getString(m_cursor.getColumnIndex("title")));
		
		String title = m_cursor.getString(m_cursor.getColumnIndex("title"));
		
		if (v.getId() == R.id.content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);
				
				/* FIXME I have no idea how to do this correctly ;( */
				
				m_activity.setLastContentImageHitTestUrl(result.getExtra());
				
			} else {
				menu.setHeaderTitle(title);
				getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
			}
		} else {
			menu.setHeaderTitle(title);
			getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);	
		
	}
	
	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		boolean useTitleWebView = m_prefs.getBoolean("article_compat_view", false);
		
		View view = inflater.inflate(useTitleWebView ? R.layout.article_fragment_compat : R.layout.article_fragment, container, false);

		m_cursor = m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
				new String[] { "articles.*", "feeds.title AS feed_title" }, "articles." + BaseColumns._ID + "=?", 
				new String[] { String.valueOf(m_articleId) }, null, null, null);

		m_cursor.moveToFirst();
		
		if (m_cursor.isFirst()) {
			int articleFontSize = Integer.parseInt(m_prefs.getString("article_font_size_sp", "16"));
			int articleSmallFontSize = Math.max(10, Math.min(18, articleFontSize - 2));
			
			TextView title = (TextView)view.findViewById(R.id.title);

			final String link = m_cursor.getString(m_cursor.getColumnIndex("link"));
			
			if (title != null) {
				
				if (m_prefs.getBoolean("enable_condensed_fonts", false)) {
					Typeface tf = TypefaceCache.get(m_activity, "sans-serif-condensed", Typeface.NORMAL);
					
					if (tf != null && !tf.equals(title.getTypeface())) {
						title.setTypeface(tf);
					}
					
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, articleFontSize + 5));
				} else {
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, articleFontSize + 3));
				}
				
				String titleStr;
				
				if (m_cursor.getString(m_cursor.getColumnIndex("title")).length() > 200)
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title")).substring(0, 200) + "...";
				else
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title"));
								
				title.setText(titleStr);
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						try {
							URL url = new URL(link.trim());
							String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
								url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
							startActivity(intent);
						} catch (Exception e) {
							e.printStackTrace();
							m_activity.toast(R.string.error_other_error);
						}
					}
				});
				
				registerForContextMenu(title);
			}
			
			TextView comments = (TextView)view.findViewById(R.id.comments);
			
			if (comments != null) {
				comments.setVisibility(View.GONE);
			}
			
			TextView note = (TextView)view.findViewById(R.id.note);
			
			if (note != null) {
				note.setVisibility(View.GONE);
			}
			
			final WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				web.setOnLongClickListener(new View.OnLongClickListener() {					
					@Override
					public boolean onLongClick(View v) {
						HitTestResult result = ((WebView)v).getHitTestResult();

						if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
							registerForContextMenu(web);
							m_activity.openContextMenu(web);
							unregisterForContextMenu(web);
							return true;
						} else {
							if (m_activity.isCompatMode()) {
								KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
								shiftPressEvent.dispatch(web);
							}
							
							return false;
						}
					}
				});
				
				web.setWebChromeClient(new WebChromeClient() {					
					@Override
	                public void onProgressChanged(WebView view, int progress) {
	                	m_activity.setProgress(Math.round(((float)progress / 100f) * 10000));
	                	if (progress == 100) {
	                		m_activity.setProgressBarVisibility(false);
	                	}
	                }
				});
				
				String content;
				String cssOverride = "";
				
				WebSettings ws = web.getSettings();
				ws.setSupportZoom(false);

				TypedValue tv = new TypedValue();				
			    getActivity().getTheme().resolveAttribute(R.attr.linkColor, tv, true);
			    
			    // prevent flicker in ics
			    if (!m_prefs.getBoolean("webview_hardware_accel", true) || useTitleWebView) {
			    	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			    		web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			    	}
			    }
			    
			    String theme = m_prefs.getString("theme", "THEME_DARK");

				if ("THEME_DARK".equals(theme) || "THEME_SYSTEM".equals(theme)) {
					cssOverride = "body { background : transparent; color : #e0e0e0}";
				} else if ("THEME_DARK_GRAY".equals(theme)) {
					cssOverride = "body { background : transparent; color : #e0e0e0}";
				} else {
					cssOverride = "body { background : transparent; }";
				}

				if (useTitleWebView || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
					web.setBackgroundColor(Color.TRANSPARENT);
				} else {
					// seriously?
					web.setBackgroundColor(Color.argb(1, 0, 0, 0));
				}
				
				String hexColor = String.format("#%06X", (0xFFFFFF & tv.data));
			    cssOverride += " a:link {color: "+hexColor+";} a:visited { color: "+hexColor+";}";
				
			    cssOverride += " table { width : 100%; }";
			    
				String articleContent = m_cursor.getString(m_cursor.getColumnIndex("content"));
				Document doc = Jsoup.parse(articleContent);
					
				if (doc != null) {
					if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {
						
						Elements images = doc.select("img");
						
						for (Element img : images) {
							String url = img.attr("src");
							
							if (ImageCacheService.isUrlCached(m_activity, url)) {						
								img.attr("src", "file://" + ImageCacheService.getCacheFileName(m_activity, url));
							}						
						}
					}
					
					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();
					
					articleContent = doc.toString();
				}
				
				if (m_prefs.getBoolean("justify_article_text", true)) {
					cssOverride += "body { text-align : justify; } ";
				}
				
				ws.setDefaultFontSize(articleFontSize);
				
				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					"<meta name=\"viewport\" content=\"width=device-width, user-scalable=no\" />" +
					"<style type=\"text/css\">" +
					"body { padding : 0px; margin : 0px; line-height : 120%; }" +
					cssOverride +
					"img { max-width : 100%; width : auto; height : auto; }" +
					"</style>" +
					"</head>" +
					"<body>" + articleContent;
				
				if (useTitleWebView) {
					content += "<p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p>";
				}
				
				content += "</body></html>";
				
				try {
					String baseUrl = null;
					
					try {
						URL url = new URL(link);
						baseUrl = url.getProtocol() + "://" + url.getHost();
					} catch (MalformedURLException e) {
						//
					}
					
					web.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
			
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				Date d = new Date(m_cursor.getInt(m_cursor.getColumnIndex("updated")) * 1000L);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				dv.setText(df.format(d));
			}

			TextView author = (TextView)view.findViewById(R.id.author);

			boolean hasAuthor = false;
			
			if (author != null) {
				author.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				int authorIndex = m_cursor.getColumnIndex("author");
				if (authorIndex >= 0)
					author.setText(m_cursor.getString(authorIndex));
				else
					author.setVisibility(View.GONE);
				
				hasAuthor = true;
			}

			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);

				int feedTitleIndex = m_cursor.getColumnIndex("feed_title");

				if (feedTitleIndex != -1 /* && m_isCat */) {
					String fTitle = m_cursor.getString(feedTitleIndex);
					
					int authorIndex = m_cursor.getColumnIndex("author");
					
					if (!hasAuthor && authorIndex >= 0) {
						fTitle += " (" + getString(R.string.author_formatted, m_cursor.getString(authorIndex)) + ")";
					}
					
					tagv.setText(fTitle);
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

		m_activity = (OfflineActivity) activity;
		
	}
}
