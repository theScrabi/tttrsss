package org.fox.ttrss.offline;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shamanland.fab.ShowHideOnScroll;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.fox.ttrss.util.NotifyingScrollView;
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

public class OfflineArticleFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private boolean m_isCat = false; // FIXME use
	private WebView m_web;
	private Cursor m_cursor;
	private OfflineDetailActivity m_activity;

	protected View m_customView;
	protected FrameLayout m_customViewContainer;
	protected View m_contentView;
	protected FSVideoChromeClient m_chromeClient;
	protected View m_fab;
	
	public void initialize(int articleId) {
		m_articleId = articleId;
	}

	private class FSVideoChromeClient extends WebChromeClient {
		//protected View m_videoChildView;

		private CustomViewCallback m_callback;

		public FSVideoChromeClient(View container) {
			super();

		}

		@Override
		public void onShowCustomView(View view, CustomViewCallback callback) {
			m_activity.getSupportActionBar().hide();

			// if a view already exists then immediately terminate the new one
			if (m_customView != null) {
				callback.onCustomViewHidden();
				return;
			}
			m_customView = view;
			m_contentView.setVisibility(View.GONE);

			m_customViewContainer.setVisibility(View.VISIBLE);
			m_customViewContainer.addView(view);

			if (m_fab != null) m_fab.setVisibility(View.GONE);

			m_activity.showSidebar(false);

			m_callback = callback;
		}

		@Override
		public void onHideCustomView() {
			super.onHideCustomView();

			m_activity.getSupportActionBar().show();

			if (m_customView == null)
				return;

			m_contentView.setVisibility(View.VISIBLE);
			m_customViewContainer.setVisibility(View.GONE);

			// Hide the custom view.
			m_customView.setVisibility(View.GONE);

			// Remove the custom view from its container.
			m_customViewContainer.removeView(m_customView);
			m_callback.onCustomViewHidden();

			if (m_fab != null && m_prefs.getBoolean("enable_article_fab", true))
				m_fab.setVisibility(View.VISIBLE);

			m_customView = null;

			m_activity.showSidebar(true);
		}
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
		
		//getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
		//menu.setHeaderTitle(m_cursor.getString(m_cursor.getColumnIndex("title")));
		
		String title = m_cursor.getString(m_cursor.getColumnIndex("title"));
		
		if (v.getId() == R.id.article_content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.content_gallery_entry, menu);
				
				/* FIXME I have no idea how to do this correctly ;( */
				
				m_activity.setLastContentImageHitTestUrl(result.getExtra());
				
			} else {
				menu.setHeaderTitle(title);
				getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
			}
		} else {
			menu.setHeaderTitle(title);
			getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);	
		
	}
	
	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}
		
		View view = inflater.inflate(R.layout.fragment_article, container, false);

		m_cursor = m_activity.getDatabase().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
				new String[] { "articles.*", "feeds.title AS feed_title" }, "articles." + BaseColumns._ID + "=?", 
				new String[] { String.valueOf(m_articleId) }, null, null, null);

		m_cursor.moveToFirst();
		
		if (m_cursor.isFirst()) {
			m_contentView = view.findViewById(R.id.article_scrollview);
			m_customViewContainer = view.findViewById(R.id.article_fullscreen_video);

            final String link = m_cursor.getString(m_cursor.getColumnIndex("link"));

            NotifyingScrollView scrollView = view.findViewById(R.id.article_scrollview);
            m_fab = view.findViewById(R.id.article_fab);

            if (scrollView != null && m_activity.isSmallScreen()) {
                view.findViewById(R.id.article_heading_spacer).setVisibility(View.VISIBLE);

                scrollView.setOnScrollChangedListener(new NotifyingScrollView.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged(ScrollView who, int l, int t, int oldl, int oldt) {
                        ActionBar ab = m_activity.getSupportActionBar();

                        if (t >= oldt && t >= ab.getHeight()) {
                            ab.hide();
                        } else if (t <= ab.getHeight() || oldt - t >= 10) {
                            ab.show();
                        }

                    }
                });
            }

            if (scrollView != null && m_fab != null) {
                if (m_prefs.getBoolean("enable_article_fab", true)) {
                    scrollView.setOnTouchListener(new ShowHideOnScroll(m_fab));

                    m_fab.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                URL url = new URL(link.trim());
                                String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
                                        url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();

								m_activity.openUri(Uri.parse(uri));
                            } catch (Exception e) {
                                e.printStackTrace();
                                m_activity.toast(R.string.error_other_error);
                            }
                        }
                    });
                } else {
                    m_fab.setVisibility(View.GONE);
                }
            }

			int articleFontSize = Integer.parseInt(m_prefs.getString("article_font_size_sp", "16"));
			int articleSmallFontSize = Math.max(10, Math.min(18, articleFontSize - 2));
			
			TextView title = view.findViewById(R.id.title);

			if (title != null) {

                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, articleFontSize + 3));

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
							m_activity.openUri(Uri.parse(link));
						} catch (Exception e) {
							e.printStackTrace();
							m_activity.toast(R.string.error_other_error);
						}
					}
				});

			}


			ImageView attachments = view.findViewById(R.id.attachments);

			if (attachments != null) {
				attachments.setVisibility(View.GONE);
			}

			ImageView share = view.findViewById(R.id.share);

			if (share != null) {
				share.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						m_activity.shareArticle(m_articleId);
					}
				});
			}


			TextView comments = view.findViewById(R.id.comments);
			
			if (comments != null) {
				comments.setVisibility(View.GONE);
			}
			
			TextView note = view.findViewById(R.id.note);
			
			if (note != null) {
				note.setVisibility(View.GONE);
			}
			
			m_web = view.findViewById(R.id.article_content);
			
			if (m_web != null) {
				if (CommonActivity.THEME_DARK.equals(m_prefs.getString("theme", CommonActivity.THEME_DEFAULT))) {
					m_web.setBackgroundColor(Color.BLACK);
				}

				m_web.setWebViewClient(new WebViewClient() {
					@Override
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						try {
							m_activity.openUri(Uri.parse(url));

							return true;

						} catch (Exception e){
							e.printStackTrace();
						}

						return false;
					} });

				m_web.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        HitTestResult result = ((WebView) v).getHitTestResult();

                        if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                            registerForContextMenu(m_web);
                            m_activity.openContextMenu(m_web);
                            unregisterForContextMenu(m_web);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });

                // prevent flicker in ics
                if (!m_prefs.getBoolean("webview_hardware_accel", true)) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
						m_web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    }
                }

                String content;
                String cssOverride = "";

                WebSettings ws = m_web.getSettings();
                ws.setSupportZoom(false);

				if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					ws.setJavaScriptEnabled(true);

					m_chromeClient = new FSVideoChromeClient(getView());
					m_web.setWebChromeClient(m_chromeClient);

					ws.setMediaPlaybackRequiresUserGesture(false);
				}

				// we need to show "insecure" file:// urls
				if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {
					ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
				}

                TypedValue tvBackground = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.articleBackground, tvBackground, true);

                String backgroundHexColor = String.format("#%06X", (0xFFFFFF & tvBackground.data));

                cssOverride = "body { background : "+ backgroundHexColor+"; }";

                TypedValue tvTextColor = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.articleTextColor, tvTextColor, true);

                String textColor = String.format("#%06X", (0xFFFFFF & tvTextColor.data));

                cssOverride += "body { color : "+textColor+"; }";

                TypedValue tvLinkColor = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.linkColor, tvLinkColor, true);

                String linkHexColor = String.format("#%06X", (0xFFFFFF & tvLinkColor.data));
                cssOverride += " a:link {color: "+linkHexColor+";} a:visited { color: "+linkHexColor+";}";

				String articleContent = m_cursor.getString(m_cursor.getColumnIndex("content"));
				Document doc = Jsoup.parse(articleContent);
					
				if (doc != null) {
					if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {
						
						Elements images = doc.select("img,source");
						
						for (Element img : images) {
							String url = img.attr("src");

							Log.d(TAG, "src=" + url);
							
							if (ImageCacheService.isUrlCached(m_activity, url)) {						
								img.attr("src", "file://" + ImageCacheService.getCacheFileName(m_activity, url));
							}						
						}
					}
					
					// thanks webview for crashing on <video> tag
					/*Elements videos = doc.select("video");
					
					for (Element video : videos)
						video.remove();*/
					
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
                    "img,video { max-width : 100%; width : auto; height : auto; }" +
                    " table { width : 100%; }" +
                    cssOverride +
                    "</style>" +
                    "</head>" +
                    "<body>" + articleContent;
				
				content += "</body></html>";
				
				try {
					String baseUrl = null;
					
					try {
						URL url = new URL(link);
						baseUrl = url.getProtocol() + "://" + url.getHost();
					} catch (MalformedURLException e) {
						//
					}

					m_web.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null);
				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
			
			}
			
			TextView dv = view.findViewById(R.id.date);
			
			if (dv != null) {
				dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				Date d = new Date(m_cursor.getInt(m_cursor.getColumnIndex("updated")) * 1000L);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				dv.setText(df.format(d));
			}

			TextView tagv = view.findViewById(R.id.tags);
						
			if (tagv != null) {
				tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);

				int feedTitleIndex = m_cursor.getColumnIndex("feed_title");

				if (feedTitleIndex != -1 /* && m_isCat */) {
					String fTitle = m_cursor.getString(feedTitleIndex);
					
					int authorIndex = m_cursor.getColumnIndex("author");
					
					if (authorIndex >= 0) {
						String authorStr = m_cursor.getString(authorIndex);

						if (authorStr != null && authorStr.length() > 0) {
							fTitle += " (" + getString(R.string.author_formatted, m_cursor.getString(authorIndex)) + ")";
						}
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
	public void onPause() {
		super.onPause();

		if (m_web != null) m_web.onPause();
	}

	public boolean inCustomView() {
		return (m_customView != null);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();	
		
		m_cursor.close();
	}

	public void hideCustomView() {
		if (m_chromeClient != null) {
			m_chromeClient.onHideCustomView();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		if (m_web != null) m_web.onResume();
	}

	@Override
	public void onStop() {
		super.onStop();

		if (inCustomView()) {
			hideCustomView();
		}
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

		m_activity = (OfflineDetailActivity) activity;
		
	}
}
