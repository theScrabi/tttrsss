package org.fox.ttrss;

import java.util.Timer;

import org.fox.ttrss.FeedsFragment.FeedsListAdapter;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

public class ArticleFragment extends Fragment {
	SharedPreferences m_prefs;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			//
		}
		
		View view = inflater.inflate(R.layout.article_fragment, container, false);

		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

}
