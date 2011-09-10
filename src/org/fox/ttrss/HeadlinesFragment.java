package org.fox.ttrss;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
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

public class HeadlinesFragment extends Fragment implements OnItemClickListener {
	private final String TAG = this.getClass().getSimpleName();
	protected int m_feedId;
	protected SharedPreferences m_prefs;
	protected SQLiteDatabase m_db;
	protected Cursor m_cursor;	
	protected SimpleCursorAdapter m_adapter;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_feedId = savedInstanceState.getInt("feedId");
		}

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		DatabaseHelper helper = new DatabaseHelper(getActivity());

		m_db = helper.getReadableDatabase();		
		m_cursor = m_db.query("articles", null, "feed_id = ?", new String[] { String.valueOf(m_feedId) }, null, null, "updated DESC");
		
		m_adapter = new SimpleCursorAdapter(getActivity(), R.layout.headlines_row, m_cursor,
				new String[] { "title", "excerpt" }, new int[] { R.id.title, R.id.excerpt }, 0);

		ListView list = (ListView) view.findViewById(R.id.headlines);
		
		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
			list.setEmptyView(view.findViewById(R.id.no_headlines));
			list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
		}
		
		return view;    	
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_cursor.close();
		m_db.close();
	}

	public void initialize(int feedId) {
		m_feedId = feedId;
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.headlines);
		
		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);
			
			if (cursor != null) {
				int articleId = (int) cursor.getLong(0);

				Log.d(TAG, "clicked on article " + articleId);
				
				viewArticle(articleId);
				
			}			
		}		
		
	}

	private void viewArticle(int articleId) {
		FragmentTransaction ft = getFragmentManager().beginTransaction();			
		ArticleFragment frag = new ArticleFragment();
		
		frag.initialize(articleId);
		
		/* LinearLayout v = (LinearLayout) getActivity().findViewById(R.id.headlines_container);

		if (v != null) {
			ObjectAnimator anim = ObjectAnimator.ofFloat(v, "weightSum", 0f, 0.5f);
			anim.setDuration(1000);
			anim.start();

		} */
		
		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.replace(R.id.article_container, frag);
		ft.commit();
		
		getActivity().findViewById(R.id.article_container).setVisibility(View.VISIBLE);
		
		//m_adapter.notifyDataSetChanged();

	}

	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);
		
		out.putInt("feedId", m_feedId);
	}

}
