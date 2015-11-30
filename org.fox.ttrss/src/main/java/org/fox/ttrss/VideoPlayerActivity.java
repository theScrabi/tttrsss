package org.fox.ttrss;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.MediaController;

import java.io.IOException;


public class VideoPlayerActivity extends CommonActivity {

    private final String TAG = this.getClass().getSimpleName();
    private String m_streamUri;
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;

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

        surfaceView = (SurfaceView) findViewById(R.id.video_player);
        registerForContextMenu(surfaceView);

        setTitle(getIntent().getStringExtra("title"));

        if (savedInstanceState == null) {
            m_streamUri = getIntent().getStringExtra("streamUri");
        } else {
            m_streamUri = savedInstanceState.getString("streamUri");
        }

        final MediaController mediaController = new MediaController(this);

        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mediaController.isShowing())
                    mediaController.show(5000);
                else
                    mediaController.hide();
            }
        });

        mediaPlayer = new MediaPlayer();

        mediaController.setMediaPlayer(new MediaController.MediaPlayerControl() {
            @Override
            public void start() {
                mediaPlayer.start();
            }

            @Override
            public void pause() {
                mediaPlayer.pause();
            }

            @Override
            public int getDuration() {
                return mediaPlayer.getDuration();
            }

            @Override
            public int getCurrentPosition() {
                return mediaPlayer.getCurrentPosition();
            }

            @Override
            public void seekTo(int pos) {
                mediaPlayer.seekTo(pos);
            }

            @Override
            public boolean isPlaying() {
                return mediaPlayer.isPlaying();
            }

            @Override
            public int getBufferPercentage() {
                return 0;
            }

            @Override
            public boolean canPause() {
                return true;
            }

            @Override
            public boolean canSeekBackward() {
                return true;
            }

            @Override
            public boolean canSeekForward() {
                return true;
            }

            @Override
            public int getAudioSessionId() {
                return 0;
            }
        });


        SurfaceHolder sh = surfaceView.getHolder();

        try {
            mediaPlayer.setDataSource(this, Uri.parse(m_streamUri));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, surfaceView.getWidth() + " " + surfaceView.getHeight());

        final FrameLayout.LayoutParams svLayoutParams = new FrameLayout.LayoutParams(surfaceView.getWidth(), surfaceView.getHeight());

        sh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mediaPlayer.setDisplay(holder);
                try {
                    mediaPlayer.prepareAsync();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                      @Override
                      public void onPrepared(MediaPlayer mp) {

                          View loadingBar = findViewById(R.id.video_loading);
                          if (loadingBar != null) loadingBar.setVisibility(View.GONE);

                          resizeSurface();
                          mp.setLooping(true);
                          mp.start();
                      }
                  }

                );
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                //
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                //
            }
        });


            mediaController.setAnchorView(surfaceView);
        }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isPortrait())
            getSupportActionBar().hide();
        else
            getSupportActionBar().show();

        resizeSurface();
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
                        openUri(Uri.parse(m_streamUri));
                    } catch (Exception e) {
                        e.printStackTrace();
                        toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_vid_share:
                if (m_streamUri != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);

                    intent.setType("text/plain");
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

    protected void resizeSurface() {
        // get the dimensions of the video (only valid when surfaceView is set)
        float videoWidth = mediaPlayer.getVideoWidth();
        float videoHeight = mediaPlayer.getVideoHeight();

        Rect rectangle = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(rectangle);

        int actionBarHeight = isPortrait() ? getSupportActionBar().getHeight() : 0;

        Display display = getWindowManager().getDefaultDisplay();
        float containerWidth = display.getWidth();
        float containerHeight = display.getHeight() - rectangle.top - actionBarHeight;

        // set dimensions to surfaceView's layout params (maintaining aspect ratio)
        android.view.ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = (int) containerWidth;
        lp.height = (int) ((videoHeight / videoWidth) * containerWidth);
        if(lp.height > containerHeight) {
            lp.width = (int) ((videoWidth / videoHeight) * containerHeight);
            lp.height = (int) containerHeight;
        }
        surfaceView.setLayoutParams(lp);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }

    }

}
