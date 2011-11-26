package org.fox.ttrss;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.animation.LayoutTransition;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MainActivity extends FragmentActivity implements FeedsFragment.OnFeedSelectedListener, HeadlinesFragment.OnArticleSelectedListener {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private String m_sessionId;
	private Article m_selectedArticle;
	private Feed m_activeFeed;
	private Timer m_refreshTimer;
	private RefreshTask m_refreshTask;
	private Menu m_menu;
	private boolean m_unreadOnly = true;

	private class RefreshTask extends TimerTask {

		@Override
		public void run() {
			refreshFeeds();
		}
	}
	
	public synchronized void refreshFeeds() {
		FeedsFragment frag = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds_fragment);

		Log.d(TAG, "Refreshing feeds..." + frag);

		if (frag != null) {
			frag.refresh();
		}
	}
	

	public void setUnreadOnly(boolean unread) {
		m_unreadOnly = unread;
		refreshFeeds();
	}
	
	public boolean getUnreadOnly() {
		return m_unreadOnly;
	}
	

	public String getSessionId() {
		return m_sessionId;
	}
	
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		m_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());       

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		m_themeName = m_prefs.getString("theme", "THEME_DARK");
	
		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_activeFeed = savedInstanceState.getParcelable("activeFeed");
			m_selectedArticle = savedInstanceState.getParcelable("selectedArticle");
		}
		
		setContentView(R.layout.main);

		if (android.os.Build.VERSION.SDK_INT > 10) {
			LayoutTransition transitioner = new LayoutTransition();
			LinearLayout layout = (LinearLayout)findViewById(R.id.main);
			layout.setLayoutTransition(transitioner);
		}
		
		if (m_selectedArticle == null)
			findViewById(R.id.article_fragment).setVisibility(View.GONE);
		else
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);

		if (m_sessionId != null) {
			loginSuccess();
		} else {
			login();
		}
	
	}

	public void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView)findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(status);
		}
		
		View pb = findViewById(R.id.loading_progress);
		
		if (pb != null) {
			pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
		}
	}
				
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putString("sessionId", m_sessionId);
		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putParcelable("activeFeed", m_activeFeed);
		out.putParcelable("selectedArticle", m_selectedArticle);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!m_prefs.getString("theme", "THEME_DARK").equals(m_themeName)) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			finish();
		} else {
			FeedsFragment frag = (FeedsFragment)getSupportFragmentManager().findFragmentById(R.id.feeds_fragment);
			
			if (frag != null) {
				frag.sortFeeds();
			}
		}
		
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}
		
		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		
		m_menu = menu;
		
		initMainMenu();

		return true;
	}

	public void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);
		
		if (mi != null) {
			mi.setTitle(labelId);
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	Log.d(TAG, "Overriding back button");
        	
        	if (m_selectedArticle != null) {
        		closeArticle();
        	} else {
        		finish();
        	}

        	return false;
        }
        return super.onKeyDown(keyCode, event);
    }
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.update_feeds:
			refreshFeeds();
			return true;
		case R.id.logout:
			logout();
			return true;
		case R.id.login:
			login();
			return true;
		case R.id.close_article:
			closeArticle();
			return true;
		case R.id.load_more_articles:
			viewFeed(m_activeFeed, true);
			return true;
		case R.id.share_article:
			shareArticle(m_selectedArticle);
			return true;
		case R.id.show_feeds:
			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_unread_feeds);
			} else {
				item.setTitle(R.string.menu_all_feeds);
			}
			
			setUnreadOnly(!getUnreadOnly());
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	public void shareArticle(Article article) {
		if (article != null) {
			Intent intent = new Intent(Intent.ACTION_SEND);
			
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, article.title);
			intent.putExtra(Intent.EXTRA_TEXT, article.link);

			startActivity(Intent.createChooser(intent, getString(R.id.share_article)));
		}
	}
	
	public void closeArticle() {
		findViewById(R.id.article_fragment).setVisibility(View.GONE);	
		findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);	

		m_selectedArticle = null;

		initMainMenu();
		refreshFeeds();
	}

	private void initMainMenu() {
		if (m_menu != null) {

			if (m_sessionId != null) {
				m_menu.findItem(R.id.login).setVisible(false);
			
				m_menu.findItem(R.id.logout).setVisible(true);
			
				if (m_selectedArticle != null) {
					m_menu.findItem(R.id.close_article).setVisible(true);
					m_menu.findItem(R.id.share_article).setVisible(true);
					
					m_menu.findItem(R.id.update_feeds).setEnabled(false);
					m_menu.findItem(R.id.show_feeds).setEnabled(false);
				} else {
					m_menu.findItem(R.id.close_article).setVisible(false);
					m_menu.findItem(R.id.share_article).setVisible(false);
					
					m_menu.findItem(R.id.update_feeds).setEnabled(true);
					m_menu.findItem(R.id.show_feeds).setEnabled(true);
				}

				m_menu.findItem(R.id.load_more_articles).setVisible(m_activeFeed != null);

			} else {
				m_menu.findItem(R.id.login).setVisible(true);
				
				m_menu.findItem(R.id.logout).setVisible(false);
				m_menu.findItem(R.id.close_article).setVisible(false);
				m_menu.findItem(R.id.share_article).setVisible(false);
				m_menu.findItem(R.id.load_more_articles).setVisible(false);

				m_menu.findItem(R.id.update_feeds).setEnabled(false);
				m_menu.findItem(R.id.show_feeds).setEnabled(false);
			}
		}		
	}
	
	private void loginSuccess() {
		findViewById(R.id.loading_container).setVisibility(View.INVISIBLE);
		findViewById(R.id.main).setVisibility(View.VISIBLE);

		initMainMenu();
		
		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}
		
		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}
		
		m_refreshTask = new RefreshTask();
		m_refreshTimer = new Timer("Refresh");
		
		m_refreshTimer.schedule(m_refreshTask, 60*1000L, 60*1000L);
	}
	
	private class LoginRequest extends ApiRequest {
		
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {			
					JsonObject rv = result.getAsJsonObject();

					int status = rv.get("status").getAsInt();
					
					if (status == 0) {
						JsonObject content = rv.get("content").getAsJsonObject();
						if (content != null) {
							m_sessionId = content.get("session_id").getAsString();
							
							Log.d(TAG, "Authenticated!");
							
							setLoadingStatus(R.string.loading_message, true);

							FeedsFragment frag = new FeedsFragment();
							
							FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
							ft.replace(R.id.feeds_fragment, frag);
							ft.commit();
							
							loginSuccess();
							
						}
					} else {
						JsonObject content = rv.get("content").getAsJsonObject();
						
						if (content != null) {
							String error = content.get("error").getAsString();

							m_sessionId = null;

							if (error.equals("LOGIN_ERROR")) {
								setLoadingStatus(R.string.login_wrong_password, false);
							} else if (error.equals("API_DISABLED")) {
								setLoadingStatus(R.string.login_api_disabled, false);
							} else {
								setLoadingStatus(R.string.login_failed, false);
							}
							
							m_menu.findItem(R.id.login).setVisible(true);
						}							
					}
				} catch (Exception e) {
					e.printStackTrace();						
				}
			}
	    }
	}

	@Override
	public void onFeedSelected(Feed feed) {
		Log.d(TAG, "Selected feed: " + feed.toString());
		viewFeed(feed, false);
	}

	public Article getSelectedArticle() {
		return m_selectedArticle;
	}
	
	public void viewFeed(Feed feed, boolean append) {
		m_activeFeed = feed;
	
		initMainMenu();
		
		if (!append) {
			HeadlinesFragment hf = new HeadlinesFragment();
		
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
			ft.replace(R.id.headlines_fragment, hf);
			ft.commit();
		} else {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentById(R.id.headlines_fragment);
			if (hf != null) {
				hf.refresh(true);
			}
		}
	}

	public void openArticle(Article article) {
		m_selectedArticle = article;
		
		initMainMenu();
		
		ArticleFragment frag = new ArticleFragment();
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();			
		ft.replace(R.id.article_fragment, frag);
		ft.commit();
		
		findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
		findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
				
	}
	
	@Override
	public void onArticleSelected(Article article) {
		openArticle(article);
	}

	public Feed getActiveFeed() {
		return m_activeFeed;
	}

	public void logout() {
		if (m_refreshTask != null) {
			m_refreshTask.cancel();
			m_refreshTask = null;
		}
		
		if (m_refreshTimer != null) {
			m_refreshTimer.cancel();
			m_refreshTimer = null;
		}

		m_sessionId = null;
		
		findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
		findViewById(R.id.main).setVisibility(View.INVISIBLE);
	
		TextView tv = (TextView)findViewById(R.id.loading_message);
		
		if (tv != null) {
			tv.setText(R.string.login_ready);		
		}
		
		findViewById(R.id.loading_progress).setVisibility(View.GONE);
		
		initMainMenu();
	}

	public void login() {		

		logout();
		
		if (m_prefs.getString("ttrss_url", null) == null ||
				m_prefs.getString("login", null) == null ||	
				m_prefs.getString("password", null) == null) {
			
			setLoadingStatus(R.string.login_need_configure, false);
			
		} else {
		
			LoginRequest ar = new LoginRequest();
			ar.setApi(m_prefs.getString("ttrss_url", null));
	
			HashMap<String,String> map = new HashMap<String,String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", null));
					put("password", m_prefs.getString("password", null));
				}			 
			};
	
			ar.execute(map);
			
			setLoadingStatus(R.string.login_in_progress, true);
		}
	}
}