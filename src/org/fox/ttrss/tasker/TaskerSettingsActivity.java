package org.fox.ttrss.tasker;

import org.fox.ttrss.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class TaskerSettingsActivity extends Activity {
	private final String TAG = this.getClass().getSimpleName();
	
	protected Bundle m_settings = new Bundle();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		//Bundle settings = getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
		
		setContentView(R.layout.tasker_settings);
		
		Button button = (Button)findViewById(R.id.close_button);
		
		button.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				finish();				
			}
		});
	}
	
	@Override
    public void finish() {
		final Intent intent = new Intent();

		intent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, m_settings);
		intent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, getString(R.string.download_articles_and_go_offline));
		
		setResult(RESULT_OK, intent);
		
		super.finish();
	
	}
}
