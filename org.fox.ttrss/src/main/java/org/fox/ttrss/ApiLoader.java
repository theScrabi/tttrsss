package org.fox.ttrss;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.fox.ttrss.ApiCommon.ApiError;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class ApiLoader extends AsyncTaskLoader<JsonElement> {
	private final String TAG = this.getClass().getSimpleName();


    public static final int API_STATUS_OK = 0;
	public static final int API_STATUS_ERR = 1;

	private String m_api;
	private boolean m_transportDebugging = false;
	protected int m_responseCode = 0;
	protected String m_responseMessage;
	protected int m_apiStatusCode = 0;
	protected Context m_context;
	private SharedPreferences m_prefs;
	protected String m_lastErrorMessage;
	protected ApiError m_lastError;
	protected HashMap<String,String> m_params;
	protected JsonElement m_data;

	public ApiLoader(Context context, HashMap<String,String> params) {
		super(context);

		m_context = context;

		m_prefs = PreferenceManager.getDefaultSharedPreferences(m_context);

		m_api = m_prefs.getString("ttrss_url", "").trim();
		m_transportDebugging = m_prefs.getBoolean("transport_debugging", false);
		m_lastError = ApiError.NO_ERROR;
		m_params = params;

	}

	@Override
	protected void onStartLoading() {
		if (m_data != null) {
			deliverResult(m_data);
		} else {
			forceLoad();
		}
	}

	@Override
	public void deliverResult(JsonElement data) {
		m_data = data;

		super.deliverResult(data);
	}

	public int getErrorMessage() {
		return ApiCommon.getErrorMessage(m_lastError);
	}

	public ApiError getLastError() {
		return m_lastError;
	}

	public String getLastErrorMessage() {
		return m_lastErrorMessage;
	}

	@Override
	public JsonElement loadInBackground() {

		if (!isNetworkAvailable()) {
			m_lastError = ApiError.NETWORK_UNAVAILABLE;
			return null;
		}

		Gson gson = new Gson();

		String requestStr = gson.toJson(new HashMap<>(m_params));
		byte[] postData = null;

		try {
			postData = requestStr.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			m_lastError = ApiError.OTHER_ERROR;
			e.printStackTrace();
			return null;
		}

		if (m_transportDebugging) Log.d(TAG, ">>> (" + requestStr + ") " + m_api);

		URL url;

		try {
			// canonicalize url just in case
			URL baseUrl = new URL(m_api);
			File f = new File(baseUrl.getPath() + "/api");
			url = new URL(baseUrl, f.getCanonicalPath() + "/");
		} catch (Exception e) {
			m_lastError = ApiError.INVALID_URL;
			e.printStackTrace();
			return null;
		}

		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			String httpLogin = m_prefs.getString("http_login", "").trim();
			String httpPassword = m_prefs.getString("http_password", "").trim();

			if (httpLogin.length() > 0) {
				if (m_transportDebugging) Log.d(TAG, "Using HTTP Basic authentication.");

				conn.setRequestProperty("Authorization", "Basic " +
						Base64.encodeToString((httpLogin + ":" + httpPassword).getBytes("UTF-8"), Base64.NO_WRAP));
			}

			conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
		    conn.setRequestProperty("Content-Length", Integer.toString(postData.length));

		    OutputStream out = conn.getOutputStream();
		    out.write(postData);
		    out.close();

		    m_responseCode = conn.getResponseCode();
		    m_responseMessage = conn.getResponseMessage();

		    switch (m_responseCode) {
			case HttpURLConnection.HTTP_OK:
				StringBuffer response = new StringBuffer();
				InputStreamReader in = new InputStreamReader(conn.getInputStream(), "UTF-8");
				char[] buf = new char[256];
				int read = 0;

				while ((read = in.read(buf)) >= 0) {
					response.append(buf, 0, read);
				}

				if (m_transportDebugging) Log.d(TAG, "<<< " + response);

				JsonParser parser = new JsonParser();

				JsonElement result = parser.parse(response.toString());
				JsonObject resultObj = result.getAsJsonObject();

				m_apiStatusCode = resultObj.get("status").getAsInt();

				conn.disconnect();

				switch (m_apiStatusCode) {
				case API_STATUS_OK:
					return result.getAsJsonObject().get("content");
				case API_STATUS_ERR:
					JsonObject contentObj = resultObj.get("content").getAsJsonObject();
					String error = contentObj.get("error").getAsString();

					if (error.equals("LOGIN_ERROR")) {
						m_lastError = ApiError.LOGIN_FAILED;
					} else if (error.equals("API_DISABLED")) {
						m_lastError = ApiError.API_DISABLED;
					} else if (error.equals("NOT_LOGGED_IN")) {
						m_lastError = ApiError.LOGIN_FAILED;
					} else if (error.equals("INCORRECT_USAGE")) {
						m_lastError = ApiError.API_INCORRECT_USAGE;
					} else if (error.equals("UNKNOWN_METHOD")) {
						m_lastError = ApiError.API_UNKNOWN_METHOD;
					} else {
						Log.d(TAG, "Unknown API error: " + error);
						m_lastError = ApiError.API_UNKNOWN;
					}
				}

				return null;
			case HttpURLConnection.HTTP_UNAUTHORIZED:
				m_lastError = ApiError.HTTP_UNAUTHORIZED;
				break;
			case HttpURLConnection.HTTP_FORBIDDEN:
				m_lastError = ApiError.HTTP_FORBIDDEN;
				break;
			case HttpURLConnection.HTTP_NOT_FOUND:
				m_lastError = ApiError.HTTP_NOT_FOUND;
				break;
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				m_lastError = ApiError.HTTP_SERVER_ERROR;
				break;
			default:
				Log.d(TAG, "HTTP response code: " + m_responseCode + "(" + m_responseMessage + ")");
				m_lastError = ApiError.HTTP_OTHER_ERROR;
				break;
			}

		    conn.disconnect();
		    return null;
		} catch (javax.net.ssl.SSLPeerUnverifiedException e) {
			m_lastError = ApiError.SSL_REJECTED;
			m_lastErrorMessage = e.getMessage();
			e.printStackTrace();
		} catch (IOException e) {
			m_lastError = ApiError.IO_ERROR;
			m_lastErrorMessage = e.getMessage();

			if (e.getMessage() != null) {
				if (e.getMessage().matches("Hostname [^ ]+ was not verified")) {
					m_lastError = ApiError.SSL_HOSTNAME_REJECTED;
				}
			}

			e.printStackTrace();
		} catch (com.google.gson.JsonSyntaxException e) {
			m_lastError = ApiError.PARSE_ERROR;
			m_lastErrorMessage = e.getMessage();
			e.printStackTrace();
		} catch (Exception e) {
			m_lastError = ApiError.OTHER_ERROR;
			m_lastErrorMessage = e.getMessage();
			e.printStackTrace();
		}
		
		return null;
	}

	protected boolean isNetworkAvailable() {
	    ConnectivityManager cm = (ConnectivityManager) 
	      m_context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
	    
	    // if no network is available networkInfo will be null
	    // otherwise check if we are connected
	    if (networkInfo != null && networkInfo.isConnected()) {
	        return true;
	    }
	    return false;
	} 
}
