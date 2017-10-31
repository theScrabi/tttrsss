package org.fox.ttrss.offline;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;

import org.fox.ttrss.Application;
import org.fox.ttrss.R;

public class OfflineMasterActivity extends OfflineActivity implements OfflineHeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();

	private static final int OFFLINE_HEADLINES_REQUEST = 1;

	//private boolean m_actionbarUpEnabled = false;
	//private int m_actionbarRevertDepth = 0;
	private boolean m_feedIsSelected = false;
	//private boolean m_feedWasSelected = false;

    private ActionBarDrawerToggle m_drawerToggle;
    private DrawerLayout m_drawerLayout;

    @SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);
		
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_master);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

        setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);

		Application.getInstance().load(savedInstanceState);

        m_drawerLayout = (DrawerLayout) findViewById(R.id.headlines_drawer);

        if (m_drawerLayout != null) {

            m_drawerToggle = new ActionBarDrawerToggle(this, m_drawerLayout, R.string.blank, R.string.blank) {
                @Override
                public void onDrawerOpened(View drawerView) {
                    invalidateOptionsMenu();
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    invalidateOptionsMenu();
                }
            };

            m_drawerLayout.setDrawerListener(m_drawerToggle);
            m_drawerToggle.setDrawerIndicatorEnabled(true);
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState != null) {
			
			//m_actionbarUpEnabled = savedInstanceState.getBoolean("actionbarUpEnabled");
			//m_actionbarRevertDepth = savedInstanceState.getInt("actionbarRevertDepth");
			m_feedIsSelected = savedInstanceState.getBoolean("feedIsSelected");
			//m_feedWasSelected = savedInstanceState.getBoolean("feedWasSelected");
			
			/* if (findViewById(R.id.sw600dp_port_anchor) != null && m_feedWasSelected && m_slidingMenu != null) {
				m_slidingMenu.setBehindWidth(getScreenWidthInPixel() * 2/3);
			} */
			
			if (m_drawerLayout != null && m_feedIsSelected == false) {
                m_drawerLayout.openDrawer(Gravity.START);
            }

		} else {
            if (m_drawerLayout != null) {
                m_drawerLayout.openDrawer(Gravity.START);
            }
			
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (m_prefs.getBoolean("enable_cats", false)) {
				ft.replace(R.id.feeds_fragment, new OfflineFeedCategoriesFragment(), FRAG_CATS);				
			} else {
				ft.replace(R.id.feeds_fragment, new OfflineFeedsFragment(), FRAG_FEEDS);
			}	
		
			ft.commit();
		}
	}

    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (m_drawerToggle != null) m_drawerToggle.syncState();
    }

	/*public void openFeedArticles(int feedId, boolean isCat) {
		if (isSmallScreen()) {
			Intent intent = new Intent(OfflineMasterActivity.this, OfflineDetailActivity.class);
			
			intent.putExtra("feed", feedId);
			intent.putExtra("isCat", isCat);
			intent.putExtra("article", 0);
			startActivityForResult(intent, 0);
		}
	}*/

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        if (m_drawerToggle != null && m_drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

    	switch (item.getItemId()) {
        case R.id.headlines_toggle_sort_order:
            /* SharedPreferences.Editor editor = m_prefs.edit();
            editor.putBoolean("offline_oldest_first", !m_prefs.getBoolean("offline_oldest_first", false));
            editor.commit();
            refresh(); */

            Dialog dialog = new Dialog(this);

            int selectedIndex = m_prefs.getBoolean("offline_oldest_first", false) ? 1 : 0;

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.headlines_sort_articles_title))
                    .setSingleChoiceItems(
                            new String[] {
                                    getString(R.string.headlines_sort_default),
                                    getString(R.string.headlines_sort_oldest_first)
                            },
                            selectedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    switch (which) {
                                        case 0:
                                            if (true) {
                                                SharedPreferences.Editor editor = m_prefs.edit();
                                                editor.putBoolean("offline_oldest_first", false);
                                                editor.apply();
                                            }
                                            break;
                                        case 1:
                                            if (true) {
                                                SharedPreferences.Editor editor = m_prefs.edit();
                                                editor.putBoolean("offline_oldest_first", true);
												editor.apply();
                                            }
                                            break;
                                    }
                                    dialog.cancel();

                                    refresh();
                                }
                            });

            dialog = builder.create();
            dialog.show();

            return true;
		/* case R.id.show_feeds:
			setUnreadOnly(!getUnreadOnly());
			invalidateOptionsMenu();
			refresh();
			return true; */
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putBoolean("feedIsSelected", m_feedIsSelected);

		Application.getInstance().save(out);
	}
	
	public void initMenu() {
		super.initMenu();
		
		if (m_menu != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			/* if (m_drawerLayout != null) {
                boolean isDrawerOpen = m_drawerLayout.isDrawerOpen(Gravity.START);

				m_menu.setGroupVisible(R.id.menu_group_feeds, isDrawerOpen);
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && !isDrawerOpen);
			} else {
				m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());				
			} */

			m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
			m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());

			//m_menu.findItem(R.id.headlines_toggle_sidebar).setVisible(false);
			
			/* MenuItem item = m_menu.findItem(R.id.show_feeds);

			if (getUnreadOnly()) {
				item.setTitle(R.string.menu_all_feeds);
			} else {
				item.setTitle(R.string.menu_unread_feeds);
			} */
		}		
	}
	
	public void onCatSelected(int catId) {
		onCatSelected(catId, m_prefs.getBoolean("browse_cats_like_feeds", false));	
	}
	
	public void onCatSelected(int catId, boolean openAsFeed) {
		OfflineFeedCategoriesFragment fc = (OfflineFeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		if (openAsFeed) {
			if (fc != null) {
				fc.setSelectedFeedId(catId);
			}

			onFeedSelected(catId, true, true);
		} else {
			if (fc != null) {
				fc.setSelectedFeedId(-1);
			}

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			OfflineFeedsFragment ff = new OfflineFeedsFragment();
			ff.initialize(catId, true);

			ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);
			ft.addToBackStack(null);

			//getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			//m_actionbarUpEnabled = true;
			//m_actionbarRevertDepth = m_actionbarRevertDepth + 1;

			ft.commit();
		}
	}

    @Override
    public void onBackPressed() {
        if (m_drawerLayout != null && !m_drawerLayout.isDrawerOpen(Gravity.START) &&
                (getSupportFragmentManager().getBackStackEntryCount() > 0 || m_feedIsSelected)) {

            m_drawerLayout.openDrawer(Gravity.START);
        } else {
			try {
				super.onBackPressed();
			} catch (IllegalStateException e) {
				// java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
				e.printStackTrace();
			}
        }
    }

    public void onFeedSelected(int feedId) {
		onFeedSelected(feedId, false, true);		
	}

	public void onFeedSelected(final int feedId, final boolean isCat, boolean open) {
		
		if (open) {

			if (m_drawerLayout != null) {
				m_drawerLayout.closeDrawers();
			}

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					FragmentTransaction ft = getSupportFragmentManager()
							.beginTransaction();
					
					OfflineHeadlinesFragment hf = new OfflineHeadlinesFragment();
					hf.initialize(feedId, isCat, false);
					ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
					
					ft.commit();

					m_feedIsSelected = true;
					//m_feedWasSelected = true;

				}
			}, 250);
		}		
	}

	@Override
	public void onArticleSelected(int articleId, boolean open) {
		
		if (!open) {
			SQLiteStatement stmt = getDatabase().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
							+ " = ?");
	
			stmt.bindLong(1, articleId);
			stmt.execute();
			stmt.close();
		}
		
		invalidateOptionsMenu();
		
		if (open) {
			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			Intent intent = new Intent(OfflineMasterActivity.this, OfflineDetailActivity.class);
			intent.putExtra("feed", hf.getFeedId());
			intent.putExtra("isCat", hf.getFeedIsCat());
			intent.putExtra("article", articleId);
	 	   
			startActivityForResult(intent, OFFLINE_HEADLINES_REQUEST);
			overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

		} else {
			refresh();
		}

	}

	@Override
	public void onArticleSelected(int articleId) {
		onArticleSelected(articleId, true);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == OFFLINE_HEADLINES_REQUEST) {

			OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			if (ohf != null) {
				ohf.refresh();
			}
		}

	}

}
