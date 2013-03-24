package org.fox.ttrss.offline;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.ShareActionProvider;

public class OfflineActivity extends CommonActivity {
	private final String TAG = this.getClass().getSimpleName();

	protected SharedPreferences m_prefs;
	protected Menu m_menu;
	
	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;

	@SuppressLint("NewApi")
	private class HeadlinesActionModeCallback implements ActionMode.Callback {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			deselectAllArticles();
			m_headlinesActionMode = null;
			initMenu();
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
		} else if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_SEPIA")) {
			setTheme(R.style.SepiaTheme);
		} else if (m_prefs.getString("theme", "THEME_DARK").equals("THEME_DARK_GRAY")) {
			setTheme(R.style.DarkGrayTheme);
		} else {
			setTheme(R.style.LightTheme);
		}
		
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_PROGRESS);

		setProgressBarVisibility(false);
		
		setContentView(R.layout.login);
		
		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);
		
		initMenu();

		Intent intent = getIntent();
		
		if (intent.getExtras() != null) {
			if (intent.getBooleanExtra("initial", false)) {
				intent = new Intent(OfflineActivity.this, OfflineFeedsActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		 	   
				startActivityForResult(intent, 0);
				finish();
			}
		}
		
		/* if (savedInstanceState != null) {

		} */

		if (!isCompatMode()) {
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
		}

	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
	}
	
	protected void selectArticles(int feedId, boolean isCat, int mode) {
		switch (mode) {
		case 0:
			SQLiteStatement stmtSelectAll = null;
			
			if (isCat) {
				stmtSelectAll = getWritableDb().compileStatement(
						"UPDATE articles SET selected = 1 WHERE feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
			} else {
				stmtSelectAll = getWritableDb().compileStatement(
								"UPDATE articles SET selected = 1 WHERE feed_id = ?");
			}
			
			stmtSelectAll.bindLong(1, feedId);
			stmtSelectAll.execute();
			stmtSelectAll.close();

			break;
		case 1:

			SQLiteStatement stmtSelectUnread = null;
			
			if (isCat) {
				stmtSelectUnread = getWritableDb().compileStatement(
						"UPDATE articles SET selected = 1 WHERE feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?) AND unread = 1");
			} else {
				stmtSelectUnread = getWritableDb().compileStatement(
								"UPDATE articles SET selected = 1 WHERE feed_id = ? AND unread = 1");
			}
			
			stmtSelectUnread.bindLong(1, feedId);
			stmtSelectUnread.execute();
			stmtSelectUnread.close();

			break;
		case 2:
			deselectAllArticles();
			break;
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {		
		final OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		/* final OfflineFeedsFragment off = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS); */
		
		/* final OfflineFeedCategoriesFragment ocf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS); */

		final OfflineArticlePager oap = (OfflineArticlePager) getSupportFragmentManager()
				.findFragmentByTag(FRAG_ARTICLE);

		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.go_online:
			switchOnline();
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

								selectArticles(ohf.getFeedId(), ohf.getFeedIsCat(), which);
								initMenu();
								refresh();

								dialog.cancel();
							}
						});

				dialog = builder.create();
				dialog.show();
			}
			return true;
		case R.id.headlines_mark_as_read:
			if (ohf != null) {
				int feedId = ohf.getFeedId();
				boolean isCat = ohf.getFeedIsCat();
				
				SQLiteStatement stmt = null;
				
				if (isCat) {
					stmt = getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");						
				} else {
					stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id = ?");
				}
				stmt.bindLong(1, feedId);
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.share_article:
			if (android.os.Build.VERSION.SDK_INT < 14 && oap != null && android.os.Build.VERSION.SDK_INT < 14) {
				int articleId = oap.getSelectedArticleId();
				
				shareArticle(articleId);
			}
			return true;
		case R.id.toggle_marked:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();
				
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, marked = NOT marked WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.selection_select_none:
			deselectAllArticles();			
			return true;
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, unread = NOT unread WHERE selected = 1");
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, marked = NOT marked WHERE selected = 1");
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, published = NOT published WHERE selected = 1");
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.toggle_published:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();
				
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, published = NOT published WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.catchup_above:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();
				int feedId = oap.getFeedId();
				boolean isCat = oap.getFeedIsCat();

				SQLiteStatement stmt = null;
				
				if (isCat) {
					stmt = getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated >= (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");						
				} else {
					stmt = getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated >= (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND feed_id = ?");						
				}
				
				stmt.bindLong(1, articleId);
				stmt.bindLong(2, feedId);
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		case R.id.set_unread:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();
				
				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, unread = 1 WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
				
				refresh();
			}
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.offline_menu, menu);

		m_menu = menu;

		initMenu();
		
		return true;
	}
	
	@SuppressLint("NewApi")
	protected void initMenu() {
		if (m_menu != null) {
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			
			if (android.os.Build.VERSION.SDK_INT >= 14) {			
				ShareActionProvider shareProvider = (ShareActionProvider) m_menu.findItem(R.id.share_article).getActionProvider();

				OfflineArticlePager af = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				
				if (af != null && af.getSelectedArticleId() > 0) {
					shareProvider.setShareIntent(getShareIntent(getArticleById(af.getSelectedArticleId())));
					
					if (!isSmallScreen()) {
						m_menu.findItem(R.id.share_article).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
					}
				}
			}
			
			if (!isCompatMode()) {
				MenuItem search = m_menu.findItem(R.id.search);
				
				OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				
				if (hf != null) {
					if (hf.getSelectedArticleCount() > 0 && m_headlinesActionMode == null) {
						m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
					} else if (hf.getSelectedArticleCount() == 0 && m_headlinesActionMode != null) { 
						m_headlinesActionMode.finish();
					}
				}
				
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
		}		
	}
	
	private void switchOnline() {
		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.putBoolean("offline_mode_active", false);
		editor.commit();

		Intent refresh = new Intent(this, org.fox.ttrss.OnlineActivity.class);
		startActivity(refresh);
		finish();
	}
	
	protected Cursor getArticleById(int articleId) {
		Cursor c = getReadableDb().query("articles", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(articleId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {		
		if (m_prefs.getBoolean("use_volume_keys", false)) {
			OfflineArticlePager ap = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			if (ap != null && ap.isAdded()) {			
				switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP:
					ap.selectArticle(false);					
					return true;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					ap.selectArticle(true);
					return true;
				}
			}
		}
		
		return super.onKeyDown(keyCode, event);			
	}
	
	// Handle onKeyUp too to suppress beep
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (m_prefs.getBoolean("use_volume_keys", false)) {
					
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:	
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				return true;
			}
		}
		
		return super.onKeyUp(keyCode, event);		
	}
	
	protected Cursor getFeedById(int feedId) {
		Cursor c = getReadableDb().query("feeds", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(feedId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	protected Cursor getCatById(int catId) {
		Cursor c = getReadableDb().query("categories", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(catId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	protected Intent getShareIntent(Cursor article) {
		if (article != null) {
			String title = article.getString(article.getColumnIndex("title"));
			String link = article.getString(article.getColumnIndex("link"));
	
			Intent intent = new Intent(Intent.ACTION_SEND);
	
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, title);
			intent.putExtra(Intent.EXTRA_TEXT, link);
	
			return intent;
		} else {
			return null;
		}
	}
	
	protected void shareArticle(int articleId) {

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
					getString(R.string.share_article)));
		}
	}

	protected int getSelectedArticleCount() {
		Cursor c = getReadableDb().query("articles",
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null,
				null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();

		return selected;
	}

	protected void deselectAllArticles() {
		getWritableDb().execSQL("UPDATE articles SET selected = 0 ");
		refresh();
	}

	protected void refresh() {
		OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);

		if (ff != null) {
			ff.refresh();
		}

		OfflineFeedCategoriesFragment cf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		if (cf != null) {
			cf.refresh();
		}

		OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		if (ohf != null) {
			ohf.refresh();
		} 
	}

}
