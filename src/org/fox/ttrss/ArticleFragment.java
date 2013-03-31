package org.fox.ttrss;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
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
import android.webkit.WebView.HitTestResult;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ArticleFragment extends Fragment implements GestureDetector.OnDoubleTapListener {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private Article m_article;
	private OnlineActivity m_activity;
	private GestureDetector m_detector;
	
	public ArticleFragment() {
		super();
	}
	
	public ArticleFragment(Article article) {
		super();
		
		m_article = article;
	}
	
	private View.OnTouchListener m_gestureListener;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		if (v.getId() == R.id.content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);
				
				/* FIXME I have no idea how to do this correctly ;( */
				
				m_activity.setLastContentImageHitTestUrl(result.getExtra());
				
			} else {
				menu.setHeaderTitle(m_article.title);
				getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
			}
		} else {
			menu.setHeaderTitle(m_article.title);
			getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		m_activity.setProgressBarVisibility(true);
		
		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);
		
		if (m_article != null) {
			
			TextView title = (TextView)view.findViewById(R.id.title);
			
			if (title != null) {
				
				String titleStr;
				
				if (m_article.title.length() > 200)
					titleStr = m_article.title.substring(0, 200) + "�";
				else
					titleStr = m_article.title;
				
				title.setText(Html.fromHtml(titleStr));
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						try {
							Intent intent = new Intent(Intent.ACTION_VIEW, 
									Uri.parse(m_article.link.trim()));
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
				if (m_activity.getApiLevel() >= 4 && m_article.comments_count > 0) {
					String commentsTitle = getString(R.string.article_comments, m_article.comments_count);
					comments.setText(commentsTitle);
					//comments.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
					comments.setOnClickListener(new OnClickListener() {					
						@Override
						public void onClick(View v) {
							try {
								String link = (m_article.comments_link != null && m_article.comments_link.length() > 0) ?
									m_article.comments_link : m_article.link; 
								
								Intent intent = new Intent(Intent.ACTION_VIEW, 
										Uri.parse(link.trim()));
									startActivity(intent);
							} catch (Exception e) {
								e.printStackTrace();
								m_activity.toast(R.string.error_other_error);
							}
						}
					});
					
				} else {
					comments.setVisibility(View.GONE);					
				}
			}
			
			WebView web = (WebView)view.findViewById(R.id.content);
			
			if (web != null) {
				registerForContextMenu(web);
				
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

				String articleContent = m_article.content != null ? m_article.content : "";
				
				Document doc = Jsoup.parse(articleContent);
				
				if (doc != null) {
					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();
					
					articleContent = doc.toString();
				}
				
				String align = m_prefs.getBoolean("justify_article_text", true) ? "text-align : justify;" : "";
				
				switch (Integer.parseInt(m_prefs.getString("font_size", "0"))) {
				case 0:
					cssOverride += "body { "+align+" font-size : 14px; } ";
					break;
				case 1:
					cssOverride += "body { "+align+" font-size : 18px; } ";
					break;
				case 2:
					cssOverride += "body { "+align+" font-size : 21px; } ";
					break;		
				}
				
				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					"<style type=\"text/css\">" +
					"body { padding : 0px; margin : 0px; }" +
					cssOverride +
					/* "img { max-width : 98%; height : auto; }" + */
					"</style>" +
					"</head>" +
					"<body>" + articleContent;
				
				if (m_article.attachments != null && m_article.attachments.size() != 0) {
					String flatContent = articleContent.replaceAll("[\r\n]", "");
					boolean hasImages = flatContent.matches(".*?<img[^>+].*?");
					
					for (Attachment a : m_article.attachments) {
						if (a.content_type != null && a.content_url != null) {							
							try {
								if (a.content_type.indexOf("image") != -1 && 
										(!hasImages || m_article.always_display_attachments)) {
									
									URL url = new URL(a.content_url.trim());
									String strUrl = url.toString().trim();

									content += "<p><img src=\"" + strUrl.replace("\"", "\\\"") + "\"></p>";
								}

							} catch (MalformedURLException e) {
								//
							} catch (Exception e) {
								e.printStackTrace();
							}
						}					
					}
				}
				
				content += "<p>&nbsp;</p><p>&nbsp;</p></body></html>";
					
				try {
					web.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
				if (m_activity.isSmallScreen())
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
				if (m_article.feed_title != null) {
					tagv.setText(m_article.feed_title);
				} else if (m_article.tags != null) {
					String tagsStr = "";
				
					for (String tag : m_article.tags)
						tagsStr += tag + ", ";
					
					tagsStr = tagsStr.replaceAll(", $", "");
				
					tagv.setText(tagsStr);
				} else {
					tagv.setVisibility(View.GONE);
				}
			}
			
			TextView author = (TextView)view.findViewById(R.id.author);

			if (author != null) {
				if (m_article.author != null && m_article.author.length() > 0) {
					author.setText(m_article.author);				
				} else {
					author.setVisibility(View.GONE);
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
		m_activity = (OnlineActivity)activity;
		//m_article = m_onlineServices.getSelectedArticle(); 
		
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
				m_activity.openContextMenu(getView());				
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
		ArticlePager ap = (ArticlePager) m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE);
		
		if (ap != null && ap.isAdded()) {
			ap.selectArticle(false);
		}
	}
	
	private void onRightSideTapped() {
		ArticlePager ap = (ArticlePager) m_activity.getSupportFragmentManager().findFragmentByTag(CommonActivity.FRAG_ARTICLE);
		
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
