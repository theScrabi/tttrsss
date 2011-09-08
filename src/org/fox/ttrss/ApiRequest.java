package org.fox.ttrss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class ApiRequest extends AsyncTask<HashMap<String,String>, Integer, JsonElement> {
	private final String TAG = this.getClass().getSimpleName();

	protected String m_sessionId;
	protected String m_apiEndpoint;
	
	protected ApiRequest(String sessionId, String apiEndpoint) {
		super();
		m_sessionId = sessionId;
		m_apiEndpoint = apiEndpoint;
	}
	
	@Override
	protected JsonElement doInBackground(HashMap<String,String>... params) {

		Gson gson = new Gson();
		
		String requestStr = gson.toJson(params);
		
		// FIXME ugly hack
		requestStr = requestStr.substring(1).substring(0, requestStr.length()-2);
		
		Log.d(TAG, "executing API request...: " + requestStr + " " + m_apiEndpoint);
		
		DefaultHttpClient client = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(m_apiEndpoint + "/api/");
		
		try {
			httpPost.setEntity(new StringEntity(requestStr, "utf-8"));
			HttpResponse execute = client.execute(httpPost);
			
			InputStream content = execute.getEntity().getContent();

			BufferedReader buffer = new BufferedReader(
					new InputStreamReader(content));

			String s = "";				
			String response = "";

			while ((s = buffer.readLine()) != null) {
				response += s;
			}

			Log.d(TAG, "Server returned: " + response);

			JsonParser parser = new JsonParser();
			
			return parser.parse(response);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
