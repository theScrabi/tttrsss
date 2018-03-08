package org.fox.ttrss;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import com.ToxicBakery.viewpager.transforms.DepthPageTransformer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.fox.ttrss.types.GalleryEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import icepick.State;
import me.relex.circleindicator.CircleIndicator;

public class GalleryActivity extends CommonActivity {
    private final String TAG = this.getClass().getSimpleName();

    @State protected ArrayList<GalleryEntry> m_items = new ArrayList<>();
    @State protected String m_title;
    private ArticleImagesPagerAdapter m_adapter;
    @State public String m_content;
    private ViewPager m_pager;
    private ProgressBar m_checkProgress;

    private class ArticleImagesPagerAdapter extends FragmentStatePagerAdapter {
        private List<GalleryEntry> m_items;

        public ArticleImagesPagerAdapter(FragmentManager fm, List<GalleryEntry> items) {
            super(fm);
            m_items = items;
        }

        @Override
        public int getCount() {
            return m_items.size();
        }

        @Override
        public Fragment getItem(int position) {

            //Log.d(TAG, "getItem: " + position + " " + m_urls.get(position));

            GalleryEntry item = m_items.get(position);

            switch (item.type) {
                case TYPE_IMAGE:
                    if (true) {
                        GalleryImageFragment frag = new GalleryImageFragment();
                        frag.initialize(item.url);

                        return frag;
                    }
                    break;
                case TYPE_VIDEO:
                    if (true) {
                        GalleryVideoFragment frag = new GalleryVideoFragment();
                        frag.initialize(item.url, item.coverUrl);

                        return frag;
                    }
                    break;
            }

            return null;
        }
    }

    private class MediaProgressResult {
        GalleryEntry item;
        int position;
        int count;

        public MediaProgressResult(GalleryEntry item, int position, int count) {
            this.item = item;
            this.position = position;
            this.count = count;
        }
    }

    private class MediaCheckTask extends AsyncTask<List<GalleryEntry>, MediaProgressResult, List<GalleryEntry>> {

        private List<GalleryEntry> m_checkedItems = new ArrayList<>();

        @Override
        protected List<GalleryEntry> doInBackground(List<GalleryEntry>... params) {

            ArrayList<GalleryEntry> items = new ArrayList<>(params[0]);
            int position = 0;

            for (GalleryEntry item : items) {
                if (!isCancelled()) {
                    ++position;

                    Log.d(TAG, "checking: " + item.url + " " + item.coverUrl);

                    if (item.type == GalleryEntry.GalleryEntryType.TYPE_IMAGE) {
                        try {
                            Bitmap bmp = Glide.with(GalleryActivity.this)
                                    .load(item.url)
                                    .asBitmap()
                                    .skipMemoryCache(false)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .dontTransform()
                                    .into(HeadlinesFragment.FLAVOR_IMG_MIN_SIZE, HeadlinesFragment.FLAVOR_IMG_MIN_SIZE)
                                    .get();

                            if (bmp.getWidth() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE && bmp.getHeight() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE) {
                                m_checkedItems.add(item);
                                publishProgress(new MediaProgressResult(item, position, items.size()));
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        } catch (OutOfMemoryError e) {
                            e.printStackTrace();
                        }

                    } else {
                        m_checkedItems.add(item);
                        publishProgress(new MediaProgressResult(item, position, items.size()));
                    }
                }
            }

            return m_checkedItems;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ActivityCompat.postponeEnterTransition(this);

        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setTheme(R.style.DarkTheme);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gallery);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //m_progress = (ProgressBar) findViewById(R.id.gallery_check_progress);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().hide();

        ArrayList<GalleryEntry> uncheckedItems = new ArrayList<>();

        if (savedInstanceState == null) {
            m_title = getIntent().getStringExtra("title");
            m_content = getIntent().getStringExtra("content");

            String imgSrcFirst = getIntent().getStringExtra("firstSrc");

            Document doc = Jsoup.parse(m_content);
            Elements elems = doc.select("img,video");

            boolean firstFound = false;

            for (Element elem : elems) {

                GalleryEntry item = new GalleryEntry();

                if ("video".equals(elem.tagName().toLowerCase())) {
                    String cover = elem.attr("poster");

                    Element source = elem.select("source").first();
                    String src = source.attr("src");

                    //Log.d(TAG, "vid/src=" + src);

                    if (src.startsWith("//")) {
                        src = "https:" + src;
                    }

                    if (imgSrcFirst.equals(src))
                        firstFound = true;

                    item.url = src;
                    item.coverUrl = cover;
                    item.type = GalleryEntry.GalleryEntryType.TYPE_VIDEO;

                } else {
                    String src = elem.attr("src");

                    if (src.startsWith("//")) {
                        src = "https:" + src;
                    }

                    if (imgSrcFirst.equals(src))
                        firstFound = true;

                    Log.d(TAG, "img/fir=" + imgSrcFirst);
                    Log.d(TAG, "img/src=" + src + "; ff=" + firstFound);

                    try {
                        Uri checkUri = Uri.parse(src);

                        if (!"data".equals(checkUri.getScheme().toLowerCase())) {
                            item.url = src;
                            item.type = GalleryEntry.GalleryEntryType.TYPE_IMAGE;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                if (firstFound && item.url != null) {
                    if (m_items.size() == 0)
                        m_items.add(item);
                    else
                        uncheckedItems.add(item);
                }
            }
        }

        findViewById(R.id.gallery_overflow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(GalleryActivity.this, v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.content_gallery_entry, popup.getMenu());

                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        String url = m_items.get(m_pager.getCurrentItem()).url;

                        return onImageMenuItemSelected(item, url);
                    }
                });

                popup.show();

            }
        });

        setTitle(m_title);

        m_adapter = new ArticleImagesPagerAdapter(getSupportFragmentManager(), m_items);

        m_pager = findViewById(R.id.gallery_pager);
        m_pager.setAdapter(m_adapter);
        m_pager.setPageTransformer(true, new DepthPageTransformer());

        CircleIndicator indicator = findViewById(R.id.gallery_pager_indicator);
        indicator.setViewPager(m_pager);
        m_adapter.registerDataSetObserver(indicator.getDataSetObserver());

        m_checkProgress = findViewById(R.id.gallery_check_progress);

        Log.d(TAG, "items to check:" + uncheckedItems.size());

        MediaCheckTask mct = new MediaCheckTask() {
            @Override
            protected void onProgressUpdate(MediaProgressResult... result) {
                m_items.add(result[0].item);
                m_adapter.notifyDataSetChanged();

                if (result[0].position < result[0].count) {
                    m_checkProgress.setVisibility(View.VISIBLE);
                    m_checkProgress.setMax(result[0].count);
                    m_checkProgress.setProgress(result[0].position);
                } else {
                    m_checkProgress.setVisibility(View.GONE);
                }

            }

            @Override
            protected void onPostExecute(List<GalleryEntry> result) {
                //m_items.addAll(result);
                //m_adapter.notifyDataSetChanged();
            }
        };

        mct.execute(uncheckedItems);

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = m_pager.getCurrentItem();
        String url = m_items.get(position).url;

        if (onImageMenuItemSelected(item, url))
            return true;

        return super.onContextItemSelected(item);
    }

    public boolean onImageMenuItemSelected(MenuItem item, String url) {
        switch (item.getItemId()) {
            case R.id.article_img_open:
                if (url != null) {
                    try {
                        openUri(Uri.parse(url));
                    } catch (Exception e) {
                        e.printStackTrace();
                        toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_img_copy:
                if (url != null) {
                    copyToClipboard(url);
                }
                return true;
            case R.id.article_img_share:
                if (url != null) {
                    shareText(url);
                }
                return true;
            case R.id.article_img_view_caption:
                if (url != null) {
                    displayImageCaption(url, m_content);
                }
                return true;
            default:
                Log.d(TAG, "onImageMenuItemSelected, unhandled id=" + item.getItemId());
                return false;
        }
    }

}
