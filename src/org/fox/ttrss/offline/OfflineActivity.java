package org.fox.ttrss.offline;

import java.util.ArrayList;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.DummyFragment;
import org.fox.ttrss.FeedCategoriesFragment;
import org.fox.ttrss.FeedsFragment;
import org.fox.ttrss.MainActivity;
import org.fox.ttrss.OnlineServices;
import org.fox.ttrss.OnlineServices.RelativeArticle;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;

import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.widget.Toast;

public class OfflineActivity extends CommonActivity implements
		OfflineServices {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private Menu m_menu;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_enableCats = false;

	private int m_activeFeedId = 0;
	private boolean m_activeFeedIsCat = false;
	private int m_activeCatId = -1;
	private int m_selectedArticleId = 0;

	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;
	private NavigationListener m_navigationListener;
	private NavigationAdapter m_navigationAdapter;
	private ArrayList<NavigationEntry> m_navigationEntries = new ArrayList<NavigationEntry>();
	
	private class RootNavigationEntry extends NavigationEntry {
		public RootNavigationEntry(String title) {
			super(title);
		}

		@Override	
		public void onItemSelected() {
			
			m_activeFeedId = 0;
			m_selectedArticleId = 0;
			m_activeCatId = -1;

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (isSmallScreen()) {
				
				if (m_enableCats) {
					ft.replace(R.id.fragment_container, new OfflineFeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.fragment_container, new OfflineFeedsFragment(), FRAG_FEEDS);
				}
				
				Fragment hf = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				if (hf != null) ft.remove(hf);
				
				Fragment af = getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				if (af != null) ft.remove(af);

			} else {
				if (m_enableCats) {
					ft.replace(R.id.feeds_fragment, new OfflineFeedCategoriesFragment(), FRAG_CATS);				
				} else {
					ft.replace(R.id.feeds_fragment, new OfflineFeedsFragment(), FRAG_FEEDS);
				}
				
				ft.replace(R.id.headlines_fragment, new DummyFragment(), "");

				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				//findViewById(R.id.article_fragment).setVisibility(View.GONE);

				ft.replace(R.id.article_fragment, new DummyFragment(), "");
			}
			
			ft.commit();

			initMainMenu();
		}
	}
	
	private class CategoryNavigationEntry extends NavigationEntry {
		int m_category = -1;
		
		public CategoryNavigationEntry(int category, String title) {
			super(title);

			m_category = category;
		}

		@Override	
		public void onItemSelected() {
			m_selectedArticleId = 0;
			m_activeFeedId = 0;

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (isSmallScreen()) {

				Fragment hf = getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				if (hf != null) ft.remove(hf);
				
				Fragment af = getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				if (af != null) ft.remove(af);
				
				if (m_activeFeedIsCat) {
					ft.replace(R.id.fragment_container, new OfflineFeedCategoriesFragment());
				} else {
					ft.replace(R.id.fragment_container, new OfflineFeedsFragment(m_category));
				}
				
			} else {
				ft.replace(R.id.article_fragment, new DummyFragment(), "");

				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				//findViewById(R.id.article_fragment).setVisibility(View.GONE);				

				//ft.replace(R.id.headlines_fragment, new DummyFragment(), "");				
			}
			
			ft.commit();

			m_activeFeedId = 0;
			refreshViews();
			initMainMenu();
		}
	}
	

	private class FeedNavigationEntry extends NavigationEntry {
		int m_feed = 0;
		
		public FeedNavigationEntry(int feed, String title) {
			super(title);

			m_feed = feed;
		}

		@Override	
		public void onItemSelected() {

			m_selectedArticleId = 0;
			
			if (!isSmallScreen())
				findViewById(R.id.article_fragment).setVisibility(View.GONE);							

			viewFeed(m_feed, false);
		}
	}
	
	private abstract class NavigationEntry {
		private String title = null;
		private int timesCalled = 0;
		
		public void _onItemSelected(int position, int size) {
			Log.d(TAG, "_onItemSelected; TC=" + timesCalled + " P/S=" + position + "/" + size);
			
			if (position == size && timesCalled == 0) {
				++timesCalled;			
			} else {
				onItemSelected();
			}			
		}
		
		public NavigationEntry(String title) {
			this.title = title;
		}
		
		public String toString() {
			return title;
		}		
		
		public abstract void onItemSelected();
	}
	
	private class NavigationAdapter extends ArrayAdapter<NavigationEntry> {
		public NavigationAdapter(Context context, int textViewResourceId, ArrayList<NavigationEntry> items) {
			super(context, textViewResourceId, items);
		}
	}
	
	private class NavigationListener implements ActionBar.OnNavigationListener {
		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			Log.d(TAG, "onNavigationItemSelected: " + itemPosition);

			NavigationEntry entry = m_navigationAdapter.getItem(itemPosition);
			entry._onItemSelected(itemPosition, m_navigationAdapter.getCount()-1);
			
			return false;
		}
	}
	
	private class HeadlinesActionModeCallback implements ActionMode.Callback {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			deselectAllArticles();
			m_headlinesActionMode = null;
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			
			 MenuInflater inflater = getMenuInflater();
	            inflater.inflate(R.menu.headlines_action_menu, menu);
			
			return true;
		}
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			onOptionsItemSelected(item);
			return false;
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);
		
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		NotificationManager nmgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nmgr.cancel(OfflineDownloadService.NOTIFY_DOWNLOADING);

		m_themeName = m_prefs.getString("theme", "THEME_DARK");

		if (savedInstanceState != null) {
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_unreadArticlesOnly = savedInstanceState
					.getBoolean("unreadArticlesOnly");
			m_activeFeedId = savedInstanceState.getInt("offlineActiveFeedId");
			m_selectedArticleId = savedInstanceState.getInt("offlineArticleId");
			m_activeFeedIsCat = savedInstanceState.getBoolean("activeFeedIsCat");
			m_activeCatId = savedInstanceState.getInt("activeCatId");
		}

		m_enableCats = m_prefs.getBoolean("enable_cats", false);
		
		setContentView(R.layout.main);

		if (!isCompatMode()) {
			if (!isSmallScreen()) {
				findViewById(R.id.feeds_fragment).setVisibility(m_selectedArticleId != 0 && getOrientation() % 2 != 0 ? View.GONE : View.VISIBLE);
				findViewById(R.id.article_fragment).setVisibility(m_selectedArticleId != 0 ? View.VISIBLE : View.GONE);
			}
			
			LayoutTransition transitioner = new LayoutTransition();
			((ViewGroup) findViewById(R.id.fragment_container)).setLayoutTransition(transitioner);
			
			m_navigationAdapter = new NavigationAdapter(this, android.R.layout.simple_spinner_dropdown_item, m_navigationEntries);
			
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
			m_navigationListener = new NavigationListener();
			
			getActionBar().setListNavigationCallbacks(m_navigationAdapter, m_navigationListener);
			
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
		}

		initMainMenu();

		findViewById(R.id.loading_container).setVisibility(View.GONE);

		if (isSmallScreen()) {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			// temporary workaround against viewpager going a bit crazy when restoring after rotation
			if (m_selectedArticleId != 0) {
				ft.remove(getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE));
				m_selectedArticleId = 0;
			}
			ft.commit();
		}
		
		if (m_activeFeedId == 0 && !m_activeFeedIsCat) {
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			
			Fragment frag = null;
			String tag = null;
			
			if (m_enableCats) {
				frag = new OfflineFeedCategoriesFragment();
				tag = FRAG_CATS;
			} else {
				frag = new OfflineFeedsFragment();
				tag = FRAG_FEEDS;
			}
			
			if (isSmallScreen()) {
				ft.replace(R.id.fragment_container, frag, tag);
			} else {
				ft.replace(R.id.feeds_fragment, frag, tag);
			}
				
			ft.commit();
		}
		
		
	}

	private void switchOnline() {
		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.putBoolean("offline_mode_active", false);
		editor.commit();

		Intent refresh = new Intent(this, MainActivity.class);
		startActivity(refresh);
		finish();
	}

	@Override
	public int getActiveFeedId() {
		return m_activeFeedId;
	}

	/* private void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(status);
		}

		setProgressBarIndeterminateVisibility(showProgress);
	} */

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putInt("offlineActiveFeedId", m_activeFeedId);
		out.putInt("offlineArticleId", m_selectedArticleId);
		out.putBoolean("activeFeedIsCat", m_activeFeedIsCat);
		out.putInt("activeCatId", m_activeCatId);
	}

	private void setUnreadOnly(boolean unread) {
		m_unreadOnly = unread;

		refreshViews();

		/*
		 * if (!m_enableCats || m_activeCategory != null ) refreshFeeds(); else
		 * refreshCategories();
		 */
	}

	@Override
	public boolean getUnreadOnly() {
		return m_unreadOnly;
	}

	@Override
	public void onResume() {
		super.onResume();
		
		boolean needRefresh = !m_prefs.getString("theme", "THEME_DARK").equals(
				m_themeName)
				|| m_prefs.getBoolean("enable_cats", false) != m_enableCats;

		if (needRefresh) {
			Intent refresh = new Intent(this, OfflineActivity.class);
			startActivity(refresh);
			finish();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.offline_menu, menu);

		m_menu = menu;

		initMainMenu();

		MenuItem item = menu.findItem(R.id.show_feeds);

		if (getUnreadOnly()) {
			item.setTitle(R.string.menu_all_feeds);
		} else {
			item.setTitle(R.string.menu_unread_feeds);
		}

		return true;
	}

	private void goBack(boolean allowQuit) {
		if (isSmallScreen()) {
			if (m_selectedArticleId != 0) {
				closeArticle();			
			} else if (m_activeFeedId != 0) {
				m_activeFeedId = 0;

				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

				if (m_activeFeedIsCat) {
					ft.replace(R.id.fragment_container, new OfflineFeedCategoriesFragment(), FRAG_CATS);
				} else {
					ft.replace(R.id.fragment_container, new OfflineFeedsFragment(m_activeCatId), FRAG_FEEDS);
				}
				ft.commit();
				
				refreshViews();
				initMainMenu();
			} else if (m_activeCatId != -1) {
				closeCategory();
			} else if (allowQuit) {
				finish();
			}
		} else {
			if (m_selectedArticleId != 0) {
				closeArticle();
			/* } else if (m_activeFeedId != 0) {
				m_activeFeedId = 0;
				
				OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_FEEDS);
				
				OfflineFeedCategoriesFragment cf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_CATS);
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.replace(R.id.headlines_fragment, new DummyFragment(), "");
				ft.commit();
				
				if (ff != null) {
					ff.setSelectedFeedId(0);
				}
				
				if (cf != null) {
					cf.setSelectedFeedId(-1);
				}

				refreshViews();
				initMainMenu(); */
			} else if (m_activeCatId != -1) {
				closeCategory();	
			} else if (allowQuit) {
				finish();
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		goBack(true);
	}

	/*
	 * @Override public boolean onKeyDown(int keyCode, KeyEvent event) { if
	 * (keyCode == KeyEvent.KEYCODE_BACK) {
	 * 
	 * if (isSmallScreen()) { if (m_selectedArticleId != 0) { closeArticle();
	 * } else if (m_activeFeedId != 0) { if (isCompatMode()) {
	 * findViewById(R.id.main).setAnimation(AnimationUtils.loadAnimation(this,
	 * R.anim.slide_right)); }
	 */

	/*
	 * if (m_activeFeed != null && m_activeFeed.is_cat) {
	 * findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
	 * findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
	 * 
	 * refreshCategories(); } else {
	 *//*
		 * findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
		 * findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE); //}
		 * m_activeFeedId = 0; refreshViews(); initMainMenu();
		 * 
		 * } else { finish(); } } else { if (m_selectedArticleId != 0) {
		 * closeArticle(); } else { finish(); } }
		 * 
		 * return false; } return super.onKeyDown(keyCode, event); }
		 */

	private Cursor getArticleById(int articleId) {
		Cursor c = getReadableDb().query("articles", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(articleId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	private Cursor getFeedById(int feedId) {
		Cursor c = getReadableDb().query("feeds", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(feedId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	private Cursor getCatById(int catId) {
		Cursor c = getReadableDb().query("categories", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(catId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	private Intent getShareIntent(Cursor article) {
		String title = article.getString(article.getColumnIndex("title"));
		String link = article.getString(article.getColumnIndex("link"));

		Intent intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		//intent.putExtra(Intent.EXTRA_SUBJECT, title);
		intent.putExtra(Intent.EXTRA_TEXT, title + " " + link);

		return intent;
	}
	
	private void shareArticle(int articleId) {

		Cursor article = getArticleById(articleId);

		if (article != null) {
			shareArticle(article);
			article.close();
		}
	}

	private void shareArticle(Cursor article) {
		if (article != null) {
			Intent intent = getShareIntent(article);
			
			startActivity(Intent.createChooser(intent,
					getString(R.id.share_article)));
		}
	}

	private void refreshHeadlines() {
		OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		if (ohf != null) {
			ohf.refresh();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		switch (item.getItemId()) {
		case R.id.close_feed:
			if (m_activeFeedId != 0 || m_activeFeedIsCat) {
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.replace(R.id.headlines_fragment, new DummyFragment(), "");
				ft.commit();
				
				if (m_activeFeedIsCat) {
					OfflineFeedCategoriesFragment cats = (OfflineFeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
					cats.setSelectedFeedId(-1);
				} else {
					OfflineFeedsFragment feeds = (OfflineFeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
					feeds.setSelectedFeedId(0);					
				}
	
				m_activeFeedId = 0;
	
				initMainMenu();
			}
			return true;
		case R.id.close_article:
			closeArticle();
			return true;
		case android.R.id.home:
			goBack(false);
			return true;
		case R.id.search:
			if (ohf != null && isCompatMode()) {
				Dialog dialog = new Dialog(this);

				final EditText edit = new EditText(this);

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.search)
						.setPositiveButton(getString(R.string.search),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										
										String query = edit.getText().toString().trim();
										
										ohf.setSearchQuery(query);

									}
								})
						.setNegativeButton(getString(R.string.cancel),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										
										//

									}
								}).setView(edit);
				
				dialog = builder.create();
				dialog.show();
			}
			
			return true;
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.go_online:
			switchOnline();
			return true;
		case R.id.headlines_select:
			if (ohf != null) {
				Dialog dialog = new Dialog(this);
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.headlines_select_dialog);

				builder.setSingleChoiceItems(new String[] {
						getString(R.string.headlines_select_all),
						getString(R.string.headlines_select_unread),
						getString(R.string.headlines_select_none) }, 0,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								switch (which) {
								case 0:
									SQLiteStatement stmtSelectAll = getWritableDb()
											.compileStatement(
													"UPDATE articles SET selected = 1 WHERE feed_id = ?");
									stmtSelectAll.bindLong(1, m_activeFeedId);
									stmtSelectAll.execute();
									stmtSelectAll.close();
									break;
								case 1:
									SQLiteStatement stmtSelectUnread = getWritableDb()
											.compileStatement(
													"UPDATE articles SET selected = 1 WHERE feed_id = ? AND unread = 1");
									stmtSelectUnread
											.bindLong(1, m_activeFeedId);
									stmtSelectUnread.execute();
									stmtSelectUnread.close();
									break;
								case 2:
									deselectAllArticles();
									break;
								}

								refreshViews();
								initMainMenu();

								dialog.cancel();
							}
						});

				dialog = builder.create();
				dialog.show();
			}
			return true;
		case R.id.headlines_mark_as_read:
			if (m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET unread = 0 WHERE feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.share_article:
			if (android.os.Build.VERSION.SDK_INT < 14) {
				shareArticle(m_selectedArticleId);
			}
			return true;
		case R.id.toggle_marked:
			if (m_selectedArticleId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET marked = NOT marked WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, m_selectedArticleId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.selection_select_none:
			deselectAllArticles();
			return true;
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET unread = NOT unread WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET marked = NOT marked WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET published = NOT published WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.toggle_published:
			if (m_selectedArticleId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET published = NOT published WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, m_selectedArticleId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.catchup_above:
			if (m_selectedArticleId != 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET unread = 0 WHERE updated >= "
								+ "(SELECT updated FROM articles WHERE "
								+ BaseColumns._ID + " = ?) AND feed_id = ?");
				stmt.bindLong(1, m_selectedArticleId);
				stmt.bindLong(2, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.set_unread:
			if (m_selectedArticleId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET unread = 1 WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, m_selectedArticleId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());

			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_all_feeds);
			} else {
				item.setTitle(R.string.menu_unread_feeds);
			}

			return true;
		default:
			Log.d(TAG,
					"onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	private void refreshFeeds() {
		OfflineFeedsFragment frag = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);

		if (frag != null) {
			frag.refresh();
		}
	}

	private void refreshCats() {
		OfflineFeedCategoriesFragment frag = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		if (frag != null) {
			frag.refresh();
		}
	}

	private void closeArticle() {
		m_selectedArticleId = 0;
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		if (isSmallScreen()) {
			ft.remove(getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE));
			ft.show(getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES));
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);	
			findViewById(R.id.article_fragment).setVisibility(View.GONE);
			ft.replace(R.id.article_fragment, new DummyFragment(), FRAG_ARTICLE);
		}
		ft.commit();

		initMainMenu();
		
		refreshViews();
	}

	private int getSelectedArticleCount() {
		Cursor c = getReadableDb().query("articles",
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null,
				null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();

		return selected;
	}

	@Override
	public void initMainMenu() {
		if (m_menu != null) {
			int numSelected = getSelectedArticleCount();
			
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			
			if (numSelected != 0) {
				if (isCompatMode()) {
					m_menu.setGroupVisible(R.id.menu_group_headlines_selection, true);
				} else {
					if (m_headlinesActionMode == null)
						m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
				}
			} else if (m_selectedArticleId != 0) {
				m_menu.setGroupVisible(R.id.menu_group_article, true);
				m_menu.findItem(R.id.close_article).setVisible(!isSmallScreen());
				
				if (android.os.Build.VERSION.SDK_INT >= 14) {			
					ShareActionProvider shareProvider = (ShareActionProvider) m_menu.findItem(R.id.share_article).getActionProvider();
					
					if (m_selectedArticleId != 0) {
						Log.d(TAG, "setting up share provider");
						shareProvider.setShareIntent(getShareIntent(getArticleById(m_selectedArticleId)));
						
						if (!m_prefs.getBoolean("tablet_article_swipe", false) && !isSmallScreen()) {
							m_menu.findItem(R.id.share_article).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
						}
					}
				}
			} else if (m_activeFeedId != 0) {
				m_menu.setGroupVisible(R.id.menu_group_headlines, true);
				m_menu.findItem(R.id.close_feed).setVisible(!isSmallScreen());
				
				MenuItem search = m_menu.findItem(R.id.search);
				
				if (!isCompatMode()) {
					SearchView searchView = (SearchView) search.getActionView();
					searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
						private String query = "";
						
						@Override
						public boolean onQueryTextSubmit(String query) {
							OfflineHeadlinesFragment frag = (OfflineHeadlinesFragment) getSupportFragmentManager()
									.findFragmentByTag(FRAG_HEADLINES);
							
							if (frag != null) {
								frag.setSearchQuery(query);
								this.query = query;
							}
							
							return false;
						}
						
						@Override
						public boolean onQueryTextChange(String newText) {
							if (newText.equals("") && !newText.equals(this.query)) {
								OfflineHeadlinesFragment frag = (OfflineHeadlinesFragment) getSupportFragmentManager()
										.findFragmentByTag(FRAG_HEADLINES);
								
								if (frag != null) {
									frag.setSearchQuery(newText);
									this.query = newText;
								}
							}
							
							return false;
						}
					});
				}
				
			} else {
				m_menu.setGroupVisible(R.id.menu_group_feeds, true);
			}
			
			if (numSelected == 0 && m_headlinesActionMode != null) {
				m_headlinesActionMode.finish();
			}
		}
		
		updateTitle();
	}

	@Override
	public void onPause() {
		super.onPause();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void refreshViews() {
		refreshFeeds();
		refreshCats();
		refreshHeadlines();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);
		OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);
		OfflineFeedCategoriesFragment cf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		switch (item.getItemId()) {
		case R.id.article_link_copy:
			if (m_selectedArticleId != 0) {
				Cursor article = null;
				
				if (m_selectedArticleId != 0) {
					article = getArticleById(m_selectedArticleId);
				} else if (info != null) {
					article = hf.getArticleAtPosition(info.position);
				}
				
				if (article != null) {				
					copyToClipboard(article.getString(article.getColumnIndex("link")));
					article.close();
				}
			}
			return true;
		case R.id.article_link_share:
			if (m_selectedArticleId != 0) {
				shareArticle(m_selectedArticleId);
			}
			return true;		

		case R.id.browse_articles:
			if (cf != null) {
				int catId = cf.getCatIdAtPosition(info.position);			
				viewFeed(catId, true);
			}
			return true;
		case R.id.browse_feeds:
			if (cf != null) {
				int catId = cf.getCatIdAtPosition(info.position);
				viewCategory(catId, false);
			}
			return true;
		case R.id.catchup_category:
			if (cf != null) {
				int catId = cf.getCatIdAtPosition(info.position);

				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET unread = 0 WHERE feed_id IN (SELECT "+
							BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
				stmt.bindLong(1, catId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		case R.id.catchup_feed:
			if (ff != null) {
				int feedId = ff.getFeedIdAtPosition(info.position);

				if (feedId != 0) {
					SQLiteStatement stmt = getWritableDb().compileStatement(
							"UPDATE articles SET unread = 0 WHERE feed_id = ?");
					stmt.bindLong(1, feedId);
					stmt.execute();
					stmt.close();
					refreshViews();
				}
			}
			return true;
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET unread = NOT unread WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			} else {
				int articleId = hf.getArticleIdAtPosition(info.position);
				if (articleId != 0) {
					SQLiteStatement stmt = getWritableDb().compileStatement(
							"UPDATE articles SET unread = NOT unread WHERE "
									+ BaseColumns._ID + " = ?");
					stmt.bindLong(1, articleId);
					stmt.execute();
					stmt.close();
					refreshViews();
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET marked = NOT marked WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			} else {
				int articleId = hf.getArticleIdAtPosition(info.position);
				if (articleId != 0) {
					SQLiteStatement stmt = getWritableDb().compileStatement(
							"UPDATE articles SET marked = NOT marked WHERE "
									+ BaseColumns._ID + " = ?");
					stmt.bindLong(1, articleId);
					stmt.execute();
					stmt.close();
					refreshViews();
				}
			}
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET published = NOT published WHERE selected = 1 AND feed_id = ?");
				stmt.bindLong(1, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			} else {
				int articleId = hf.getArticleIdAtPosition(info.position);
				if (articleId != 0) {
					SQLiteStatement stmt = getWritableDb().compileStatement(
							"UPDATE articles SET published = NOT published WHERE "
									+ BaseColumns._ID + " = ?");
					stmt.bindLong(1, articleId);
					stmt.execute();
					stmt.close();
					refreshViews();
				}
			}
			return true;
		case R.id.share_article:
			Cursor article = hf.getArticleAtPosition(info.position);

			if (article != null) {
				shareArticle(article);
			}
			return true;
		case R.id.catchup_above:
			int articleId = hf.getArticleIdAtPosition(info.position);

			if (articleId != 0 && m_activeFeedId != 0) {
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET unread = 0 WHERE updated >= "
								+ "(SELECT updated FROM articles WHERE "
								+ BaseColumns._ID + " = ?) AND feed_id = ?");
				stmt.bindLong(1, articleId);
				stmt.bindLong(2, m_activeFeedId);
				stmt.execute();
				stmt.close();
				refreshViews();
			}
			return true;
		default:
			Log.d(TAG,
					"onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (action == KeyEvent.ACTION_DOWN) {

				OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_HEADLINES);

				int nextId = getRelativeArticleId(m_selectedArticleId,
						m_activeFeedId, RelativeArticle.AFTER);

				if (nextId != 0 && ohf != null) {
					if (m_prefs.getBoolean("combined_mode", false)) {
						ohf.setActiveArticleId(nextId);

						SQLiteStatement stmt = getWritableDb()
								.compileStatement(
										"UPDATE articles SET unread = 0 "
												+ "WHERE " + BaseColumns._ID
												+ " = ?");

						stmt.bindLong(1, nextId);
						stmt.execute();
						stmt.close();

					} else {
						openArticle(nextId, 0);
					}
				}
			}
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (action == KeyEvent.ACTION_UP) {

				OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
						.findFragmentByTag(FRAG_HEADLINES);

				int prevId = getRelativeArticleId(m_selectedArticleId,
						m_activeFeedId, RelativeArticle.BEFORE);

				if (prevId != 0 && ohf != null) {
					if (m_prefs.getBoolean("combined_mode", false)) {
						ohf.setActiveArticleId(prevId);

						SQLiteStatement stmt = getWritableDb()
								.compileStatement(
										"UPDATE articles SET unread = 0 "
												+ "WHERE " + BaseColumns._ID
												+ " = ?");

						stmt.bindLong(1, prevId);
						stmt.execute();
						stmt.close();

					} else {
						openArticle(prevId, 0);
					}
				}
			}
			return true;
		default:
			return super.dispatchKeyEvent(event);
		}
	}

	private void deselectAllArticles() {
		getWritableDb().execSQL("UPDATE articles SET selected = 0 ");
	}

	@Override
	public int getRelativeArticleId(int baseId, int feedId,
			OnlineServices.RelativeArticle mode) {

		Cursor c;

		/*
		 * if (baseId == 0) { c = getReadableDb().query("articles", null,
		 * "feed_id = ?", new String[] { String.valueOf(feedId) }, null, null,
		 * "updated DESC LIMIT 1");
		 * 
		 * if (c.moveToFirst()) { baseId = c.getInt(0); }
		 * 
		 * c.close();
		 * 
		 * return baseId; }
		 */

		if (mode == RelativeArticle.BEFORE) {
			c = getReadableDb().query(
					"articles",
					null,
					"updated > (SELECT updated FROM articles WHERE "
							+ BaseColumns._ID + " = ?) AND feed_id = ?",
					new String[] { String.valueOf(baseId),
							String.valueOf(feedId) }, null, null,
					"updated  LIMIT 1");

		} else {
			c = getReadableDb().query(
					"articles",
					null,
					"updated < (SELECT updated FROM articles WHERE "
							+ BaseColumns._ID + " = ?) AND feed_id = ?",
					new String[] { String.valueOf(baseId),
							String.valueOf(feedId) }, null, null,
					"updated DESC LIMIT 1");
		}

		int id = 0;

		if (c.moveToFirst()) {
			id = c.getInt(0);
		}

		c.close();

		return id;
	}

	public void onCatSelected(int catId) {
		Log.d(TAG, "onCatSelected");
		boolean browse = m_prefs.getBoolean("browse_cats_like_feeds", false);

		viewCategory(catId, browse);
	}
	
	public void viewCategory(int cat, boolean openAsFeed) {

		Log.d(TAG, "viewCategory");

		if (!openAsFeed) {
			OfflineFeedsFragment frag = new OfflineFeedsFragment(cat);

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			
			if (isSmallScreen()) {			
				ft.replace(R.id.fragment_container, frag, FRAG_FEEDS);
			} else {				
				ft.replace(R.id.feeds_fragment, frag, FRAG_FEEDS);
			}
			ft.commit();			
			
			m_activeCatId = cat;
			
		} else {
			if (m_menu != null) {
				MenuItem search = m_menu.findItem(R.id.search);
			
				if (search != null && !isCompatMode()) {
					SearchView sv = (SearchView) search.getActionView();
					sv.setQuery("", false);				
				}
			}
			viewFeed(cat, true);
		}

		initMainMenu();
	}

	@Override
	public void onFeedSelected(int feedId) {
		viewFeed(feedId);
	}
	
	public void viewFeed(int feedId) {
		viewFeed(feedId, false);
	}
	
	public void viewFeed(int feedId, boolean isCat) {
		m_activeFeedId = feedId;
		m_activeFeedIsCat = isCat;

		initMainMenu();

		deselectAllArticles();

		if (m_menu != null) {
			MenuItem search = m_menu.findItem(R.id.search);
		
			if (search != null && !isCompatMode()) {
				SearchView sv = (SearchView) search.getActionView();
				sv.setQuery("", false);				
			}
		}
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		OfflineHeadlinesFragment frag = new OfflineHeadlinesFragment(feedId, isCat);
		
		if (isSmallScreen()) {
			ft.replace(R.id.fragment_container, frag, FRAG_HEADLINES);
		} else {
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
			ft.replace(R.id.headlines_fragment, frag, FRAG_HEADLINES);
		}
		ft.commit();

	}

	@Override
	public void openArticle(int articleId, int compatAnimation) {
		m_selectedArticleId = articleId;

		initMainMenu();

		/* OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		if (hf != null) {
			hf.setActiveArticleId(articleId);
		} */

		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET unread = 0 " + "WHERE " + BaseColumns._ID
						+ " = ?");

		stmt.bindLong(1, articleId);
		stmt.execute();
		stmt.close();

		Fragment frag;
		
		if (isSmallScreen() || m_prefs.getBoolean("tablet_article_swipe", false)) {
			frag = new OfflineArticlePager(articleId);
		} else {
			frag = new OfflineArticleFragment(articleId);
		}

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		
		if (isSmallScreen()) {
			ft.hide(getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES));
			ft.add(R.id.fragment_container, frag, FRAG_ARTICLE);
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(getOrientation() % 2 != 0 ? View.GONE : View.VISIBLE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);			
			ft.replace(R.id.article_fragment, frag, FRAG_ARTICLE);
			
			refreshViews();
		}
		
		ft.commit();
	}

	@Override
	public int getSelectedArticleId() {
		return m_selectedArticleId;
	}

	@Override
	public void setSelectedArticleId(int articleId) {
		m_selectedArticleId = articleId;
		initMainMenu();
		refreshViews();
	}
	
	private void closeCategory() {
		m_activeCatId = -1;
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		if (isSmallScreen()) {
			ft.replace(R.id.fragment_container, new OfflineFeedCategoriesFragment(), FRAG_CATS);
		} else {
			ft.replace(R.id.feeds_fragment, new OfflineFeedCategoriesFragment(), FRAG_CATS);
		}
		ft.commit();
		
		initMainMenu();
		
		refreshViews();
	}

	@Override
	public boolean activeFeedIsCat() {
		return m_activeFeedIsCat;
	}
	
	private void updateTitle() {
		if (!isCompatMode()) {
			
			m_navigationAdapter.clear();

			if (m_activeCatId != -1 || (m_activeFeedId != 0 && (isSmallScreen() || getOrientation() % 2 != 0))) {
				getActionBar().setDisplayShowTitleEnabled(false);
				getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
				
				m_navigationAdapter.add(new RootNavigationEntry(getString(R.string.app_name)));
				
				if (m_activeCatId != -1) {
					Cursor cat = getCatById(m_activeCatId);
					String title = cat.getString(cat.getColumnIndex("title"));
					m_navigationAdapter.add(new CategoryNavigationEntry(m_activeCatId, title));
					cat.close();
				}

				if (m_activeFeedId != 0) {
					Cursor feed = null; 
					if (m_activeFeedIsCat) {
						feed = getCatById(m_activeFeedId);
					} else {
						feed = getFeedById(m_activeFeedId);
					}
					String title = feed.getString(feed.getColumnIndex("title"));						
					m_navigationAdapter.add(new FeedNavigationEntry(m_activeFeedId, title));
					feed.close();
				}

				//if (m_selectedArticle != null)
				//	m_navigationAdapter.add(new ArticleNavigationEntry(m_selectedArticle));

				getActionBar().setSelectedNavigationItem(getActionBar().getNavigationItemCount());
			
			} else {
				getActionBar().setDisplayShowTitleEnabled(true);
				getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
				getActionBar().setTitle(R.string.app_name);
			}
			
			if (isSmallScreen()) {
				getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticleId != 0 || m_activeFeedId != 0 || m_activeCatId != -1);
			} else {					
				getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticleId != 0 || m_activeCatId != -1);
			}
			
		} else {
			if (m_activeFeedId != 0) {
				if (!m_activeFeedIsCat) {
					Cursor feed = getFeedById(m_activeFeedId);
				
					if (feed != null) {					
						setTitle(feed.getString(feed.getColumnIndex("title")));
						feed.close();
					}
				} else {
					Cursor cat = getCatById(m_activeFeedId);
					
					if (cat != null) {					
						setTitle(cat.getString(cat.getColumnIndex("title")));
						cat.close();
					}
				}
			} else if (m_activeCatId != -1) {
				Cursor cat = getCatById(m_activeCatId);
			
				if (cat != null) {					
					setTitle(cat.getString(cat.getColumnIndex("title")));
					cat.close();
				}
			
			} else {
				setTitle(R.string.app_name);
			} 
					
		}
	}
}