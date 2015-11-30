package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;

import com.google.gson.JsonElement;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;
import org.fox.ttrss.widget.SmallWidgetProvider;

import java.util.Date;
import java.util.HashMap;

public class MasterActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	
	private static final int HEADLINES_REQUEST = 1;
	
	protected SharedPreferences m_prefs;
	protected long m_lastRefresh = 0;
	
	private boolean m_feedIsSelected = false;
    private boolean m_userFeedSelected = false;

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

		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		Application.getInstance().load(savedInstanceState);

        m_drawerLayout = (DrawerLayout) findViewById(R.id.headlines_drawer);

        if (m_drawerLayout != null) {

			m_drawerToggle = new ActionBarDrawerToggle(this, m_drawerLayout, R.string.blank, R.string.blank) {
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);

                    getSupportActionBar().show();
                    invalidateOptionsMenu();
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);

                    if (m_prefs.getBoolean("drawer_open_on_start", true)) {
                        SharedPreferences.Editor editor = m_prefs.edit();
                        editor.putBoolean("drawer_open_on_start", false);
                        editor.apply();
                    }

                    invalidateOptionsMenu();
                }
            };

            m_drawerLayout.setDrawerListener(m_drawerToggle);
            m_drawerToggle.setDrawerIndicatorEnabled(true);

            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        if (savedInstanceState == null) {
            if (m_drawerLayout != null && m_prefs.getBoolean("drawer_open_on_start", true)) {
                m_drawerLayout.openDrawer(Gravity.START);
            }

			final Intent i = getIntent();
			boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);
			
			Log.d(TAG, "is_shortcut_mode: " + shortcutMode);

			if (shortcutMode) {
				LoginRequest lr = new LoginRequest(this, false, new OnLoginFinishedListener() {
					
					@Override
					public void OnLoginSuccess() {
						int feedId = i.getIntExtra("feed_id", 0);
						boolean isCat = i.getBooleanExtra("feed_is_cat", false);
						String feedTitle = i.getStringExtra("feed_title");
						
						Feed tmpFeed = new Feed(feedId, feedTitle, isCat);
						
						onFeedSelected(tmpFeed, false);
					}
					
					@Override
					public void OnLoginFailed() {
						login();						
					}
				});
				
				HashMap<String, String> map = new HashMap<String, String>() {
					{
						put("op", "login");
						put("user", m_prefs.getString("login", "").trim());
						put("password", m_prefs.getString("password", "").trim());
					}
				};

				lr.execute(map);
			}
			
			//m_pullToRefreshAttacher.setRefreshing(true);

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (m_prefs.getBoolean("enable_cats", false)) {
				ft.replace(R.id.feeds_fragment, new FeedCategoriesFragment(), FRAG_CATS);
			} else {
				ft.replace(R.id.feeds_fragment, new FeedsFragment(), FRAG_FEEDS);
			}

            if (m_prefs.getBoolean("open_fresh_on_startup", true)) {
                HeadlinesFragment hf = new HeadlinesFragment();
                hf.initialize(new Feed(-3, getString(R.string.fresh_articles), false));

                ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);
            } else if (m_drawerLayout != null) {
                m_drawerLayout.openDrawer(Gravity.START);
            }

			ft.commit();

            m_feedIsSelected = true;

			checkTrial(true);

		} else { // savedInstanceState != null
			//m_actionbarUpEnabled = savedInstanceState.getBoolean("actionbarUpEnabled");
			//m_actionbarRevertDepth = savedInstanceState.getInt("actionbarRevertDepth");
			m_feedIsSelected = savedInstanceState.getBoolean("feedIsSelected");
            m_userFeedSelected = savedInstanceState.getBoolean("userFeedSelected");
			//m_feedWasSelected = savedInstanceState.getBoolean("feedWasSelected");

			/* if (findViewById(R.id.sw600dp_port_anchor) != null && m_feedWasSelected && m_slidingMenu != null) {
				m_slidingMenu.setBehindWidth(getScreenWidthInPixel() * 2/3);
			} */
			
			if (m_drawerLayout != null && m_feedIsSelected == false) {
				m_drawerLayout.openDrawer(Gravity.START);
			}

		}
		
	}

    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (m_drawerToggle != null) m_drawerToggle.syncState();
    }

	@Override
	protected void initMenu() {
		super.initMenu();
		
		if (m_menu != null && getSessionId() != null) {
			Fragment ff = getSupportFragmentManager().findFragmentByTag(FRAG_FEEDS);
			Fragment cf = getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			
			/* if (m_drawerLayout != null) {
                boolean isDrawerOpen = m_drawerLayout.isDrawerOpen(Gravity.START);

				m_menu.setGroupVisible(R.id.menu_group_feeds, isDrawerOpen);
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded() && !isDrawerOpen);
			} else {
				m_menu.setGroupVisible(R.id.menu_group_feeds, (ff != null && ff.isAdded()) || (cf != null && cf.isAdded()));
				m_menu.setGroupVisible(R.id.menu_group_headlines, hf != null && hf.isAdded());
				
				m_menu.findItem(R.id.update_headlines).setVisible(false);
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

    public void onFeedSelected(Feed feed) {
        onFeedSelected(feed, true);
    }

	public void onFeedSelected(Feed feed, final boolean selectedByUser) {

		ImageLoader.getInstance().clearMemoryCache();

        FragmentTransaction ft = getSupportFragmentManager()
                .beginTransaction();

        HeadlinesFragment hf = new HeadlinesFragment();
        hf.initialize(feed);

        ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);

        ft.commit();

        m_feedIsSelected = true;
        m_userFeedSelected = selectedByUser;
        //m_feedWasSelected = true;

        if (m_drawerLayout != null) {
            m_drawerLayout.closeDrawers();
        }

        Date date = new Date();

        if (date.getTime() - m_lastRefresh > 10000) {
            m_lastRefresh = date.getTime();
            refresh(false);
        }
	}
	
	public void onCatSelected(FeedCategory cat, boolean openAsFeed) {
		FeedCategoriesFragment fc = (FeedCategoriesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_CATS);
		
		//m_pullToRefreshAttacher.setRefreshing(true);
		
		if (!openAsFeed) {
			
			if (fc != null) {
				fc.setSelectedCategory(null);
			}

			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();

			FeedsFragment ff = new FeedsFragment();
			ff.initialize(cat, true);
			ft.replace(R.id.feeds_fragment, ff, FRAG_FEEDS);

			ft.addToBackStack(null);
			ft.commit();
			
			//getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			//m_actionbarUpEnabled = true;
			//m_actionbarRevertDepth = m_actionbarRevertDepth + 1;

		} else {
			
			if (fc != null) {
				fc.setSelectedCategory(cat);
			}

			Feed feed = new Feed(cat.id, cat.title, true);
			onFeedSelected(feed);
		}
	}
	
	public void onCatSelected(FeedCategory cat) {
		onCatSelected(cat, m_prefs.getBoolean("browse_cats_like_feeds", false));		
	}

    @Override
    public void logout() {
        super.logout();

        finish();
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        if (m_drawerToggle != null && m_drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
        case R.id.headlines_toggle_sort_order:
            Dialog dialog = new Dialog(this);

            String sortMode = getSortMode();

            int selectedIndex = 0;

            if (sortMode.equals("feed_dates")) {
                selectedIndex = 1;
            } else if (sortMode.equals("date_reverse")) {
                selectedIndex = 2;
            } else if (sortMode.equals("title")) {
                selectedIndex = 3;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.headlines_sort_articles_title))
                    .setSingleChoiceItems(
                            new String[] {
                                    getString(R.string.headlines_sort_default),
                                    getString(R.string.headlines_sort_newest_first),
                                    getString(R.string.headlines_sort_oldest_first),
                                    getString(R.string.headlines_sort_title)
                            },
                            selectedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    switch (which) {
                                        case 0:
                                            setSortMode("default");
                                            break;
                                        case 1:
                                            setSortMode("feed_dates");
                                            break;
                                        case 2:
                                            setSortMode("date_reverse");
                                            break;
                                        case 3:
                                            setSortMode("title");
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
		/*case R.id.update_feeds:
			//m_pullToRefreshAttacher.setRefreshing(true);
			refresh();
			return true;*/
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

    @Override
    public void onBackPressed() {
        if (m_drawerLayout != null && !m_drawerLayout.isDrawerOpen(Gravity.START) &&
                (getSupportFragmentManager().getBackStackEntryCount() > 0 || m_userFeedSelected)) {

            m_drawerLayout.openDrawer(Gravity.START);
        } else {
            super.onBackPressed();
        }
    }

	@Override
	protected void loginSuccess(boolean refresh) {
		invalidateOptionsMenu();

		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);	
		
		out.putBoolean("feedIsSelected", m_feedIsSelected);
        out.putBoolean("userFeedSelected", m_userFeedSelected);

		Application.getInstance().save(out);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		invalidateOptionsMenu();

	}

	@Override
	public void onArticleListSelectionChange(ArticleList m_selectedArticles) {
		invalidateOptionsMenu();
	}

	/* public void openFeedArticles(Feed feed) {
		//Application.getInstance().m_loadedArticles.clear();
		
		Intent intent = new Intent(MasterActivity.this, DetailActivity.class);
		intent.putExtra("feed", feed);
		intent.putExtra("article", (Article)null);
		intent.putExtra("searchQuery", (String)null);

		startActivityForResult(intent, HEADLINES_REQUEST);
		overridePendingTransition(R.anim.right_slide_in, 0);
	} */
	
	public void onArticleSelected(Article article, boolean open) {
		if (open) {
			HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			Intent intent = new Intent(MasterActivity.this, DetailActivity.class);
			intent.putExtra("feed", hf.getFeed());
			//intent.putExtra("article", article);
			intent.putExtra("searchQuery", hf.getSearchQuery());
            //intent.putExtra("articles", (Parcelable)hf.getAllArticles());
            Application.getInstance().tmpArticleList = hf.getAllArticles();
			Application.getInstance().tmpArticle = article;

            /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startActivityForResult(intent, HEADLINES_REQUEST, ActivityOptions.makeSceneTransitionAnimation(this).toBundle());
            } else {
                startActivityForResult(intent, HEADLINES_REQUEST);
            } */

            // mysterious crashes somewhere in gl layer (?) on some feeds if we use activitycompat transitions here on LP so welp
            startActivityForResult(intent, HEADLINES_REQUEST);
			overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);


		} else {
			invalidateOptionsMenu();

            if (article.unread) {
			    article.unread = false;
			    saveArticleUnread(article);
		    }
        }
	}

    @Override
    public void onStop() {
        super.onStop();

        Intent updateWidgetIntent = new Intent(SmallWidgetProvider.ACTION_REQUEST_UPDATE);
        sendBroadcast(updateWidgetIntent);
    }

    @Override
	public void onArticleSelected(Article article) {
		onArticleSelected(article, true);		
	}

	public void catchupFeed(final Feed feed) {
		super.catchupFeed(feed);
		refresh();
	}

	@Override
	public void onHeadlinesLoaded(boolean appended) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == HEADLINES_REQUEST && data != null) {
			//Application.getInstance().m_activeArticle = null;

            //ArrayList<Article> tmp = data.getParcelableArrayListExtra("articles");
            Article article = data.getParcelableExtra("activeArticle");
            ArticleList articles = Application.getInstance().tmpArticleList;

            if (articles != null) {
                HeadlinesFragment hf = (HeadlinesFragment)getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

                if (hf != null) {
                    hf.setArticles(articles);
                    hf.setActiveArticle(article);
                }
            }


		}		
	}

	public void createFeedShortcut(Feed feed) {
		final Intent shortcutIntent = new Intent(this, MasterActivity.class);
		shortcutIntent.putExtra("feed_id", feed.id);
		shortcutIntent.putExtra("feed_is_cat", feed.is_cat);
		shortcutIntent.putExtra("feed_title", feed.title);
		shortcutIntent.putExtra("shortcut_mode", true);
		
		Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
		
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, feed.title);
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher));
		intent.putExtra("duplicate", false);
		
		sendBroadcast(intent);
		
		toast(R.string.shortcut_has_been_placed_on_the_home_screen);
	}
	
	public void createCategoryShortcut(FeedCategory cat) {
		createFeedShortcut(new Feed(cat.id, cat.title, true));
	}

	public void unsubscribeFeed(final Feed feed) {
		ApiRequest req = new ApiRequest(getApplicationContext()) {
			protected void onPostExecute(JsonElement result) {
				refresh();
			}
		};

		@SuppressWarnings("serial")
		HashMap<String, String> map = new HashMap<String, String>() {
			{
				put("sid", getSessionId());
				put("op", "unsubscribeFeed");
				put("feed_id", String.valueOf(feed.id));
			}
		};
		
		req.execute(map);

	}
}
