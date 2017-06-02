package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
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

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.util.NotifyingScrollView;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ArticleFragment extends Fragment  {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private Article m_article;
	private DetailActivity m_activity;
    private WebView m_web;
    protected View m_customView;
    protected FrameLayout m_customViewContainer;
    protected View m_contentView;
    protected FSVideoChromeClient m_chromeClient;
    protected View m_fab;
    protected int m_articleFontSize;
    protected int m_articleSmallFontSize;
    protected boolean m_acceleratedWebview = true;

    public void initialize(Article article) {
		m_article = article;
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

	//private View.OnTouchListener m_gestureListener;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		if (v.getId() == R.id.article_content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {

				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.content_gallery_entry, menu);
				
				/* FIXME I have no idea how to do this correctly ;( */
				
				m_activity.setLastContentImageHitTestUrl(result.getExtra());
				
			} else {
				menu.setHeaderTitle(m_article.title);
				getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
			}
		} else {
			menu.setHeaderTitle(m_article.title);
			getActivity().getMenuInflater().inflate(R.menu.context_article_link, menu);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);

	}

	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {

		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
            //m_fsviewShown = savedInstanceState.getBoolean("fsviewShown");
		}

		final View view = inflater.inflate(R.layout.fragment_article, container, false);

        /* if (m_fsviewShown) {
            view.findViewById(R.id.article_fullscreen_video).setVisibility(View.VISIBLE);
            view.findViewById(R.id.article_scrollview).setVisibility(View.INVISIBLE);
        } */

        m_contentView = view.findViewById(R.id.article_scrollview);
        m_customViewContainer = (FrameLayout) view.findViewById(R.id.article_fullscreen_video);

        /* if (m_article.id == HeadlinesFragment.ARTICLE_SPECIAL_TOP_CHANGED) {
            TextView statusMessage = (TextView) view.findViewById(R.id.article_status_message);
            statusMessage.setText(R.string.headlines_row_top_changed);
            statusMessage.setVisibility(View.VISIBLE);

            view.findViewById(R.id.article_scrollview).setVisibility(View.GONE);
            view.findViewById(R.id.article_fab).setVisibility(View.GONE);

            return view;
        } */

        NotifyingScrollView scrollView = (NotifyingScrollView) view.findViewById(R.id.article_scrollview);
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
                            m_activity.openUri(Uri.parse(m_article.link));
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

        m_articleFontSize = Integer.parseInt(m_prefs.getString("article_font_size_sp", "16"));
        m_articleSmallFontSize = Math.max(10, Math.min(18, m_articleFontSize - 2));

        TextView title = (TextView)view.findViewById(R.id.title);

        if (title != null) {

            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, m_articleFontSize + 3));

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
                        m_activity.openUri(Uri.parse(m_article.link));
                    } catch (Exception e) {
                        e.printStackTrace();
                        m_activity.toast(R.string.error_other_error);
                    }
                }
            });

        }

        ImageView share = (ImageView)view.findViewById(R.id.share);

        if (share != null) {
            share.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    m_activity.shareArticle(m_article);
                }
            });
        }

        TextView comments = (TextView)view.findViewById(R.id.comments);

        if (comments != null) {
            if (m_activity.getApiLevel() >= 4 && m_article.comments_count > 0) {
                comments.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

                String commentsTitle = getResources().getQuantityString(R.plurals.article_comments, m_article.comments_count, m_article.comments_count);
                comments.setText(commentsTitle);
                //comments.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                comments.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            String url = (m_article.comments_link != null && m_article.comments_link.length() > 0) ?
                                    m_article.comments_link : m_article.link;

                            m_activity.openUri(Uri.parse(url));
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
                note.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);
                note.setText(m_article.note);
            } else {
                note.setVisibility(View.GONE);
            }

        }

        TextView dv = (TextView)view.findViewById(R.id.date);

        if (dv != null) {
            dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

            Date d = new Date(m_article.updated * 1000L);
            DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
            dv.setText(df.format(d));
        }

        TextView tagv = (TextView)view.findViewById(R.id.tags);

        if (tagv != null) {
            tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, m_articleSmallFontSize);

            if (m_article.feed_title != null) {
                String fTitle = m_article.feed_title;

                if (m_article.author != null && m_article.author.length() > 0) {
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

        m_web = (WebView)view.findViewById(R.id.article_content);

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
                HitTestResult result = ((WebView)v).getHitTestResult();

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
                m_acceleratedWebview = false;
            }
        }

        m_web.setVisibility(View.VISIBLE);

        //renderContent(savedInstanceState);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                renderContent(savedInstanceState);
            }
        }, 250);

        return view;
	}

    protected void renderContent(Bundle savedInstanceState) {
        if (!isAdded() || m_web == null) return;

        Log.d(TAG, "renderContent: " + m_article.title);

        WebSettings ws = m_web.getSettings();
        ws.setSupportZoom(false);

        TypedValue tvBackground = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.articleBackground, tvBackground, true);

        String backgroundHexColor = String.format("#%06X", (0xFFFFFF & tvBackground.data));

        String cssOverride = "";

        cssOverride = "body { background : "+ backgroundHexColor+"; }";

        TypedValue tvTextColor = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.articleTextColor, tvTextColor, true);

        String textColor = String.format("#%06X", (0xFFFFFF & tvTextColor.data));

        cssOverride += "body { color : "+textColor+"; }";

        TypedValue tvLinkColor = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.linkColor, tvLinkColor, true);

        String linkHexColor = String.format("#%06X", (0xFFFFFF & tvLinkColor.data));
        cssOverride += " a:link {color: "+linkHexColor+";} a:visited { color: "+linkHexColor+";}";

        String articleContent = m_article.content != null ? m_article.content : "";

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ws.setJavaScriptEnabled(true);

            m_chromeClient = new FSVideoChromeClient(getView());
            m_web.setWebChromeClient(m_chromeClient);

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            ws.setMediaPlaybackRequiresUserGesture(false);
        }

        if (CommonActivity.THEME_DARK.equals(m_prefs.getString("theme", CommonActivity.THEME_DEFAULT))) {
            m_web.setBackgroundColor(Color.BLACK);
        }

        if (m_prefs.getBoolean("justify_article_text", true)) {
            cssOverride += "body { text-align : justify; } ";
        }

        ws.setDefaultFontSize(m_articleFontSize);

        StringBuilder content = new StringBuilder("<html>" +
                "<head>" +
                "<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
                "<meta name=\"viewport\" content=\"width=device-width, user-scalable=no\" />" +
                "<style type=\"text/css\">" +
                "body { padding : 0px; margin : 0px; line-height : 130%; }" +
                "img, video, iframe { max-width : 100%; width : auto; height : auto; }" +
                " table { width : 100%; }" +
                cssOverride +
                "</style>" +
                "</head>" +
                "<body>");

        content.append(articleContent);

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

                            content.append("<p><img src=\"" + strUrl.replace("\"", "\\\"") + "\"></p>");
                        }

                    } catch (MalformedURLException e) {
                        //
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        content.append("</body></html>");

        try {
            String baseUrl = null;

            try {
                URL url = new URL(m_article.link);
                baseUrl = url.getProtocol() + "://" + url.getHost();
            } catch (MalformedURLException e) {
                //
            }

            if (savedInstanceState == null || !m_acceleratedWebview) {
                m_web.loadDataWithBaseURL(baseUrl, content.toString(), "text/html", "utf-8", null);
            } else {
                WebBackForwardList rc = m_web.restoreState(savedInstanceState);

                if (rc == null) {
                    // restore failed...
                    m_web.loadDataWithBaseURL(baseUrl, content.toString(), "text/html", "utf-8", null);
                }
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        if (m_web != null) m_web.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (m_web != null) m_web.onResume();
    }

    public boolean inCustomView() {
        return (m_customView != null);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (inCustomView()) {
            hideCustomView();
        }
    }

    public void hideCustomView() {
        if (m_chromeClient != null) {
            m_chromeClient.onHideCustomView();
        }
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
		m_activity = (DetailActivity)activity;

	}
}
