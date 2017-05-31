package org.fox.ttrss.tasker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import org.fox.ttrss.R;

public class TaskerSettingsActivity extends Activity {
	protected static final int ACTION_DOWNLOAD = 0;
	protected static final int ACTION_UPLOAD = 1;

	private final String TAG = this.getClass().getSimpleName();
	
	protected Bundle m_settings = new Bundle();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		Bundle settings = getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
		
		int actionId = settings != null ? settings.getInt("actionId", -1) : -1;
		
		setContentView(R.layout.activity_tasker_settings);

		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.taskerActions);
		
		switch (actionId) {
		case TaskerSettingsActivity.ACTION_DOWNLOAD:
			radioGroup.check(R.id.actionDownload);
			break;
		case TaskerSettingsActivity.ACTION_UPLOAD:
			radioGroup.check(R.id.actionUpload);
			break;
		default:
			Log.d(TAG, "unknown action id=" + actionId);
		}
		
		radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {			
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				switch (checkedId) {
				case R.id.actionDownload:
					m_settings.putInt("actionId", ACTION_DOWNLOAD);					
					break;
				case R.id.actionUpload:
					m_settings.putInt("actionId", ACTION_UPLOAD);
					break;
				}				
			}
		});
		
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
		
		String blurb = "?";
		
		switch (m_settings.getInt("actionId")) {
		case TaskerSettingsActivity.ACTION_DOWNLOAD:
			blurb = getString(R.string.download_articles_and_go_offline);
			break;
		case TaskerSettingsActivity.ACTION_UPLOAD:
			blurb = getString(R.string.synchronize_read_articles_and_go_online);
			break;
		}
		
		intent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, blurb);
		
		setResult(RESULT_OK, intent);
		
		super.finish();
	
	}
}
