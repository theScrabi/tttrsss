package org.fox.ttrss;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;


public class VideoPlayerActivity extends CommonActivity {

    private final String TAG = this.getClass().getSimpleName();
    private String m_streamUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.DarkTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!isPortrait())
            getSupportActionBar().hide();

        VideoView videoView = (VideoView) findViewById(R.id.video_player);
        registerForContextMenu(videoView); // doesn't work :[

        setTitle(getIntent().getStringExtra("title"));

        if (savedInstanceState == null) {
            m_streamUri = getIntent().getStringExtra("streamUri");
        } else {
            m_streamUri = savedInstanceState.getString("streamUri");
        }

        final MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setVideoURI(Uri.parse(m_streamUri));

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.seekTo(0);
            }
        });

        videoView.start();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isPortrait())
            getSupportActionBar().hide();
        else
            getSupportActionBar().show();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("streamUri", m_streamUri);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_video_player, menu);
        return true;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        getMenuInflater().inflate(R.menu.activity_video_player, menu);

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
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(m_streamUri));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_vid_share:
                if (m_streamUri != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);

                    intent.setType("video/mp4");
                    intent.putExtra(Intent.EXTRA_SUBJECT, m_streamUri);
                    intent.putExtra(Intent.EXTRA_TEXT, m_streamUri);

                    startActivity(Intent.createChooser(intent, m_streamUri));
                }
                return true;
            default:
                Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
                return super.onContextItemSelected(item);
        }
    }

}
