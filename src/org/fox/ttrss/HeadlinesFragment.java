package org.fox.ttrss;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class HeadlinesFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	protected SharedPreferences m_prefs;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		return view;    	
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		
	}

	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
	}

}
