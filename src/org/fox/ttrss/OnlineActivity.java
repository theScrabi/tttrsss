package org.fox.ttrss;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

import org.fox.ttrss.offline.OfflineActivity;
import org.fox.ttrss.offline.OfflineDownloadService;
import org.fox.ttrss.offline.OfflineUploadService;
import org.fox.ttrss.share.SubscribeActivity;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.Label;
import org.fox.ttrss.widget.SmallWidgetProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshAttacher;
import android.animation.ObjectAnimator;
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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SearchView;
import android.widget.TextView;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;


public class OnlineActivity extends CommonActivity {
	private final String TAG = this.getClass().getSimpleName();

	private final static int TRIAL_DAYS = 8;
	
	protected SharedPreferences m_prefs;
	protected Menu m_menu;

	protected int m_offlineModeStatus = 0;
	
	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;

	private String m_lastImageHitTestUrl;

	protected PullToRefreshAttacher m_pullToRefreshAttacher;

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
			m_headlinesActionMode = null;

			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();
				if (selected.size() > 0) {
					selected.clear();
					initMenu();
					hf.notifyUpdated();
				}
			}
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {

			MenuInflater inflater = getSupportMenuInflater();
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
		ApiRequest.disableConnectionReuseIfNecessary();
		
		// we use that before parent onCreate so let's init locally
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

		super.onCreate(savedInstanceState);

		if (canUseProgress()) {
			requestWindowFeature(Window.FEATURE_PROGRESS);
		}

		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setProgressBarVisibility(false);
		setProgressBarIndeterminateVisibility(false);

//		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);

		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		
		boolean isOffline = localPrefs.getBoolean("offline_mode_active", false);

		Log.d(TAG, "m_isOffline=" + isOffline);

		setContentView(R.layout.login);

		m_pullToRefreshAttacher = PullToRefreshAttacher.get(this);

		if (isOffline) {
			switchOfflineSuccess();			
		} else {
			
			if (!isAmazonDevice()) {
				checkTrial(false);
			}
			
			/* if (getIntent().getExtras() != null) {
				Intent i = getIntent();
			} */
			
			if (savedInstanceState != null) {
				m_offlineModeStatus = savedInstanceState.getInt("offlineModeStatus");
			}
			
			m_headlinesActionModeCallback = new HeadlinesActionModeCallback();
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
	public void onStop() {
		super.onStop();
		
		Intent initialUpdateIntent = new Intent(SmallWidgetProvider.FORCE_UPDATE_ACTION);
		sendBroadcast(initialUpdateIntent);
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
		overridePendingTransition(0, 0);
	
		if (hasPendingOfflineData())
			syncOfflineData();
		
		finish();
	}
	
	public void checkTrial(boolean notify) {
		boolean isTrial = getPackageManager().checkSignatures(
				getPackageName(), "org.fox.ttrss.key") != PackageManager.SIGNATURE_MATCH;

		if (isTrial) {
			long firstStart = m_prefs.getLong("date_firstlaunch_trial", -1);
			
			if (firstStart == -1) {
				firstStart = System.currentTimeMillis();
				
				SharedPreferences.Editor editor = m_prefs.edit();
				editor.putLong("date_firstlaunch_trial", firstStart);
				editor.commit();
			}
			
			if (!notify && System.currentTimeMillis() > firstStart + (TRIAL_DAYS * 24 * 60 * 60 * 1000)) {
				
				AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.trial_expired)
				.setMessage(R.string.trial_expired_message)
				.setCancelable(false)
				.setPositiveButton(getString(R.string.trial_purchase),
						new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								
								openUnlockUrl();								
								finish();

							}
						})
				.setNegativeButton(getString(R.string.cancel),
						new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								
								finish();

							}
						});
		
				AlertDialog dialog = builder.create();
				dialog.show();
				
			} else {
				long daysLeft = Math.round((firstStart + (TRIAL_DAYS * 24 * 60 * 60 * 1000) - System.currentTimeMillis()) / (24 * 60 * 60 * 1000));
				
				if (notify) {
					toast(getString(R.string.trial_mode_prompt, Long.valueOf(daysLeft)));
				}
			}
		} else if (notify) {			
			//toast(R.string.trial_thanks);
		}
	}
	
	private void openUnlockUrl() {
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
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		/* AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo(); */
		
		final ArticlePager ap = (ArticlePager)getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
		
		switch (item.getItemId()) {
		case R.id.article_img_open:
			if (getLastContentImageHitTestUrl() != null) {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW, 
							Uri.parse(getLastContentImageHitTestUrl()));
					startActivity(intent);
				} catch (Exception e) {
					e.printStackTrace();
					toast(R.string.error_other_error);
				}
			}			
			return true;
		case R.id.article_img_copy:
			if (getLastContentImageHitTestUrl() != null) {
				copyToClipboard(getLastContentImageHitTestUrl());
			}			
			return true;
		case R.id.article_img_share:
			if (getLastContentImageHitTestUrl() != null) {
				Intent intent = new Intent(Intent.ACTION_SEND);

				intent.setType("image/png");
				intent.putExtra(Intent.EXTRA_SUBJECT, getLastContentImageHitTestUrl());
				intent.putExtra(Intent.EXTRA_TEXT, getLastContentImageHitTestUrl());

				startActivity(Intent.createChooser(intent, getLastContentImageHitTestUrl()));
			}
			return true;
		case R.id.article_img_view_caption:
			if (getLastContentImageHitTestUrl() != null) {

                // Android doesn't give us an easy way to access title tags;
                // we'll use Jsoup on the body text to grab the title text
                // from the first image tag with this url. This will show
                // the wrong text if an image is used multiple times.
                Document doc = Jsoup.parse(ap.getSelectedArticle().content);
                Elements es = doc.getElementsByAttributeValue("src", getLastContentImageHitTestUrl());
                if (es.size() > 0){
                    if (es.get(0).hasAttr("title")){
                        Dialog dia = new Dialog(this);
                        if (es.get(0).hasAttr("alt")){
                            dia.setTitle(es.get(0).attr("alt"));
                        } else {
                            dia.setTitle(es.get(0).attr("title"));
                        }
                        TextView titleText = new TextView(this);
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        	titleText.setPaddingRelative(24, 24, 24, 24);
                        } else {
                        	titleText.setPadding(24, 24, 24, 24);
                        }
                        
                        titleText.setTextSize(16);
                        titleText.setText(es.get(0).attr("title"));
                        dia.setContentView(titleText);
                        dia.show();
                    } else {
                        toast(R.string.no_caption_to_display);
                    }
                } else {
                    toast(R.string.no_caption_to_display);
                }
            }
            return true;
		case R.id.article_link_share:
			if (ap != null && ap.getSelectedArticle() != null) {
				shareArticle(ap.getSelectedArticle());
			}
			return true;
		case R.id.article_link_copy:
			Log.d(TAG, "article_link_copy");
			if (ap != null && ap.getSelectedArticle() != null) {
				copyToClipboard(ap.getSelectedArticle().link);
			}
			return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		final ArticlePager ap = (ArticlePager)getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

		switch (item.getItemId()) {
		/* case android.R.id.home:
			finish();
			return true; */
		case R.id.headlines_toggle_sidebar:
			if (true && !isSmallScreen()) {
				View v = findViewById(R.id.headlines_fragment); 
				
				if (v != null) {
					SharedPreferences.Editor editor = m_prefs.edit();
					editor.putBoolean("headlines_hide_sidebar", !m_prefs.getBoolean("headlines_hide_sidebar", false));
					editor.commit();

					v.setVisibility(m_prefs.getBoolean("headlines_hide_sidebar", false) ? View.GONE : View.VISIBLE);
				}
			}
			return true;
		case R.id.subscribe_to_feed:
			Intent subscribe = new Intent(OnlineActivity.this, SubscribeActivity.class);
			startActivityForResult(subscribe, 0);
			return true;
		case R.id.toggle_attachments:
			if (true) {
				Article article = ap.getSelectedArticle();
				
				if (article != null && article.attachments != null && article.attachments.size() > 0) {
					CharSequence[] items = new CharSequence[article.attachments.size()];
					final CharSequence[] itemUrls = new CharSequence[article.attachments.size()];

					for (int i = 0; i < article.attachments.size(); i++) {
						items[i] = article.attachments.get(i).title != null ? article.attachments.get(i).content_url : 
							article.attachments.get(i).content_url;
						
						itemUrls[i] = article.attachments.get(i).content_url;
					}

					Dialog dialog = new Dialog(OnlineActivity.this);
					AlertDialog.Builder builder = new AlertDialog.Builder(OnlineActivity.this)
							.setTitle(R.string.attachments_prompt)
							.setCancelable(true)
							.setSingleChoiceItems(items, 0, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									//
								}
							}).setNeutralButton(R.string.attachment_copy, new OnClickListener() {								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									int selectedPosition = ((AlertDialog)dialog).getListView().getCheckedItemPosition();
									
									copyToClipboard((String)itemUrls[selectedPosition]);
								}
							}).setPositiveButton(R.string.attachment_view, new OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int id) {
									int selectedPosition = ((AlertDialog)dialog).getListView().getCheckedItemPosition();
									
									Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse((String)itemUrls[selectedPosition]));
									startActivity(browserIntent);

									dialog.cancel();
								}
							}).setNegativeButton(R.string.dialog_cancel, new OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
								}
							});

					dialog = builder.create();
					dialog.show();
				}
			}
			return true;
		case R.id.donate:
			if (true) {
				openUnlockUrl();
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
				
				int count = hf.getUnreadArticles().size();
				
				boolean confirm = m_prefs.getBoolean("confirm_headlines_catchup", true);
				
				if (count > 0) {
					if (confirm) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								OnlineActivity.this)
								.setMessage(getString(R.string.mark_num_headlines_as_read, count))
								.setPositiveButton(R.string.catchup,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {
	
												catchupVisibleArticles();											
												
											}
										})
								.setNegativeButton(R.string.dialog_cancel,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {
		
											}
										});
		
						AlertDialog dlg = builder.create();
						dlg.show();
					} else {
						catchupVisibleArticles();
					}
				}
			}
			return true;
		case R.id.headlines_view_mode:
			if (hf != null) {
				Dialog dialog = new Dialog(this);
				
				String viewMode = getViewMode();
				
				//Log.d(TAG, "viewMode:" + getViewMode());

				int selectedIndex = 0;
				
				if (viewMode.equals("all_articles")) {
					selectedIndex = 1;
				} else if (viewMode.equals("marked")) {
					selectedIndex = 2;
				} else if (viewMode.equals("published")) {
					selectedIndex = 3;
				} else if (viewMode.equals("unread")) {
					selectedIndex = 4;
				}
				
				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.headlines_set_view_mode)
						.setSingleChoiceItems(
								new String[] {
										getString(R.string.headlines_adaptive),
										getString(R.string.headlines_all_articles),
										getString(R.string.headlines_starred),
										getString(R.string.headlines_published),
										getString(R.string.headlines_unread) },
								selectedIndex, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										switch (which) {
										case 0:
											setViewMode("adaptive");
											break;
										case 1:
											setViewMode("all_articles");
											break;
										case 2:
											setViewMode("marked");
											break;
										case 3:
											setViewMode("published");
											break;
										case 4:
											setViewMode("unread");
											break;
										}
										dialog.cancel();

										refresh();
									}
								});

				dialog = builder.create();
				dialog.show();

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
			if (ap != null) {
				shareArticle(ap.getSelectedArticle());
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
		/* case R.id.selection_select_none:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();
				if (selected.size() > 0) {
					selected.clear();
					initMenu();
					hf.notifyUpdated();
				}
			}
			return true; */
		case R.id.selection_toggle_unread:
			if (hf != null) {
				ArticleList selected = hf.getSelectedArticles();

				if (selected.size() > 0) {
					for (Article a : selected)
						a.unread = !a.unread;

					toggleArticlesUnread(selected);
					hf.notifyUpdated();
					initMenu();
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
					initMenu();
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
					initMenu();
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
						initMenu();
					}
				}
			}
			return true;
		case R.id.set_unread:
			if (ap != null && ap.getSelectedArticle() != null) {
				Article a = ap.getSelectedArticle();
				
				if (a != null) {
					a.unread = !a.unread;
					saveArticleUnread(a);
				}
				
				if (hf != null) hf.notifyUpdated();
			}
			return true;
		case R.id.set_labels:
			if (ap != null && ap.getSelectedArticle() != null) {
				if (getApiLevel() != 7) {
					editArticleLabels(ap.getSelectedArticle());					
				} else {
					toast(R.string.server_function_not_available);
				}				
								
			}
			return true;
		case R.id.update_headlines:
			if (hf != null) {
				m_pullToRefreshAttacher.setRefreshing(true);
				hf.refresh(false);
			}
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	protected void catchupVisibleArticles() {
		final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		
		if (hf != null) {
			ArticleList articles = hf.getUnreadArticles();
			
			for (Article a : articles)
				a.unread = false;
	
			ApiRequest req = new ApiRequest(getApplicationContext()) {
				protected void onPostExecute(JsonElement result) {
					if (hf.isAdded()) {
						hf.refresh(false);
					}
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
	        	String note = topicEdit.getText().toString().trim();
	        	
	        	saveArticleNote(article, note);
	        	article.published = true;	
	        	article.note = note;
	        	
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
		
		ApiRequest.trustAllHosts(m_prefs.getBoolean("ssl_trust_any", false),
				m_prefs.getBoolean("ssl_trust_any_host", false));				
		
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
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		m_menu = menu;

		initMenu();
		
		if (!isAmazonDevice()) {
			List<PackageInfo> pkgs = getPackageManager()
					.getInstalledPackages(0);
	
			for (PackageInfo p : pkgs) {
				if ("org.fox.ttrss.key".equals(p.packageName)) {
					Log.d(TAG, "license apk found");
					menu.findItem(R.id.donate).setVisible(false);
					break;
				}
			}
		} else {
			menu.findItem(R.id.donate).setVisible(false);
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
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				//toast(R.string.article_set_unread);
				initMenu();
			}
		};

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

	public void saveArticleMarked(final Article article) {
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				//toast(article.marked ? R.string.notify_article_marked : R.string.notify_article_unmarked);
				initMenu();
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
				//toast(article.published ? R.string.notify_article_published : R.string.notify_article_unpublished);
				initMenu();
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {		
		if (m_prefs.getBoolean("use_volume_keys", false)) {
			ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
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
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);
			
			m_menu.findItem(R.id.set_labels).setEnabled(getApiLevel() >= 1);
			m_menu.findItem(R.id.article_set_note).setEnabled(getApiLevel() >= 1);
			m_menu.findItem(R.id.subscribe_to_feed).setEnabled(getApiLevel() >= 5);
			
			MenuItem search = m_menu.findItem(R.id.search);
			search.setEnabled(getApiLevel() >= 2);
			
			ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			if (ap != null) {
				Article article = ap.getSelectedArticle();
				
				if (article != null) {
					m_menu.findItem(R.id.toggle_marked).setIcon(article.marked ? R.drawable.ic_important_light :
						R.drawable.ic_unimportant_light);

					m_menu.findItem(R.id.toggle_published).setIcon(article.published ? R.drawable.ic_menu_published_light :
						R.drawable.ic_menu_unpublished_light);

					m_menu.findItem(R.id.set_unread).setIcon(article.unread ? R.drawable.ic_unread_light :
						R.drawable.ic_read_light);
				}				
			}
			
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
				
			if (hf != null) {
				if (hf.getSelectedArticles().size() > 0 && m_headlinesActionMode == null) {
					m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
				} else if (hf.getSelectedArticles().size() == 0 && m_headlinesActionMode != null) { 
					m_headlinesActionMode.finish();
				}
			}

			if (!isCompatMode()) {
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
						
						JsonElement apiLevel = content.get("api_level");

						GlobalState.getInstance().m_canUseProgress = m_canUseProgress;

						Log.d(TAG, "Authenticated! canUseProgress=" + m_canUseProgress);
						
						if (apiLevel != null) {
							setApiLevel(apiLevel.getAsInt());
							Log.d(TAG, "Received API level: " + getApiLevel());
							
							loginSuccess(m_refreshAfterLogin);
							
						} else {

							ApiRequest req = new ApiRequest(m_context) {
								protected void onPostExecute(JsonElement result) {
									setApiLevel(0);
	
									if (result != null) {
										try {
											setApiLevel(result.getAsJsonObject().get("level").getAsInt());
										} catch (Exception e) {
											e.printStackTrace();
										}
									} else if (m_lastError != ApiError.API_UNKNOWN_METHOD) {
										// Unknown method means old tt-rss, in that case we assume API 0 and continue
										
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
						}

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

	public void setViewMode(String viewMode) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("view_mode", viewMode);
		editor.commit();
	}
	
	public String getViewMode() {		
		return m_prefs.getString("view_mode", "adaptive");
	}
	
	public void setLastContentImageHitTestUrl(String url) {
		m_lastImageHitTestUrl = url;		
	}
	
	public String getLastContentImageHitTestUrl() {
		return m_lastImageHitTestUrl;
	}

}
