package org.fox.ttrss;

import org.fox.ttrss.OnlineServices.RelativeArticle;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.TextView;

public class OfflineActivity extends FragmentActivity implements
		OfflineServices {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private String m_themeName = "";
	private Menu m_menu;
	private boolean m_smallScreenMode;
	private boolean m_unreadOnly = true;
	private boolean m_unreadArticlesOnly = true;
	private boolean m_compatMode = false;
	private boolean m_enableCats = false;

	private int m_activeFeedId = 0;
	private int m_selectedArticleId = 0;

	private SQLiteDatabase m_readableDb;
	private SQLiteDatabase m_writableDb;

	@Override
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}

	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;
	
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
		initDatabase();

		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		m_compatMode = android.os.Build.VERSION.SDK_INT <= 10;

		if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK")) {
			setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}

		super.onCreate(savedInstanceState);

		NotificationManager nmgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nmgr.cancel(OfflineDownloadService.NOTIFY_DOWNLOADING);

		m_themeName = m_prefs.getString("theme", "THEME_DARK");

		if (savedInstanceState != null) {
			m_unreadOnly = savedInstanceState.getBoolean("unreadOnly");
			m_unreadArticlesOnly = savedInstanceState
					.getBoolean("unreadArticlesOnly");
			m_activeFeedId = savedInstanceState.getInt("offlineActiveFeedId");
			m_selectedArticleId = savedInstanceState.getInt("offlineArticleId");
		}

		m_enableCats = m_prefs.getBoolean("enable_cats", false);

		m_smallScreenMode = m_compatMode || (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) != 
				Configuration.SCREENLAYOUT_SIZE_XLARGE;

		setContentView(R.layout.main);

		Log.d(TAG, "m_smallScreenMode=" + m_smallScreenMode);
		Log.d(TAG, "m_compatMode=" + m_compatMode);

		if (!m_compatMode) {
			if (android.os.Build.VERSION.SDK_INT < 14) {
				new TransitionHelper((ViewGroup) findViewById(R.id.main));
			}
			
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
		}

		initMainMenu();

		findViewById(R.id.loading_container).setVisibility(View.GONE);

		if (m_smallScreenMode) {
			if (m_selectedArticleId != 0) {
				findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				findViewById(R.id.cats_fragment).setVisibility(View.GONE);
				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
			} else if (m_activeFeedId != 0) {
				findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				findViewById(R.id.article_fragment).setVisibility(View.GONE);
				findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			} else {
				//findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
				// findViewById(R.id.article_fragment).setVisibility(View.GONE);

				/*
				 * if (m_enableCats && m_activeCategory == null) {
				 * findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				 * findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
				 * } else {
				 * findViewById(R.id.cats_fragment).setVisibility(View.GONE); }
				 */

				findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			}
		} else {
			if (m_selectedArticleId == 0) {
				findViewById(R.id.article_fragment).setVisibility(View.GONE);

				/*
				 * if (!m_enableCats || m_activeCategory != null)
				 * findViewById(R.id.cats_fragment).setVisibility(View.GONE);
				 * else
				 * findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				 */

				findViewById(R.id.cats_fragment).setVisibility(View.GONE);

			} else {
				findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
				findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			}
		}

		if (m_activeFeedId == 0) {
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			OfflineFeedsFragment frag = new OfflineFeedsFragment();
			ft.replace(R.id.feeds_fragment, frag);
			ft.commit();
		}
	}

	private void initDatabase() {
		DatabaseHelper dh = new DatabaseHelper(getApplicationContext());
		m_writableDb = dh.getWritableDatabase();
		m_readableDb = dh.getReadableDatabase();
	}

	@Override
	public synchronized SQLiteDatabase getReadableDb() {
		return m_readableDb;
	}

	@Override
	public synchronized SQLiteDatabase getWritableDb() {
		return m_writableDb;
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

	private void setLoadingStatus(int status, boolean showProgress) {
		TextView tv = (TextView) findViewById(R.id.loading_message);

		if (tv != null) {
			tv.setText(status);
		}

		View pb = findViewById(R.id.loading_progress);

		if (pb != null) {
			pb.setVisibility(showProgress ? View.VISIBLE : View.GONE);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putBoolean("unreadOnly", m_unreadOnly);
		out.putBoolean("unreadArticlesOnly", m_unreadArticlesOnly);
		out.putInt("offlineActiveFeedId", m_activeFeedId);
		out.putInt("offlineArticleId", m_selectedArticleId);
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

	private void setMenuLabel(int id, int labelId) {
		MenuItem mi = m_menu.findItem(id);

		if (mi != null) {
			mi.setTitle(labelId);
		}
	}

	private void goBack(boolean allowQuit) {
		if (m_smallScreenMode) {
			if (m_selectedArticleId != 0) {
				closeArticle();
			} else if (m_activeFeedId != 0) {
				//if (m_compatMode) {
					findViewById(R.id.main).setAnimation(
							AnimationUtils.loadAnimation(this,
									R.anim.slide_right));
				//}

				/*
				 * if (m_activeFeed != null && m_activeFeed.is_cat) {
				 * findViewById
				 * (R.id.headlines_fragment).setVisibility(View.GONE);
				 * findViewById(R.id.cats_fragment).setVisibility(View.VISIBLE);
				 * 
				 * refreshCategories(); } else {
				 */
				findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
				findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);
				// }
				m_activeFeedId = 0;
				
				OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
						.findFragmentById(R.id.feeds_fragment);
				
				if (ff != null) {
					ff.setSelectedFeedId(0);
				}
				
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.replace(R.id.headlines_fragment, new OfflineHeadlinesFragment());
				ft.commit();
				
				refreshViews();
				initMainMenu();

			} else if (allowQuit) {
				finish();
			}
		} else {
			if (m_selectedArticleId != 0) {
				closeArticle();
			} else if (m_activeFeedId != 0) {
				findViewById(R.id.headlines_fragment).setVisibility(View.INVISIBLE);
				m_activeFeedId = 0;
				
				OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
						.findFragmentById(R.id.feeds_fragment);
				
				if (ff != null) {
					ff.setSelectedFeedId(0);
				}

				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.replace(R.id.headlines_fragment, new OfflineHeadlinesFragment());
				ft.commit();

				refreshViews();
				initMainMenu();
				
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
	 * if (m_smallScreenMode) { if (m_selectedArticleId != 0) { closeArticle();
	 * } else if (m_activeFeedId != 0) { if (m_compatMode) {
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

	private Intent getShareIntent(Cursor article) {
		String title = article.getString(article.getColumnIndex("title"));
		String link = article.getString(article.getColumnIndex("link"));

		Intent intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, title);
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
				.findFragmentById(R.id.headlines_fragment);

		if (ohf != null) {
			ohf.refresh();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentById(R.id.headlines_fragment);

		switch (item.getItemId()) {
		case android.R.id.home:
			goBack(false);
			return true;
		case R.id.search:
			if (ohf != null && m_compatMode) {
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
			shareArticle(m_selectedArticleId);
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
				.findFragmentById(R.id.feeds_fragment);

		if (frag != null) {
			frag.refresh();
		}
	}

	private void closeArticle() {
		if (m_smallScreenMode) {
			findViewById(R.id.main).setAnimation(
					AnimationUtils.loadAnimation(this, R.anim.slide_right));
		}

		if (m_smallScreenMode) {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.article_fragment).setVisibility(View.GONE);
			findViewById(R.id.feeds_fragment).setVisibility(View.VISIBLE);

		}

		// we don't want to lose selected article in headlines so we refresh them before setting selected id to 0
		refreshViews();

		m_selectedArticleId = 0;
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.article_fragment, new DummyFragment());
		ft.commit();

		initMainMenu();
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
				if (m_compatMode) {
					m_menu.setGroupVisible(R.id.menu_group_headlines_selection, true);
				} else {
					if (m_headlinesActionMode == null)
						m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
				}
			} else if (m_selectedArticleId != 0) {
				m_menu.setGroupVisible(R.id.menu_group_article, true);
			} else if (m_activeFeedId != 0) {
				m_menu.setGroupVisible(R.id.menu_group_headlines, true);
				
				MenuItem search = m_menu.findItem(R.id.search);
				
				if (!m_compatMode) {
					SearchView searchView = (SearchView) search.getActionView();
					searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
						private String query = "";
						
						@Override
						public boolean onQueryTextSubmit(String query) {
							OfflineHeadlinesFragment frag = (OfflineHeadlinesFragment) getSupportFragmentManager()
									.findFragmentById(R.id.headlines_fragment);
							
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
										.findFragmentById(R.id.headlines_fragment);
								
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
			
			if (!m_compatMode) {
				
				if (m_activeFeedId != 0) {
					Cursor feed = getFeedById(m_activeFeedId);
					
					if (feed != null) {					
						getActionBar().setTitle(feed.getString(feed.getColumnIndex("title")));
					}
				} else {
					getActionBar().setTitle(R.string.app_name);
				}
				
				if (!m_smallScreenMode) {
					getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticleId != 0);
				} else {
					getActionBar().setDisplayHomeAsUpEnabled(m_selectedArticleId != 0 || m_activeFeedId != 0);
					getActionBar().setDisplayShowTitleEnabled(m_selectedArticleId == 0);
				}
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_readableDb.close();
		m_writableDb.close();

	}

	private void refreshViews() {
		refreshFeeds();
		refreshHeadlines();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentById(R.id.headlines_fragment);
		OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentById(R.id.feeds_fragment);

		switch (item.getItemId()) {
		case R.id.browse_articles:
			// TODO cat stuff
			return true;
		case R.id.browse_feeds:
			// TODO cat stuff
			return true;
		case R.id.catchup_category:
			// TODO cat stuff
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
						.findFragmentById(R.id.headlines_fragment);

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
						.findFragmentById(R.id.headlines_fragment);

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

	@Override
	public void viewFeed(int feedId) {
		m_activeFeedId = feedId;

		initMainMenu();

		if (m_smallScreenMode) {
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.headlines_fragment).setVisibility(View.VISIBLE);
		}

		deselectAllArticles();

		if (m_menu != null) {
			MenuItem search = m_menu.findItem(R.id.search);
		
			if (search != null && !m_compatMode) {
				SearchView sv = (SearchView) search.getActionView();
				sv.setQuery("", false);				
			}
		}
		
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		OfflineHeadlinesFragment frag = new OfflineHeadlinesFragment();
		ft.replace(R.id.headlines_fragment, frag);
		ft.commit();

	}

	@Override
	public void openArticle(int articleId, int compatAnimation) {
		m_selectedArticleId = articleId;

		initMainMenu();

		OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentById(R.id.headlines_fragment);

		if (hf != null) {
			hf.setActiveArticleId(articleId);
		}

		SQLiteStatement stmt = getWritableDb().compileStatement(
				"UPDATE articles SET unread = 0 " + "WHERE " + BaseColumns._ID
						+ " = ?");

		stmt.bindLong(1, articleId);
		stmt.execute();
		stmt.close();

		Fragment frag;
		
		if (m_smallScreenMode) {
			frag = new OfflineArticlePager(articleId);
		} else {
			frag = new OfflineArticleFragment(articleId);
		}

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.article_fragment, frag);
		ft.commit();
		
		if (m_smallScreenMode) {
			if (compatAnimation == 0)
				findViewById(R.id.main).setAnimation(
						AnimationUtils.loadAnimation(this, R.anim.slide_left));
			else
				findViewById(R.id.main).setAnimation(
						AnimationUtils.loadAnimation(this, compatAnimation));
		}

		if (m_smallScreenMode) {
			findViewById(R.id.headlines_fragment).setVisibility(View.GONE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.feeds_fragment).setVisibility(View.GONE);
			findViewById(R.id.cats_fragment).setVisibility(View.GONE);
			findViewById(R.id.article_fragment).setVisibility(View.VISIBLE);
		}

	}

	@Override
	public int getSelectedArticleId() {
		return m_selectedArticleId;
	}

	@Override
	public void setSelectedArticleId(int articleId) {
		m_selectedArticleId = articleId;
		refreshViews();
	}
}