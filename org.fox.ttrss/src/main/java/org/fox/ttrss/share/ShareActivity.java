package org.fox.ttrss.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.JsonElement;

import org.fox.ttrss.ApiCommon;
import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.R;

import java.util.HashMap;

public class ShareActivity extends CommonShareActivity {
	private final String TAG = this.getClass().getSimpleName();
	
	private Button m_button;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		//setTheme(R.style.DarkTheme);

		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_LEFT_ICON);

		Intent intent = getIntent();

		String urlValue = intent.getStringExtra(Intent.EXTRA_TEXT);
		String titleValue = intent.getStringExtra(Intent.EXTRA_SUBJECT);
		String contentValue = "";
		
		if (savedInstanceState != null) {
			urlValue = savedInstanceState.getString("url");
			titleValue = savedInstanceState.getString("title");
			contentValue = savedInstanceState.getString("content");
		}

		setContentView(R.layout.activity_share);
		
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher);
		
		setSmallScreen(false); 
		
		EditText url = (EditText) findViewById(R.id.url);
		url.setText(urlValue);
		
		EditText title = (EditText) findViewById(R.id.title);
		title.setText(titleValue);

		EditText content = (EditText) findViewById(R.id.content);
		content.setText(contentValue);

		m_button = (Button) findViewById(R.id.share_button);
		
		m_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				login(0);
			} 
		});
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		EditText url = (EditText) findViewById(R.id.url);

		if (url != null) {
			out.putString("url", url.getText().toString());
		}
		
		EditText title = (EditText) findViewById(R.id.title);

		if (title != null) {
			out.putString("title", title.getText().toString());
		}

		EditText content = (EditText) findViewById(R.id.content);
		
		if (content != null) {
			out.putString("content", content.getText().toString());
		}

	}
	
	private void postData() {
		m_button.setEnabled(false);
		
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				setProgressBarIndeterminateVisibility(false);

				if (m_lastError != ApiCommon.ApiError.NO_ERROR) {
					toast(getErrorMessage());
				} else {
					toast(R.string.share_article_posted);
					finish();
				}
				
				m_button.setEnabled(true);
			}
		};
		
		final EditText url = (EditText) findViewById(R.id.url);
		final EditText title = (EditText) findViewById(R.id.title);
		final EditText content = (EditText) findViewById(R.id.content);			

		if (url != null && title != null && content != null) {
			HashMap<String, String> map = new HashMap<String, String>() {
				{
					put("sid", m_sessionId);
					put("op", "shareToPublished");
					put("title", title.getText().toString());
					put("url", url.getText().toString());
					put("content", content.getText().toString());
				}
			};

			setProgressBarIndeterminateVisibility(true);

			req.execute(map);
		}
	}
	
	
	@Override
	public void onLoggingIn(int requestId) {
		m_button.setEnabled(false);
	}

	@Override
	protected void onLoggedIn(int requestId) {
		m_button.setEnabled(true);
		
		if (m_apiLevel < 4) {
			toast(R.string.api_too_low);									
		} else {
			postData();									
		}
	}
}
