package org.fox.ttrss;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MainActivity extends Activity implements FeedsFragment.OnFeedSelectedListener, HeadlinesFragment.OnArticleSelectedListener {
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
			Log.d(TAG, "Refreshing feeds...");
			
			refreshFeeds();
		}
	}
	
	public synchronized void refreshFeeds() {
		FeedsFragment frag = (FeedsFragment) getFragmentManager().findFragmentById(R.id.feeds_fragment);
		
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

	public void toast(String message) {
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.show();
	}
	
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
		}
		
		setContentView(R.layout.main);

		HeadlinesFragment hf = new HeadlinesFragment();
		ArticleFragment af = new ArticleFragment();
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.replace(R.id.feeds_fragment, new FeedsFragment());
		ft.replace(R.id.headlines_fragment, hf);
		ft.replace(R.id.article_fragment, af);
		ft.commit();
		
		findViewById(R.id.article_fragment).setVisibility(View.GONE);
		
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
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!m_prefs.getString("theme", "THEME_DARK").equals(m_themeName)) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			finish();
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

		return true;
	}

	public void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);
		
		if (mi != null) {
			mi.setTitle(labelId);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.update:
			refreshFeeds();
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
							
							setLoadingStatus(R.string.loading_message, true);
							
							ViewFlipper vf = (ViewFlipper) findViewById(R.id.main_flipper);
							
							if (vf != null) {
								vf.showNext();
							}
							
							FeedsFragment frag = new FeedsFragment();
							
							FragmentTransaction ft = getFragmentManager().beginTransaction();
							ft.replace(R.id.feeds_fragment, frag);
							ft.show(frag);
							ft.commit();
							
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
		
		m_activeFeed = feed;
		
		HeadlinesFragment hf = new HeadlinesFragment();
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();			
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.show(getFragmentManager().findFragmentById(R.id.headlines_fragment));
		ft.replace(R.id.headlines_fragment, hf);
		ft.addToBackStack(null);
		ft.commit();
	}

	public Article getSelectedArticle() {
		return m_selectedArticle;
	}
	
	@Override
	public void onArticleSelected(Article article) {
		Log.d(TAG, "Selected article: " + article.toString());
		
		m_selectedArticle = article;
		
		ArticleFragment frag = new ArticleFragment();
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();			
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		//ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
		ft.show(getFragmentManager().findFragmentById(R.id.article_fragment));
		ft.replace(R.id.article_fragment, frag);
		ft.addToBackStack(null);
		ft.commit();
		
		findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		
	}

	public Feed getActiveFeed() {
		return m_activeFeed;
	}
}