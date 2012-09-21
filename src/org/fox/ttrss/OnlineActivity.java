package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.offline.OfflineActivity;
import org.fox.ttrss.offline.OfflineDownloadService;
import org.fox.ttrss.offline.OfflineUploadService;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.Label;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.ShareActionProvider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class OnlineActivity extends CommonActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	protected SharedPreferences m_prefs;
	protected Menu m_menu;

	protected int m_offlineModeStatus = 0;
	
	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;

	private BroadcastReceiver m_broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context content, Intent intent) {

			if (intent.getAction().equals(OfflineDownloadService.INTENT_ACTION_SUCCESS)) {
			
				m_offlineModeStatus = 2;
				
				switchOffline();
				
			} else if (intent.getAction().equals(OfflineUploadService.INTENT_ACTION_SUCCESS)) {
				Log.d(TAG, "offline upload service reports success");
				toast(R.string.offline_sync_success);
			}
		}
	};
	
	
	@TargetApi(11)
	private class HeadlinesActionModeCallback implements ActionMode.Callback {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();
				if (selected.size() > 0) {
					selected.clear();
					initMenu();
					hf.notifyUpdated();
				}
			}

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
	
	protected String getSessionId() {
		return GlobalState.getInstance().m_sessionId;
	}

	protected void setSessionId(String sessionId) {
		GlobalState.getInstance().m_sessionId = sessionId;
	}
	
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

		if (canUseProgress()) {
			requestWindowFeature(Window.FEATURE_PROGRESS);
		} else {
			requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		}
		
		setProgressBarVisibility(false);
		setProgressBarIndeterminateVisibility(false);

//		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);

		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		
		boolean isOffline = localPrefs.getBoolean("offline_mode_active", false);

		Log.d(TAG, "m_isOffline=" + isOffline);

		setContentView(R.layout.login);

		if (isOffline) {
			switchOfflineSuccess();			
		} else {
			
			/* if (getIntent().getExtras() != null) {
				Intent i = getIntent();
			} */
			
			if (savedInstanceState != null) {
				m_offlineModeStatus = savedInstanceState.getInt("offlineModeStatus");
			}
			
			if (!isCompatMode()) {
				m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
			}
		}
	}
	
	protected boolean canUseProgress() {
		return GlobalState.getInstance().m_canUseProgress;
	}

	private void switchOffline() {
		if (m_offlineModeStatus == 2) {
			
			AlertDialog.Builder builder = new AlertDialog.Builder(
					OnlineActivity.this)
					.setMessage(R.string.dialog_offline_success)
					.setPositiveButton(R.string.dialog_offline_go,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									
									m_offlineModeStatus = 0;
									
									SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
									SharedPreferences.Editor editor = localPrefs.edit();
									editor.putBoolean("offline_mode_active", true);
									editor.commit();
									
									Intent offline = new Intent(
											OnlineActivity.this,
											OfflineActivity.class);
									offline.putExtra("initial", true);
									startActivity(offline);
									finish();
								}
							})
					.setNegativeButton(R.string.dialog_cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {

									m_offlineModeStatus = 0;

								}
							});

			AlertDialog dlg = builder.create();
			dlg.show();
			
		} else if (m_offlineModeStatus == 0) {
		
			AlertDialog.Builder builder = new AlertDialog.Builder(this)
					.setMessage(R.string.dialog_offline_switch_prompt)
					.setPositiveButton(R.string.dialog_offline_go,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
	
									if (getSessionId() != null) {
										Log.d(TAG, "offline: starting");
										
										m_offlineModeStatus = 1;
	
										Intent intent = new Intent(
												OnlineActivity.this,
												OfflineDownloadService.class);
										intent.putExtra("sessionId", getSessionId());
	
										startService(intent);
									}
								}
							})
					.setNegativeButton(R.string.dialog_cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									//
								}
							});
	
			AlertDialog dlg = builder.create();
			dlg.show();
		} else if (m_offlineModeStatus == 1) {
			cancelOfflineSync();
		}
	}
	
	private boolean hasPendingOfflineData() {
		try {
			Cursor c = getReadableDb().query("articles",
					new String[] { "COUNT(*)" }, "modified = 1", null, null, null,
					null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();
	
				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed? ugh
		}

		return false;
	}

	private boolean hasOfflineData() {
		try {
			Cursor c = getReadableDb().query("articles",
					new String[] { "COUNT(*)" }, null, null, null, null, null);
			if (c.moveToFirst()) {
				int modified = c.getInt(0);
				c.close();
	
				return modified > 0;
			}
		} catch (IllegalStateException e) {
			// db is closed?
		}

		return false;
	}
	
	@Override
	public void onPause() {
		super.onPause();

		unregisterReceiver(m_broadcastReceiver);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void syncOfflineData() {
		Log.d(TAG, "offlineSync: starting");
		
		Intent intent = new Intent(
				OnlineActivity.this,
				OfflineUploadService.class);
		
		intent.putExtra("sessionId", getSessionId());

		startService(intent);
	}
	
	private void cancelOfflineSync() {		
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
		.setMessage(R.string.dialog_offline_sync_in_progress)
		.setNegativeButton(R.string.dialog_offline_sync_stop,
				new Dialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int which) {

						if (getSessionId() != null) {
							Log.d(TAG, "offline: stopping");
							
							m_offlineModeStatus = 0;

							Intent intent = new Intent(
									OnlineActivity.this,
									OfflineDownloadService.class);

							stopService(intent);
							
							dialog.dismiss();

							restart();
						}
					}
				})
		.setPositiveButton(R.string.dialog_offline_sync_continue,
				new Dialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int which) {
					
						dialog.dismiss();

						restart();
					}
				});

		AlertDialog dlg = builder.create();
		dlg.show();
	}
	
	public void restart() {
		Intent refresh = new Intent(OnlineActivity.this, OnlineActivity.class);
		startActivity(refresh);
		finish();
	}
	
	private void switchOfflineSuccess() {
		logout();
		// setLoadingStatus(R.string.blank, false);

		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("offline_mode_active", true);
		editor.commit();

		Intent offline = new Intent(OnlineActivity.this, OfflineActivity.class);
		offline.putExtra("initial", true);
		offline.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
	 	   
		startActivityForResult(offline, 0);

		finish();

	}
	
	public void login() {
		login(false);
	}
	
	public void login(boolean refresh) {
		if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

			setLoadingStatus(R.string.login_need_configure, false);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.dialog_need_configure_prompt)
			       .setCancelable(false)
			       .setPositiveButton(R.string.dialog_open_preferences, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			   			// launch preferences
			   			
			        	   Intent intent = new Intent(OnlineActivity.this,
			        			   PreferencesActivity.class);
			        	   startActivityForResult(intent, 0);
			           }
			       })
			       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
			
		} else {
			setLoadingStatus(R.string.login_in_progress, true);
			
			LoginRequest ar = new LoginRequest(getApplicationContext(), refresh);

			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("op", "login");
					put("user", m_prefs.getString("login", "").trim());
					put("password", m_prefs.getString("password", "").trim());
				}
			};

			ar.execute(map);

			setLoadingStatus(R.string.login_in_progress, true);
		}
	}
	
	protected void loginSuccess(boolean refresh) {
		setLoadingStatus(R.string.blank, false);
		
		initMenu();
	
		Intent intent = new Intent(OnlineActivity.this, FeedsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
 	   
		startActivityForResult(intent, 0);
	
		if (hasPendingOfflineData())
			syncOfflineData();
		
		List<PackageInfo> pkgs = getPackageManager()
				.getInstalledPackages(0);

		for (PackageInfo p : pkgs) {
			if ("org.fox.ttrss.key".equals(p.packageName)) {
				toast(R.string.donate_thanks);
				break;
			}
		}
		
		finish();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		final ArticlePager ap = (ArticlePager)getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.donate:
			if (true) {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW, 
						Uri.parse("market://details?id=org.fox.ttrss.key"));
					startActivity(intent);
				} catch (ActivityNotFoundException ae) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW, 
							Uri.parse("https://play.google.com/store/apps/details?id=org.fox.ttrss.key"));
						startActivity(intent);
					} catch (Exception e) {
						e.printStackTrace();
						toast(R.string.error_other_error);
					}
				}
			}
			return true;
		case R.id.logout:
			logout();
			return true;
		case R.id.login:
			login();
			return true;
		case R.id.go_offline:
			switchOffline();
			return true;
		case R.id.article_set_note:
			if (ap != null && ap.getSelectedArticle() != null) {
				editArticleNote(ap.getSelectedArticle());				
			}
			return true;
		case R.id.preferences:
			Intent intent = new Intent(OnlineActivity.this,
					PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.search:			
			if (hf != null && isCompatMode()) {
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
										
										hf.setSearchQuery(query);

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
		case R.id.headlines_mark_as_read:
			if (hf != null) {
				ArticleList articles = hf.getUnreadArticles();

				for (Article a : articles)
					a.unread = false;

				ApiRequest req = new ApiRequest(getApplicationContext()) {
					protected void onPostExecute(JsonElement result) {
						hf.refresh(false);
					}
				};

				final String articleIds = articlesToIdString(articles);

				@SuppressWarnings("serial")
				HashMap<String, String> map = new HashMap<String, String>() {
					{
						put("sid", getSessionId());
						put("op", "updateArticle");
						put("article_ids", articleIds);
						put("mode", "0");
						put("field", "2");
					}
				};
				req.execute(map);
			}
			return true;
		case R.id.headlines_select:
			if (hf != null) {
				Dialog dialog = new Dialog(this);
				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.headlines_select_dialog)
						.setSingleChoiceItems(
								new String[] {
										getString(R.string.headlines_select_all),
										getString(R.string.headlines_select_unread),
										getString(R.string.headlines_select_none) },
								0, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										switch (which) {
										case 0:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.ALL);
											break;
										case 1:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.UNREAD);
											break;
										case 2:
											hf.setSelection(HeadlinesFragment.ArticlesSelection.NONE);
											break;
										}
										dialog.cancel();
										initMenu();
									}
								});

				dialog = builder.create();
				dialog.show();
			}
			return true;
		case R.id.share_article:
			if (android.os.Build.VERSION.SDK_INT < 14) {
				if (ap != null) {
					shareArticle(ap.getSelectedArticle());
				}
			}
			return true;
		case R.id.toggle_marked:
			if (ap != null & ap.getSelectedArticle() != null) {
				Article a = ap.getSelectedArticle();
				a.marked = !a.marked;
				saveArticleMarked(a);
				if (hf != null) hf.notifyUpdated();
			}
			return true;
		case R.id.selection_select_none:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();
				if (selected.size() > 0) {
					selected.clear();
					initMenu();
					hf.notifyUpdated();
				}
			}
			return true;
		case R.id.selection_toggle_unread:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.unread = !a.unread;

					toggleArticlesUnread(selected);
					hf.notifyUpdated();
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.marked = !a.marked;

					toggleArticlesMarked(selected);
					hf.notifyUpdated();
				}
			}
			return true;
		case R.id.selection_toggle_published:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.published = !a.published;

					toggleArticlesPublished(selected);
					hf.notifyUpdated();
				}
			}
			return true;
		case R.id.toggle_published:
			if (ap != null && ap.getSelectedArticle() != null) {
				Article a = ap.getSelectedArticle();
				a.published = !a.published;
				saveArticlePublished(a);
				if (hf != null) hf.notifyUpdated();
			}
			return true;
		case R.id.catchup_above:
			if (hf != null) {
				if (ap != null && ap.getSelectedArticle() != null) {
					Article article = ap.getSelectedArticle();
					
					ArticleList articles = hf.getAllArticles();
					ArticleList tmp = new ArticleList();
					for (Article a : articles) {
						a.unread = false;
						tmp.add(a);
						if (article.id == a.id)
							break;
					}
					if (tmp.size() > 0) {
						toggleArticlesUnread(tmp);
						hf.notifyUpdated();
					}
				}
			}
			return true;
		case R.id.set_unread:
			if (ap != null && ap.getSelectedArticle() != null) {
				Article a = ap.getSelectedArticle();
				a.unread = true;
				saveArticleUnread(a);
				if (hf != null) hf.notifyUpdated();
			}
			return true;
		case R.id.set_labels:
			if (ap != null && ap.getSelectedArticle() != null) {
				editArticleLabels(ap.getSelectedArticle());				
			}
			return true;
		case R.id.update_headlines:
			if (hf != null) {
				hf.refresh(false);
			}
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	public void editArticleNote(final Article article) {
		String note = "";
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);  
		builder.setTitle(article.title);
		final EditText topicEdit = new EditText(this);
		topicEdit.setText(note);
		builder.setView(topicEdit);
		
		builder.setPositiveButton(R.string.article_set_note, new Dialog.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	saveArticleNote(article, topicEdit.getText().toString().trim());
	        	article.published = true;	        	
	        	saveArticlePublished(article);
	        	
	        	HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
	        	if (hf != null) hf.notifyUpdated();
	        }
	    });
		
		builder.setNegativeButton(R.string.dialog_cancel, new Dialog.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	//
	        }
	    });
		
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	
	public void editArticleLabels(Article article) {
		final int articleId = article.id;									

		ApiRequest req = new ApiRequest(getApplicationContext()) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (result != null) {
					Type listType = new TypeToken<List<Label>>() {}.getType();
					final List<Label> labels = new Gson().fromJson(result, listType);

					CharSequence[] items = new CharSequence[labels.size()];
					final int[] itemIds = new int[labels.size()];
					boolean[] checkedItems = new boolean[labels.size()];
					
					for (int i = 0; i < labels.size(); i++) {
						items[i] = labels.get(i).caption;
						itemIds[i] = labels.get(i).id;
						checkedItems[i] = labels.get(i).checked;
					}
					
					Dialog dialog = new Dialog(OnlineActivity.this);
					AlertDialog.Builder builder = new AlertDialog.Builder(OnlineActivity.this)
							.setTitle(R.string.article_set_labels)
							.setMultiChoiceItems(items, checkedItems, new OnMultiChoiceClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which, final boolean isChecked) {
									final int labelId = itemIds[which];
									
									@SuppressWarnings("serial")
									HashMap<String, String> map = new HashMap<String, String>() {
										{
											put("sid", getSessionId());
											put("op", "setArticleLabel");
											put("label_id", String.valueOf(labelId));
											put("article_ids", String.valueOf(articleId));
											if (isChecked) put("assign", "true");
										}
									};
									
									ApiRequest req = new ApiRequest(m_context);
									req.execute(map);
									
								}
							}).setPositiveButton(R.string.dialog_close, new OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.cancel();
								}
							});

					dialog = builder.create();
					dialog.show();

				}
			}
		};
		
		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "getLabels");
				put("article_id", String.valueOf(articleId));
			}
		};
		
		req.execute(map);
	}
	
	protected void logout() {
		setSessionId(null);
		
		findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
		setLoadingStatus(R.string.login_ready, false);

		initMenu();
	}
	
	protected void loginFailure() {
		setSessionId(null);
		initMenu();
		
		if (hasOfflineData()) {

			AlertDialog.Builder builder = new AlertDialog.Builder(
					OnlineActivity.this)
					.setMessage(R.string.dialog_offline_prompt)
					.setPositiveButton(R.string.dialog_offline_go,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									switchOfflineSuccess();
								}
							})
					.setNegativeButton(R.string.dialog_cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									//
								}
							});

			AlertDialog dlg = builder.create();
			dlg.show();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("offlineModeStatus", m_offlineModeStatus);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(OfflineDownloadService.INTENT_ACTION_SUCCESS);
		filter.addAction(OfflineUploadService.INTENT_ACTION_SUCCESS);
		filter.addCategory(Intent.CATEGORY_DEFAULT);

		registerReceiver(m_broadcastReceiver, filter);
		
		if (getSessionId() == null) {
			login();
		} else {
			loginSuccess(false);
		}
	}
	
	public Menu getMenu() {
		return m_menu;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		m_menu = menu;

		initMenu();
		
		List<PackageInfo> pkgs = getPackageManager()
				.getInstalledPackages(0);

		for (PackageInfo p : pkgs) {
			if ("org.fox.ttrss.key".equals(p.packageName)) {
				Log.d(TAG, "license apk found");
				menu.findItem(R.id.donate).setVisible(false);
				break;
			}
		}
		
		return true;
	}
	
	protected int getApiLevel() {
		return GlobalState.getInstance().m_apiLevel;
	}
	
	protected void setApiLevel(int apiLevel) {
		GlobalState.getInstance().m_apiLevel = apiLevel;
	}
	
	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleUnread(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.unread ? "1" : "0");
				put("field", "2");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleMarked(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				toast(article.marked ? R.string.notify_article_marked : R.string.notify_article_unmarked);
			}
		};

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.marked ? "1" : "0");
				put("field", "0");
			}
		};
		
		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticlePublished(final Article article) {

		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				toast(article.published ? R.string.notify_article_published : R.string.notify_article_unpublished);
			}
		};

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", article.published ? "1" : "0");
				put("field", "1");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings({ "unchecked", "serial" })
	public void saveArticleNote(final Article article, final String note) {
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				toast(R.string.notify_article_note_set);
			}
		};

		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", String.valueOf(article.id));
				put("mode", "1");
				put("data", note);
				put("field", "3");
			}
		};

		req.execute(map);
	}

	public static String articlesToIdString(ArticleList articles) {
		String tmp = "";

		for (Article a : articles)
			tmp += String.valueOf(a.id) + ",";

		return tmp.replaceAll(",$", "");
	}
	
	public void shareText(String text) {

		Intent intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, text);

		startActivity(Intent.createChooser(intent, text));
	}
	
	public void shareArticle(Article article) {
		if (article != null) {

			Intent intent = getShareIntent(article);
			
			startActivity(Intent.createChooser(intent,
					getString(R.string.share_article)));
		}
	}
	
	protected Intent getShareIntent(Article article) {
		Intent intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, article.title);
		intent.putExtra(Intent.EXTRA_TEXT, article.link);

		return intent;
	}
	
	@SuppressWarnings("unchecked")
	public void catchupFeed(final Feed feed) {
		Log.d(TAG, "catchupFeed=" + feed);

		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				// refresh?
			}
		};

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "catchupFeed");
				put("feed_id", String.valueOf(feed.id));
				if (feed.is_cat)
					put("is_cat", "1");
			}
		};

		req.execute(map);
	}
	
	@SuppressWarnings("unchecked")
	public void toggleArticlesMarked(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "0");
			}
		};

		req.execute(map);
	}

	@SuppressWarnings("unchecked")
	public void toggleArticlesUnread(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "2");
			}
		};

		req.execute(map);
		//refresh();
	}

	@SuppressWarnings("unchecked")
	public void toggleArticlesPublished(final ArticleList articles) {
		ApiRequest req = new ApiRequest(getApplicationContext());

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "updateArticle");
				put("article_ids", articlesToIdString(articles));
				put("mode", "2");
				put("field", "1");
			}
		};

		req.execute(map);
	}
	
	
	protected void initMenu() {
		if (m_menu != null) {			
			if (getSessionId() != null) {
				m_menu.setGroupVisible(R.id.menu_group_logged_in, true);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, false);
			} else {
				m_menu.setGroupVisible(R.id.menu_group_logged_in, false);
				m_menu.setGroupVisible(R.id.menu_group_logged_out, true);				
			}
			
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_headlines_selection, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			
			m_menu.findItem(R.id.set_labels).setEnabled(getApiLevel() >= 1);
			m_menu.findItem(R.id.article_set_note).setEnabled(getApiLevel() >= 1);
			
			MenuItem search = m_menu.findItem(R.id.search);
			search.setEnabled(getApiLevel() >= 2);
			
			if (android.os.Build.VERSION.SDK_INT >= 14) {			
				ShareActionProvider shareProvider = (ShareActionProvider) m_menu.findItem(R.id.share_article).getActionProvider();

				ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
				
				if (af != null && af.getSelectedArticle() != null) {
					shareProvider.setShareIntent(getShareIntent(af.getSelectedArticle()));
					
					if (!isSmallScreen()) {
						m_menu.findItem(R.id.share_article).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
					}
				}
			}
			
			if (!isCompatMode()) {
				HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				
				if (hf != null) {
					if (hf.getSelectedArticles().size() > 0 && m_headlinesActionMode == null) {
						m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
					} else if (hf.getSelectedArticles().size() == 0 && m_headlinesActionMode != null) { 
						m_headlinesActionMode.finish();
					}
				}
				
				SearchView searchView = (SearchView) search.getActionView();
				searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
					private String query = "";
					
					@Override
					public boolean onQueryTextSubmit(String query) {
						HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
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
							HeadlinesFragment frag = (HeadlinesFragment) getSupportFragmentManager()
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
	
	protected void refresh(boolean includeHeadlines) {
		FeedCategoriesFragment cf = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		if (cf != null) {
			cf.refresh(false);
		}

		FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
		
		if (ff != null) {
			ff.refresh(false);
		}

		if (includeHeadlines) {
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		
			if (hf != null) {
				hf.refresh(false);
			}
			
			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			if (af != null) {
				af.refresh(false);
			}
		}
	}
	
	protected void refresh() {
		refresh(true);
	}
	
	private class LoginRequest extends ApiRequest {
		boolean m_refreshAfterLogin = false;
		
		public LoginRequest(Context context, boolean refresh) {
			super(context);
			m_refreshAfterLogin = refresh;
		}

		@SuppressWarnings("unchecked")
		protected void onPostExecute(JsonElement result) {
			if (result != null) {
				try {
					JsonObject content = result.getAsJsonObject();
					if (content != null) {
						setSessionId(content.get("session_id").getAsString());

						GlobalState.getInstance().m_canUseProgress = m_canUseProgress;
						
						Log.d(TAG, "Authenticated! canUseProgress=" + m_canUseProgress);

						ApiRequest req = new ApiRequest(m_context) {
							protected void onPostExecute(JsonElement result) {
								setApiLevel(0);

								if (result != null) {
									try {
										setApiLevel(result.getAsJsonObject().get("level").getAsInt());
									} catch (Exception e) {
										e.printStackTrace();
									}
								} else {
									setLoadingStatus(getErrorMessage(), false);
									loginFailure();
									return;
								}

								Log.d(TAG, "Received API level: " + getApiLevel());

								loginSuccess(m_refreshAfterLogin);

								return;
							}
						};

						@SuppressWarnings("serial")
						HashMap<String, String> map = new HashMap<String, String>() {
							{
								put("sid", getSessionId());
								put("op", "getApiLevel");
							}
						};

						req.execute(map);

						setLoadingStatus(R.string.loading_message, true);

						return;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			setSessionId(null);
			setLoadingStatus(getErrorMessage(), false);
			
			loginFailure();
		}

	}
}
