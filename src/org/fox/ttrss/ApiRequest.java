package org.fox.ttrss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ApiRequest extends AsyncTask<HashMap<String,String>, Integer, JsonElement> {
	private final String TAG = this.getClass().getSimpleName();

	public enum ApiError { NO_ERROR, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, 
		HTTP_SERVER_ERROR, HTTP_OTHER_ERROR, SSL_REJECTED, PARSE_ERROR, IO_ERROR, OTHER_ERROR, API_DISABLED, API_UNKNOWN, LOGIN_FAILED, INVALID_URL };
	
	public static final int API_STATUS_OK = 0;
	public static final int API_STATUS_ERR = 1;
		
	private String m_api;
	private boolean m_trustAny = false;
	private boolean m_transportDebugging = false;
	protected int m_httpStatusCode = 0;
	protected int m_apiStatusCode = 0;
	protected Context m_context;
	private SharedPreferences m_prefs;
	
	protected ApiError m_lastError;

	public ApiRequest(Context context) {
		m_context = context;

		m_prefs = PreferenceManager.getDefaultSharedPreferences(m_context);
		
		m_api = m_prefs.getString("ttrss_url", null).trim();
		m_trustAny = m_prefs.getBoolean("ssl_trust_any", false);
		m_transportDebugging = m_prefs.getBoolean("transport_debugging", false);
		m_lastError = ApiError.NO_ERROR;
		
	}
	
	protected int getErrorMessage() {
		switch (m_lastError) {
		case NO_ERROR:
			return R.string.error_unknown;
		case HTTP_UNAUTHORIZED:
			return R.string.error_http_unauthorized;
		case HTTP_FORBIDDEN:
			return R.string.error_http_forbidden;
		case HTTP_NOT_FOUND:
			return R.string.error_http_not_found;
		case HTTP_SERVER_ERROR:
			return R.string.error_http_server_error;
		case HTTP_OTHER_ERROR:
			return R.string.error_http_other_error;
		case SSL_REJECTED:
			return R.string.error_ssl_rejected;
		case PARSE_ERROR:
			return R.string.error_parse_error;
		case IO_ERROR:
			return R.string.error_io_error;
		case OTHER_ERROR:
			return R.string.error_other_error;
		case API_DISABLED:
			return R.string.error_api_disabled;
		case API_UNKNOWN:
			return R.string.error_api_unknown;
		case LOGIN_FAILED:
			return R.string.error_login_failed;
		case INVALID_URL:
			return R.string.error_invalid_api_url;
		default:
			Log.d(TAG, "getErrorMessage: unknown error code=" + m_lastError);
			return R.string.error_unknown;
		}
	}
	
	@Override
	protected JsonElement doInBackground(HashMap<String, String>... params) {

		Gson gson = new Gson();
		
		String requestStr = gson.toJson(new HashMap<String,String>(params[0]));
		
		if (m_transportDebugging) Log.d(TAG, ">>> (" + requestStr + ") " + m_api);
		
		DefaultHttpClient client;
		
		if (m_trustAny) {
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
        
			HttpParams httpParams = new BasicHttpParams();

			client = new DefaultHttpClient(new ThreadSafeClientConnManager(httpParams, schemeRegistry), httpParams);
		} else {
			client = new DefaultHttpClient();
		}

		try {

			HttpPost httpPost;
			
			try {
				httpPost = new HttpPost(m_api + "/api/");
			} catch (IllegalArgumentException e) {
				m_lastError = ApiError.INVALID_URL;
				e.printStackTrace();
				return null;
			} catch (Exception e) {
				m_lastError = ApiError.OTHER_ERROR;
				e.printStackTrace();
				return null;
			}
	
			String httpLogin = m_prefs.getString("http_login", "").trim();
			String httpPassword = m_prefs.getString("http_password", "").trim();
			
			if (httpLogin.length() > 0) {
				if (m_transportDebugging) Log.d(TAG, "Using HTTP Basic authentication.");
	
				URL targetUrl;
				try {
					targetUrl = new URL(m_api);
				} catch (MalformedURLException e) {
					m_lastError = ApiError.INVALID_URL;
					e.printStackTrace();
					return null;
				}
				
				HttpHost targetHost = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getProtocol());
				
				client.getCredentialsProvider().setCredentials(
		                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
		                new UsernamePasswordCredentials(httpLogin, httpPassword));
			}

			httpPost.setEntity(new StringEntity(requestStr, "utf-8"));
			HttpResponse execute = client.execute(httpPost);
			
			m_httpStatusCode = execute.getStatusLine().getStatusCode();

			switch (m_httpStatusCode) {
			case 200:
				InputStream content = execute.getEntity().getContent();
	
				BufferedReader buffer = new BufferedReader(
						new InputStreamReader(content), 8192);
	
				String s = "";				
				String response = "";
	
				while ((s = buffer.readLine()) != null) {
					response += s;
				}
	
				if (m_transportDebugging) Log.d(TAG, "<<< " + response);
	
				JsonParser parser = new JsonParser();
				
				JsonElement result = parser.parse(response);
				JsonObject resultObj = result.getAsJsonObject();
				
				m_apiStatusCode = resultObj.get("status").getAsInt();
				
				switch (m_apiStatusCode) {
				case API_STATUS_OK:
					return result.getAsJsonObject().get("content");
				case API_STATUS_ERR:
					JsonObject contentObj = resultObj.get("content").getAsJsonObject();
					String error = contentObj.get("error").getAsString();
					
					if (error.equals("LOGIN_ERROR")) {
						m_lastError = ApiError.LOGIN_FAILED;
					} else if (error.equals("API_DISABLED")) {
						m_lastError = ApiError.LOGIN_FAILED;
					} else if (error.equals("NOT_LOGGED_IN")) {
						m_lastError = ApiError.LOGIN_FAILED;
					} else {
						Log.d(TAG, "Unknown API error: " + error);
						m_lastError = ApiError.API_UNKNOWN;
					}		
				}
				
				return null;
			case 401:
				m_lastError = ApiError.HTTP_UNAUTHORIZED;
				break;
			case 403:
				m_lastError = ApiError.HTTP_FORBIDDEN;
				break;
			case 404:
				m_lastError = ApiError.HTTP_NOT_FOUND;
				break;
			case 500:
				m_lastError = ApiError.HTTP_SERVER_ERROR;
				break;
			default:
				m_lastError = ApiError.HTTP_OTHER_ERROR;
				break;
			}
			
			return null;
		} catch (javax.net.ssl.SSLPeerUnverifiedException e) {
			m_lastError = ApiError.SSL_REJECTED;
			e.printStackTrace();
		} catch (IOException e) {
			m_lastError = ApiError.IO_ERROR;
			e.printStackTrace();
		} catch (com.google.gson.JsonSyntaxException e) {
			m_lastError = ApiError.PARSE_ERROR;
			e.printStackTrace();
		} catch (Exception e) {
			m_lastError = ApiError.OTHER_ERROR;
			e.printStackTrace();
		}
		
		return null;
	}
}
