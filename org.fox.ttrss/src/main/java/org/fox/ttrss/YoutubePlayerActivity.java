package org.fox.ttrss;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import icepick.State;


public class YoutubePlayerActivity extends CommonActivity implements YouTubePlayer.OnInitializedListener {

    private final String TAG = this.getClass().getSimpleName();
    private static final String DEVELOPER_KEY = "AIzaSyD8BS4Uj21jg_gHZfP4v0VXrAWiwqd05nk";

    @State protected String m_streamUri;
    @State protected String m_videoId;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.DarkTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_player);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!isPortrait())
            getSupportActionBar().hide();

        setTitle(getIntent().getStringExtra("title"));

        if (savedInstanceState == null) {
            m_streamUri = getIntent().getStringExtra("streamUri");
            m_videoId = getIntent().getStringExtra("vid");
        }

        YouTubePlayerSupportFragment frag = (YouTubePlayerSupportFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_youtube_player);
        frag.initialize(DEVELOPER_KEY, this);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isPortrait())
            getSupportActionBar().hide();
        else
            getSupportActionBar().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_youtube_player, menu);
        return true;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        getMenuInflater().inflate(R.menu.activity_youtube_player, menu);

        super.onCreateContextMenu(menu, v, menuInfo);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onContextItemSelected(item); // this is really bad :()
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.article_vid_open:
                if (m_streamUri != null) {
                    try {
                        openUri(Uri.parse(m_streamUri));
                    } catch (Exception e) {
                        e.printStackTrace();
                        toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_vid_share:
                if (m_streamUri != null) {
                    shareText(m_streamUri);
                }
                return true;
            default:
                Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored) {
        Log.d(TAG, "youtube: init success");

        findViewById(R.id.video_loading).setVisibility(View.GONE);

        if (!wasRestored) {
            player.cueVideo(m_videoId);
        }
    }

    @Override
    public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult result) {
        Log.d(TAG, "youtube: init failure");

        findViewById(R.id.video_loading).setVisibility(View.GONE);

        toast(result.toString());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }

    }
}
