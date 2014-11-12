package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.widget.TextView;

import com.shamanland.fab.ShowHideOnScroll;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.util.TypefaceCache;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ArticleFragment extends Fragment  {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private Article m_article;
	private OnlineActivity m_activity;
	
	public void initialize(Article article) {
		m_article = article;
	}

	//private View.OnTouchListener m_gestureListener;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		if (v.getId() == R.id.article_content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {

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

		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
		}

		boolean useTitleWebView = m_prefs.getBoolean("article_compat_view", false);
		
		View view = inflater.inflate(useTitleWebView ? R.layout.article_fragment_compat : R.layout.article_fragment, container, false);
		
		if (m_article != null) {

            View scrollView = view.findViewById(R.id.article_scrollview);
            View fab = view.findViewById(R.id.article_fab);

            if (scrollView != null && fab != null) {
                if (m_prefs.getBoolean("enable_article_fab", true)) {
                    scrollView.setOnTouchListener(new ShowHideOnScroll(fab));

                    fab.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                URL url = new URL(m_article.link.trim());
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
                } else {
                    fab.setVisibility(View.GONE);
                }
            }


			/* if (!useTitleWebView) {
				View scroll = view.findViewById(R.id.article_scrollview);

				if (scroll != null) {
					final float scale = getResources().getDisplayMetrics().density;
					
					if (m_activity.isSmallScreen()) {
						scroll.setPadding((int)(8 * scale + 0.5f),
								(int)(5 * scale + 0.5f),
								(int)(8 * scale + 0.5f),
								0);
					} else {
						scroll.setPadding((int)(25 * scale + 0.5f),
								(int)(10 * scale + 0.5f),
								(int)(25 * scale + 0.5f),
								0);

					}
					
				}
			} */
			
			int articleFontSize = Integer.parseInt(m_prefs.getString("article_font_size_sp", "16"));
			int articleSmallFontSize = Math.max(10, Math.min(18, articleFontSize - 2));
			
			TextView title = (TextView)view.findViewById(R.id.title);
						
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
				
				if (m_article.title.length() > 200)
					titleStr = m_article.title.substring(0, 200) + "...";
				else
					titleStr = m_article.title;
								
				title.setText(Html.fromHtml(titleStr));
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						try {
							URL url = new URL(m_article.link.trim());
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
				if (m_activity.getApiLevel() >= 4 && m_article.comments_count > 0) {
					comments.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
					
					String commentsTitle = getString(R.string.article_comments, m_article.comments_count);
					comments.setText(commentsTitle);
					//comments.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
					comments.setOnClickListener(new OnClickListener() {					
						@Override
						public void onClick(View v) {
							try {
								URL url = new URL((m_article.comments_link != null && m_article.comments_link.length() > 0) ?
									m_article.comments_link : m_article.link);
								String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
										url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
								Intent intent = new Intent(Intent.ACTION_VIEW, 
										Uri.parse(uri));
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
			
			TextView note = (TextView)view.findViewById(R.id.note);
			
			if (note != null) {
				if (m_article.note != null && !"".equals(m_article.note)) {
					note.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
					note.setText(m_article.note);					
				} else {
					note.setVisibility(View.GONE);
				}
				
			}
			
			final WebView web = (WebView)view.findViewById(R.id.article_content);
			
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
				
			    // prevent flicker in ics
			    if (!m_prefs.getBoolean("webview_hardware_accel", true) || useTitleWebView) {
			    	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			    		web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			    	}
			    }

				String content;
				String cssOverride = "";
				
				WebSettings ws = web.getSettings();
				ws.setSupportZoom(false);

				TypedValue tvBackground = new TypedValue();
			    getActivity().getTheme().resolveAttribute(R.attr.articleBackground, tvBackground, true);

                String backgroundHexColor = String.format("#%06X", (0xFFFFFF & tvBackground.data));

                cssOverride = "body { background : "+ backgroundHexColor+"; }";

                if (m_activity.isDarkTheme()) {
                    cssOverride += "body { color : #e0e0e0; }";
                }

                TypedValue tvLinkColor = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.linkColor, tvLinkColor, true);

				String linkHexColor = String.format("#%06X", (0xFFFFFF & tvLinkColor.data));
			    cssOverride += " a:link {color: "+linkHexColor+";} a:visited { color: "+linkHexColor+";}";

				String articleContent = m_article.content != null ? m_article.content : "";
				
				Document doc = Jsoup.parse(articleContent);
				
				if (doc != null) {
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
					"body { padding : 0px; margin : 0px; line-height : 130%; }" +
					"img { max-width : 100%; width : auto; height : auto; }" +
                    " table { width : 100%; }" +
					cssOverride +
					"</style>" +
					"</head>" +
					"<body>" + articleContent;
				
				if (useTitleWebView) {
					content += "<p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p>";
				}
				
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
				
				content += "</body></html>";
					
				try {
					String baseUrl = null;
					
					try {
						URL url = new URL(m_article.link);
						baseUrl = url.getProtocol() + "://" + url.getHost();
					} catch (MalformedURLException e) {
						//
					}
					
					web.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
//				if (m_activity.isSmallScreen())
//					web.setOnTouchListener(m_gestureListener);
				
				web.setVisibility(View.VISIBLE);
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				Date d = new Date(m_article.updated * 1000L);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				dv.setText(df.format(d));
			}

			TextView author = (TextView)view.findViewById(R.id.author);

			boolean hasAuthor = false;
			
			if (author != null) {
				author.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				if (m_article.author != null && m_article.author.length() > 0) {
					author.setText(getString(R.string.author_formatted, m_article.author));				
				} else {
					author.setVisibility(View.GONE);
				}
				hasAuthor = true;
			}

			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				if (m_article.feed_title != null) {
					String fTitle = m_article.feed_title;
					
					if (!hasAuthor && m_article.author != null && m_article.author.length() > 0) {
						fTitle += " (" + getString(R.string.author_formatted, m_article.author) + ")";						
					}
					
					tagv.setText(fTitle);
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

		out.setClassLoader(getClass().getClassLoader());
		out.putParcelable("article", m_article);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_activity = (OnlineActivity)activity;
		//m_article = m_onlineServices.getSelectedArticle(); 
		
	}
}
