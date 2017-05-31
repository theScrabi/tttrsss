package org.fox.ttrss.widget;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.util.SimpleLoginManager;

import java.util.HashMap;

public class WidgetUpdateService extends Service {
    private final String TAG = this.getClass().getSimpleName();
    private SharedPreferences m_prefs;

    public static final int UPDATE_RESULT_OK = 0;
    public static final int UPDATE_RESULT_ERROR_LOGIN = 1;
    public static final int UPDATE_RESULT_ERROR_OTHER = 2;
    public static final int UPDATE_RESULT_ERROR_NEED_CONF = 3;
    public static final int UPDATE_IN_PROGRESS = 4;

    @Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind");

		return null;
	}

    protected boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart");

        try {
            sendResultIntent(-1, UPDATE_IN_PROGRESS);

            if (!isNetworkAvailable()) {
                final int retryCount = intent.getIntExtra("retryCount", 0);

                Log.d(TAG, "service update requested but network is not available, try: " + retryCount);

                if (retryCount < 10) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent serviceIntent = new Intent(getApplicationContext(), WidgetUpdateService.class);
                            serviceIntent.putExtra("retryCount", retryCount + 1);
                            startService(serviceIntent);

                        }
                    }, 3 * 1000);
                } else {
                    sendResultIntent(-1, UPDATE_RESULT_ERROR_OTHER);
                }

                stopSelf();
                return;
            }

            m_prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            if (m_prefs.getString("ttrss_url", "").trim().length() == 0) {

                sendResultIntent(-1, UPDATE_RESULT_ERROR_NEED_CONF);

            } else {

                final int feedId = m_prefs.getBoolean("widget_show_fresh", true) ? -3 : 0;

                final SimpleLoginManager loginManager = new SimpleLoginManager() {

                    @Override
                    protected void onLoginSuccess(int requestId, String sessionId, int apiLevel) {

                        ApiRequest aru = new ApiRequest(getApplicationContext()) {
                            @Override
                            protected void onPostExecute(JsonElement result) {
                                if (result != null) {
                                    try {
                                        JsonObject content = result.getAsJsonObject();

                                        if (content != null) {

                                            int unread = content.get("unread").getAsInt();
                                            sendResultIntent(unread, UPDATE_RESULT_OK);

                                            return;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Log.d(TAG, "request failed: " + getErrorMessage());
                                }

                                sendResultIntent(-1, UPDATE_RESULT_ERROR_OTHER);
                            }
                        };

                        final String fSessionId = sessionId;

                        HashMap<String, String> umap = new HashMap<String, String>() {
                            {
                                put("op", "getUnread");
                                put("feed_id", String.valueOf(feedId));
                                put("sid", fSessionId);
                            }
                        };

                        aru.execute(umap);
                    }

                    @Override
                    protected void onLoginFailed(int requestId, ApiRequest ar) {
                        Log.d(TAG, "login failed: " + getString(ar.getErrorMessage()));

                        sendResultIntent(-1, UPDATE_RESULT_ERROR_LOGIN);
                    }

                    @Override
                    protected void onLoggingIn(int requestId) {


                    }
                };

                String login = m_prefs.getString("login", "").trim();
                String password = m_prefs.getString("password", "").trim();

                loginManager.logIn(getApplicationContext(), 1, login, password);

            }
        } catch (Exception e) {
            e.printStackTrace();

            sendResultIntent(-1, UPDATE_RESULT_ERROR_OTHER);
        }

        stopSelf();

        super.onStart(intent, startId);
    }

	public void sendResultIntent(int unread, int resultCode) {
		Intent intent = new Intent();
        intent.setAction(SmallWidgetProvider.ACTION_UPDATE_RESULT);
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("unread", unread);

        sendBroadcast(intent);

        if (resultCode != UPDATE_IN_PROGRESS) stopSelf();
	}
}
