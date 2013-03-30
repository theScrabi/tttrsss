package org.fox.ttrss.offline;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.TextView;

public class OfflineArticleFragment extends Fragment implements GestureDetector.OnDoubleTapListener {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private boolean m_isCat = false; // FIXME use
	private Cursor m_cursor;
	private OfflineActivity m_activity;
	private GestureDetector m_detector;
	
	public OfflineArticleFragment() {
		super();
	}
	
	public OfflineArticleFragment(int articleId) {
		super();
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
		
		getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		menu.setHeaderTitle(m_cursor.getString(m_cursor.getColumnIndex("title")));
		
		super.onCreateContextMenu(menu, v, menuInfo);	
		
	}
	
	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);

		m_cursor = m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
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
				
				final String link = m_cursor.getString(m_cursor.getColumnIndex("link"));
				
				title.setText(titleStr);
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						try {
							Intent intent = new Intent(Intent.ACTION_VIEW, 
									Uri.parse(link.trim()));
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
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				
				web.setWebChromeClient(new WebChromeClient() {					
					@Override
	                public void onProgressChanged(WebView view, int progress) {
	                	m_activity.setProgress(Math.round(((float)progress / 100f) * 10000));
	                	if (progress == 100) {
	                		m_activity.setProgressBarVisibility(false);
	                	}
	                }
				});
				
				web.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						return m_detector.onTouchEvent(event);
					}
				});
				
				String content;
				String cssOverride = "";
				
				WebSettings ws = web.getSettings();
				ws.setSupportZoom(true);
				ws.setBuiltInZoomControls(false);
				
				web.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);

				TypedValue tv = new TypedValue();				
			    getActivity().getTheme().resolveAttribute(R.attr.linkColor, tv, true);
			    
			    // prevent flicker in ics
			    if (android.os.Build.VERSION.SDK_INT >= 11) {
			    	web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			    }

				if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
					cssOverride = "body { background : transparent; color : #e0e0e0}";
				} else if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK_GRAY")) {
					cssOverride = "body { background : transparent; color : #e0e0e0}";
				} else {
					cssOverride = "body { background : transparent; }";
				}
				web.setBackgroundColor(getResources().getColor(android.R.color.transparent));
				
				String hexColor = String.format("#%06X", (0xFFFFFF & tv.data));
			    cssOverride += " a:link {color: "+hexColor+";} a:visited { color: "+hexColor+";}";
				
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
					"<body>" + articleContent + "<p>&nbsp;</p><p>&nbsp;</p></body></html>";;
					
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

				if (feedTitleIndex != -1 && m_isCat) {
					tagv.setText(m_cursor.getString(feedTitleIndex));
				} else {				
					String tagsStr = m_cursor.getString(m_cursor.getColumnIndex("tags"));
					tagv.setText(tagsStr);
				}
			}	
			
			TextView author = (TextView)view.findViewById(R.id.author);

			if (author != null) {
				author.setVisibility(View.GONE);
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
		
		m_detector = new GestureDetector(m_activity, new GestureDetector.OnGestureListener() {			
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public void onShowPress(MotionEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
					float distanceY) {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public void onLongPress(MotionEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
					float velocityY) {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean onDown(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}
		});
		
		m_detector.setOnDoubleTapListener(this);
	}

	@Override
	public boolean onDoubleTap(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	private void onLeftSideTapped() {
		OfflineArticlePager ap = (OfflineArticlePager) m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE);
		
		if (ap != null && ap.isAdded()) {
			ap.selectArticle(false);
		}
	}
	
	private void onRightSideTapped() {
		OfflineArticlePager ap = (OfflineArticlePager) m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE);
		
		if (ap != null && ap.isAdded()) {
			ap.selectArticle(true);
		}
	}
	
	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		
		int width = getView().getWidth();		
		int x = Math.round(e.getX());
		
		if (x <= width/15) {
			onLeftSideTapped();
			return true;
		} else if (x >= width-(width/15)) {
			onRightSideTapped();
			return true;
		} /* else if (!m_activity.isCompatMode()) {
			ActionBar bar = m_activity.getActionBar();
			
			if (bar.isShowing()) {
				bar.hide();
			} else {
				bar.show();
			}
		} */
		
		return false;
	}


}
