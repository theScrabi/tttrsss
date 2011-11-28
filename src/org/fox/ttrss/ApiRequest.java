package org.fox.ttrss;

import java.io.BufferedReader;
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
import com.google.gson.JsonParser;

public class ApiRequest extends AsyncTask<HashMap<String,String>, Integer, JsonElement> {
	private final String TAG = this.getClass().getSimpleName();

	protected static final int STATUS_LOGIN_FAILED = 0;
	protected static final int STATUS_OK = 1;
	protected static final int STATUS_API_DISABLED = 2;
	protected static final int STATUS_OTHER_ERROR = 3;
	
	private String m_api;
	private boolean m_trustAny = false;
	private boolean m_transportDebugging = false;
	private Context m_context;
	private SharedPreferences m_prefs;

	public ApiRequest(Context context) {
		m_context = context;

		m_prefs = PreferenceManager.getDefaultSharedPreferences(m_context);
		
		m_api = m_prefs.getString("ttrss_url", null);
		m_trustAny = m_prefs.getBoolean("ssl_trust_any", false);
		m_transportDebugging = m_prefs.getBoolean("transport_debugging", false);
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

		HttpPost httpPost = new HttpPost(m_api + "/api/");

		String httpLogin = m_prefs.getString("http_login", "");
		String httpPassword = m_prefs.getString("http_password", "");
		
		if (httpLogin.length() > 0) {
			if (m_transportDebugging) Log.d(TAG, "Using HTTP Basic authentication.");

			URL targetUrl;
			try {
				targetUrl = new URL(m_api);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return null;
			}
			
			HttpHost targetHost = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getProtocol());
			
			client.getCredentialsProvider().setCredentials(
	                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
	                new UsernamePasswordCredentials(httpLogin, httpPassword));
		}
		

		try {
			httpPost.setEntity(new StringEntity(requestStr, "utf-8"));
			HttpResponse execute = client.execute(httpPost);
			
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
			
			return parser.parse(response);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
