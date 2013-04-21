package org.fox.ttrss;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ApiRequest extends AsyncTask<HashMap<String,String>, Integer, JsonElement> {
	private final String TAG = this.getClass().getSimpleName();

	public enum ApiError { NO_ERROR, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, 
		HTTP_SERVER_ERROR, HTTP_OTHER_ERROR, SSL_REJECTED, SSL_HOSTNAME_REJECTED, PARSE_ERROR, IO_ERROR, OTHER_ERROR, API_DISABLED, 
		API_UNKNOWN, LOGIN_FAILED, INVALID_URL, API_INCORRECT_USAGE, NETWORK_UNAVAILABLE, API_UNKNOWN_METHOD };
	
	public static final int API_STATUS_OK = 0;
	public static final int API_STATUS_ERR = 1;
		
	private String m_api;
	private boolean m_transportDebugging = false;
	protected int m_responseCode = 0;
	protected String m_responseMessage;
	protected int m_apiStatusCode = 0;
	protected boolean m_canUseProgress = false;
	protected Context m_context;
	private SharedPreferences m_prefs;
	
	protected ApiError m_lastError;

	public ApiRequest(Context context) {
		m_context = context;

		m_prefs = PreferenceManager.getDefaultSharedPreferences(m_context);
		
		m_api = m_prefs.getString("ttrss_url", null).trim();
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
		case SSL_HOSTNAME_REJECTED:
			return R.string.error_ssl_hostname_rejected;
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
		case API_UNKNOWN_METHOD:
			return R.string.error_api_unknown_method;
		case LOGIN_FAILED:
			return R.string.error_login_failed;
		case INVALID_URL:
			return R.string.error_invalid_api_url;
		case API_INCORRECT_USAGE:
			return R.string.error_api_incorrect_usage;
		case NETWORK_UNAVAILABLE:
			return R.string.error_network_unavailable;
		default:
			Log.d(TAG, "getErrorMessage: unknown error code=" + m_lastError);
			return R.string.error_unknown;
		}
	}
	
	@Override
	protected JsonElement doInBackground(HashMap<String, String>... params) {

		if (!isNetworkAvailable()) {
			m_lastError = ApiError.NETWORK_UNAVAILABLE;
			return null;
		}
		
		Gson gson = new Gson();
		
		String requestStr = gson.toJson(new HashMap<String,String>(params[0]));
		byte[] postData = null;
		
		try {
			postData = requestStr.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			m_lastError = ApiError.OTHER_ERROR;
			e.printStackTrace();
			return null;
		}
		
		/* disableConnectionReuseIfNecessary(); */
		
		if (m_transportDebugging) Log.d(TAG, ">>> (" + requestStr + ") " + m_api);
		
		/* ApiRequest.trustAllHosts(m_prefs.getBoolean("ssl_trust_any", false),
				m_prefs.getBoolean("ssl_trust_any_host", false)); */				
		
		URL url;
		
		try {
			url = new URL(m_api + "/api/");			
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
				int total = 0;

				int contentLength = conn.getHeaderFieldInt("Api-Content-Length", -1);

				m_canUseProgress = (contentLength != -1);
				
				while ((read = in.read(buf)) >= 0) {
					response.append(buf, 0, read);
					total += read;
					publishProgress(Integer.valueOf(total), Integer.valueOf(contentLength));
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
			e.printStackTrace();
		} catch (IOException e) {
			m_lastError = ApiError.IO_ERROR;

			if (e.getMessage() != null) {
				if (e.getMessage().matches("Hostname [^ ]+ was not verified")) {
					m_lastError = ApiError.SSL_HOSTNAME_REJECTED;
				}
			}
			
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
	
	protected static void trustAllHosts(boolean trustAnyCert, boolean trustAnyHost) {
	    try {
	    	if (trustAnyCert) {
	    	    X509TrustManager easyTrustManager = new X509TrustManager() {

	    	        public void checkClientTrusted(
	    	        		X509Certificate[] chain,
	    	                String authType) throws CertificateException {
	    	            // Oh, I am easy!
	    	        }

	    	        public void checkServerTrusted(
	    	        		X509Certificate[] chain,
	    	                String authType) throws CertificateException {
	    	            // Oh, I am easy!
	    	        }

	    	        public X509Certificate[] getAcceptedIssuers() {
	    	            return null;
	    	        }

	    	    };

	    	    // Create a trust manager that does not validate certificate chains
	    	    TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};

	    	    // Install the all-trusting trust manager

	    		SSLContext sc = SSLContext.getInstance("TLS");

	    		sc.init(null, trustAllCerts, new java.security.SecureRandom());

	    		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	    	}
	    	
	    	if (trustAnyHost) {
	    		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {				
	    			@Override
	    			public boolean verify(String hostname, SSLSession session) {
	    				return true;
	    			}
	    		});
	    	}

	    } catch (Exception e) {
	            e.printStackTrace();
	    }
	}
	
	@SuppressWarnings("deprecation")
	protected static void disableConnectionReuseIfNecessary() {
	    // HTTP connection reuse which was buggy pre-froyo
	    if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) {
	        System.setProperty("http.keepAlive", "false");
	    }
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
