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

	protected FeedsListAdapter m_adapter;
	protected SharedPreferences m_prefs;
	protected int m_activeFeedId;
	protected Cursor m_cursor;
	protected SQLiteDatabase m_db;

/*	private Timer m_timer;
	private TimerTask m_updateTask = new TimerTask() {
		@Override
		public void run() {
			downloadFeeds();
		}		
	}; */

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_activeFeedId = savedInstanceState.getInt("activeFeedId");
		}

		View view = inflater.inflate(R.layout.feeds_fragment, container, false);

		DatabaseHelper helper = new DatabaseHelper(getActivity());

		m_db = helper.getReadableDatabase();		
		m_cursor = m_db.query("feeds_unread", null, null, null, null, null, "unread DESC, title");

		m_adapter = new FeedsListAdapter(getActivity(), R.layout.feeds_row, m_cursor,
				new String[] { "title", "unread" }, new int[] { R.id.title, R.id.unread_counter }, 0);

		ListView list = (ListView) view.findViewById(R.id.feeds);

		if (list != null) {
			list.setAdapter(m_adapter);		
			list.setOnItemClickListener(this);
			list.setEmptyView(view.findViewById(R.id.no_unread_feeds));
			list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
		}

/*		View pb = view.findViewById(R.id.loading_progress);

		if (pb != null && m_cursor.getCount() == 0)
			pb.setVisibility(View.VISIBLE); */

//		m_timer = new Timer("UpdateFeeds");
//		m_timer.schedule(m_updateTask, 1000L, 60*1000L);

		return view;    	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_cursor.close();
		m_db.close();

//		m_timer.cancel();
//		m_timer = null;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

		@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("activeFeedId", m_activeFeedId);
	}

	class FeedsListAdapter extends SimpleCursorAdapter {

		private Context context;
		private int layout;

		public FeedsListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);

			this.context = context;
			this.layout = layout;
		}		

	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)getActivity().findViewById(R.id.feeds);

		if (list != null) {
			Cursor cursor = (Cursor) list.getItemAtPosition(position);

			if (cursor != null) {
				int feedId = (int) cursor.getLong(0);

				Log.d(TAG, "clicked on feed " + feedId);

				viewFeed(feedId);				
			}			
		}		
	} 

	private void viewFeed(int feedId) {
		m_activeFeedId = feedId;

		FragmentTransaction ft = getFragmentManager().beginTransaction();			
		HeadlinesFragment frag = new HeadlinesFragment();

		frag.initialize(feedId);

		ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
		ft.replace(R.id.headlines_container, frag);
		ft.commit();

		m_adapter.notifyDataSetChanged();

	}

	public synchronized void updateListView() {
		m_cursor.requery();
		m_adapter.notifyDataSetChanged();		
	}	

}
