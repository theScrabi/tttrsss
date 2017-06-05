package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import org.fox.ttrss.util.DatabaseHelper;
import org.fox.ttrss.widget.SmallWidgetProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class CommonActivity extends ActionBarActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";

	public final static String THEME_DARK = "THEME_DARK";
	public final static String THEME_LIGHT = "THEME_LIGHT";
	//public final static String THEME_SEPIA = "THEME_SEPIA";
    //public final static String THEME_AMBER = "THEME_AMBER";
	public final static String THEME_DEFAULT = CommonActivity.THEME_LIGHT;

    public static final int EXCERPT_MAX_LENGTH = 256;
    public static final int EXCERPT_MAX_QUERY_LENGTH = 2048;

	public static final int PENDING_INTENT_CHROME_SHARE = 1;

	private DatabaseHelper m_databaseHelper;

	//private SQLiteDatabase m_readableDb;
	//private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode = true;
	private String m_theme;
	private boolean m_needRestart;

	protected CustomTabsClient m_customTabClient;
	protected CustomTabsServiceConnection m_customTabServiceConnection = new CustomTabsServiceConnection() {
		@Override
		public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
			m_customTabClient = customTabsClient;

			m_customTabClient.warmup(0);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			m_customTabClient = null;
		}
	};

	protected SharedPreferences m_prefs;

	protected void setSmallScreen(boolean smallScreen) {
		Log.d(TAG, "m_smallScreenMode=" + smallScreen);
		m_smallScreenMode = smallScreen;
	}

	public DatabaseHelper getDatabaseHelper() {
		return m_databaseHelper;
	}

	public SQLiteDatabase getDatabase() {
		return m_databaseHelper.getWritableDatabase();
	}

	public boolean getUnreadOnly() {
		return m_prefs.getBoolean("show_unread_only", true);
	}

    // not the same as isSmallScreen() which is mostly about layout being loaded
    public boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

	public void setUnreadOnly(boolean unread) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("show_unread_only", unread);
		editor.apply();
	}

	public void toast(int msgId) {
		toast(getString(msgId));
	}

	public void toast(String msg) {
		Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
				.setAction(R.string.dialog_close, new View.OnClickListener() {
					@Override
					public void onClick(View v) {

					}
				})
				.show();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (m_needRestart) {
			Log.d(TAG, "restart requested");
			
			finish();
			startActivity(getIntent());
		}
	}

	@Override
	public void onDestroy() {

		if (m_customTabServiceConnection != null) {
			unbindService(m_customTabServiceConnection);
		}

		super.onDestroy();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_databaseHelper = DatabaseHelper.getInstance(this);

		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		m_prefs.registerOnSharedPreferenceChangeListener(this);

		setupWidgetUpdates(this);

        if (savedInstanceState != null) {
			m_theme = savedInstanceState.getString("theme");
		} else {
			m_theme = m_prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		}

		CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", m_customTabServiceConnection);

		/*if (!ImageLoader.getInstance().isInited()) {
			ImageLoaderConfiguration config;

			try {
				config = new ImageLoaderConfiguration.Builder(getApplicationContext())
						.diskCache(
								new LruDiscCache(new File(getCacheDir(), "article-images"),
										DefaultConfigurationFactory.createFileNameGenerator(),
										100 * 1024 * 1024))
						.build();
			} catch (IOException e) {
				config = new ImageLoaderConfiguration.Builder(getApplicationContext())
						.build();
			}
			ImageLoader.getInstance().init(config);
		}*/

		super.onCreate(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("theme", m_theme);
	}
	
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}

	@SuppressWarnings("deprecation")
	public boolean isPortrait() {
		Display display = getWindowManager().getDefaultDisplay(); 
		
	    int width = display.getWidth();
	    int height = display.getHeight();
		
	    return width < height;
	}

	@SuppressLint({ "NewApi", "ServiceCast" })
	@SuppressWarnings("deprecation")
	public void copyToClipboard(String str) {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		} else {
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		}

		Snackbar.make(findViewById(android.R.id.content), R.string.text_copied_to_clipboard, Snackbar.LENGTH_SHORT)
				.setAction(R.string.dialog_close, new View.OnClickListener() {
					@Override
					public void onClick(View v) {

					}
				})
				.show();
	}

	protected void setAppTheme(SharedPreferences prefs) {
		String theme = prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		
		if (theme.equals(THEME_DARK)) {
            setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Log.d(TAG, "onSharedPreferenceChanged:" + key);

		String[] filter = new String[] { "theme", "enable_cats", "headline_mode", "widget_update_interval",
				"headlines_swipe_to_dismiss", "headlines_mark_read_scroll" };

		m_needRestart = Arrays.asList(filter).indexOf(key) != -1;
	}

	private CustomTabsSession getCustomTabSession() {
		return m_customTabClient.newSession(new CustomTabsCallback() {
			@Override
			public void onNavigationEvent(int navigationEvent, Bundle extras) {
				super.onNavigationEvent(navigationEvent, extras);
			}
		});
	}

	protected void preloadUriIfAllowed(Uri uri) {
		boolean enableCustomTabs = m_prefs.getBoolean("enable_custom_tabs", true);

		if (m_customTabClient != null && enableCustomTabs) {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo info = cm.getActiveNetworkInfo();

			if (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI) {
				CustomTabsSession session = getCustomTabSession();
				session.mayLaunchUrl(uri, null, null);

				//toast("Preloading: " + uri.toString());
			}
		}
	}

	protected Intent getShareIntent(String text, String subject) {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_TEXT, text);

		if (subject != null) {
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		}

		return shareIntent;
	}

	protected void shareText(String text) {
		startActivity(Intent.createChooser(getShareIntent(text, null), text));
	}

	protected void shareText(String text, String subject) {
		startActivity(Intent.createChooser(getShareIntent(text, subject), text));
	}

	private void openUriWithCustomTab(Uri uri) {
		if (m_customTabClient != null) {
			TypedValue tvBackground = new TypedValue();
			getTheme().resolveAttribute(R.attr.colorPrimary, tvBackground, true);

			CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getCustomTabSession());

			builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
			builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);

			builder.setToolbarColor(tvBackground.data);

			Intent shareIntent = getShareIntent(uri.toString(), null);

			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
					CommonActivity.PENDING_INTENT_CHROME_SHARE, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.setActionButton(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share),
					getString(R.string.share_article), pendingIntent);

			CustomTabsIntent intent = builder.build();

			try {
				intent.launchUrl(this, uri);
			} catch (Exception e) {
				e.printStackTrace();
				toast(e.getMessage());
			}
		}
	}

	// uses chrome custom tabs when available
	public void openUri(Uri uri) {
		boolean enableCustomTabs = m_prefs.getBoolean("enable_custom_tabs", true);
		final boolean askEveryTime = m_prefs.getBoolean("custom_tabs_ask_always", true);

		if (uri.getScheme() == null) {
			try {
				uri = Uri.parse("https:" + uri.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		final Uri finalUri = uri;

		if (enableCustomTabs && m_customTabClient != null) {

			if (askEveryTime) {

				View dialogView = View.inflate(this, R.layout.dialog_open_link_askcb, null);
				final CheckBox askEveryTimeCB = (CheckBox) dialogView.findViewById(R.id.open_link_ask_checkbox);

				AlertDialog.Builder builder = new AlertDialog.Builder(
						CommonActivity.this)
						.setView(dialogView)
						.setMessage(uri.toString())
						.setPositiveButton(R.string.quick_preview,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
														int which) {

										if (!askEveryTimeCB.isChecked()) {
											SharedPreferences.Editor editor = m_prefs.edit();
											editor.putBoolean("custom_tabs_ask_always", false);
											editor.apply();
										}

										openUriWithCustomTab(finalUri);

									}
								})
						.setNegativeButton(R.string.open_with,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
														int which) {

										if (!askEveryTimeCB.isChecked()) {
											SharedPreferences.Editor editor = m_prefs.edit();
											editor.putBoolean("custom_tabs_ask_always", false);
											editor.putBoolean("enable_custom_tabs", false);
											editor.apply();
										}

										Intent intent = new Intent(Intent.ACTION_VIEW, finalUri);

										try {
											startActivity(intent);
										} catch (Exception e) {
											e.printStackTrace();
											toast(e.getMessage());
										}

									}
								});
						/*.setNegativeButton(R.string.cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
													int which) {

									if (!askEveryTimeCB.isChecked()) {
										SharedPreferences.Editor editor = m_prefs.edit();
										editor.putBoolean("custom_tabs_ask_always", false);
										editor.apply();
									}

								}
							});*/

				AlertDialog dlg = builder.create();
				dlg.show();

			} else {
				openUriWithCustomTab(uri);
			}

		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);

			try {
				startActivity(intent);
			} catch (Exception e) {
				toast(e.getMessage());
			}
		}
	}

	public static void setupWidgetUpdates(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		int updateInterval = Integer.parseInt(prefs.getString("widget_update_interval", "15")) * 60 * 1000;

		Log.d("setupWidgetUpdates", "interval= " + updateInterval);

		AlarmManager alarmManager = (AlarmManager)context.getSystemService(ALARM_SERVICE);

		Intent intentUpdate = new Intent(SmallWidgetProvider.ACTION_REQUEST_UPDATE);

		PendingIntent pendingIntentAlarm = PendingIntent.getBroadcast(context,
				0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT);

		alarmManager.cancel(pendingIntentAlarm);

		alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
				SystemClock.elapsedRealtime() + updateInterval,
				updateInterval,
				pendingIntentAlarm);

	}

	public void displayImageCaption(String url, String htmlContent) {
		// Android doesn't give us an easy way to access title tags;
		// we'll use Jsoup on the body text to grab the title text
		// from the first image tag with this url. This will show
		// the wrong text if an image is used multiple times.
		Document doc = Jsoup.parse(htmlContent);
		Elements es = doc.getElementsByAttributeValue("src", url);
		if (es.size() > 0) {
			if (es.get(0).hasAttr("title")) {

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
					.setCancelable(true)
					.setMessage(es.get(0).attr("title"))
					.setPositiveButton(R.string.dialog_close, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
								}
							}
					);

				AlertDialog dialog = builder.create();
				dialog.show();

			} else {
				toast(R.string.no_caption_to_display);
			}
		} else {
			toast(R.string.no_caption_to_display);
		}
	}

}

