package org.fox.ttrss;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class HeadlinesFragment extends Fragment {
	private final String TAG = this.getClass().getSimpleName();
	protected String m_sessionId;
	protected int m_feedId;
	protected SharedPreferences m_prefs;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_sessionId = savedInstanceState.getString("sessionId");
		}
		
		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		/* m_adapter = new FeedsListAdapter(getActivity(), R.id.feeds_row, m_feeds);
		
		ListView list = (ListView) view.findViewById(R.id.feeds);
		
		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
		} */
		
		return view;    	
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	public void initialize(String sessionId, int feedId) {
		m_sessionId = sessionId;
		m_feedId = feedId;
	}

}
