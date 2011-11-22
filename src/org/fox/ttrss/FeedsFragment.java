package org.fox.ttrss;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class FeedsFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			//m_activeFeedId = savedInstanceState.getInt("activeFeedId");
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

		@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		//out.putInt("activeFeedId", m_activeFeedId);
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
	} 

}
