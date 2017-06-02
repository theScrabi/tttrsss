package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
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
import android.view.ContextMenu;
import android.view.View;
import android.widget.ProgressBar;

import com.ToxicBakery.viewpager.transforms.DepthPageTransformer;
import com.bumptech.glide.Glide;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import me.relex.circleindicator.CircleIndicator;

public class ArticleImagesPagerActivity extends CommonActivity {
    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<GalleryEntry> m_items;
    //private ArrayList<GalleryEntry> m_checkedItems;
    private String m_title;
    private ArticleImagesPagerAdapter m_adapter;
    public String m_content;
    private ProgressBar m_progress;
    private ViewPager m_pager;

    private enum GalleryEntryType { TYPE_IMAGE, TYPE_VIDEO };

    private class GalleryEntry implements Serializable {
        String url;
        GalleryEntryType type;
        String coverUrl;
    }

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
                        ArticleImageFragment frag = new ArticleImageFragment();
                        frag.initialize(item.url);

                        return frag;
                    }
                    break;
                case TYPE_VIDEO:
                    if (true) {
                        ArticleVideoFragment frag = new ArticleVideoFragment();
                        frag.initialize(item.url, item.coverUrl);

                        return frag;
                    }
                    break;
            }

            return null;
        }
    }

    /*private class ImageCheckTask extends AsyncTask<List<GalleryEntry>, Integer, List<GalleryEntry>> {
        private GalleryEntry m_lastCheckedItem;

        @Override
        protected List<GalleryEntry> doInBackground(List<GalleryEntry>... items) {

            List<GalleryEntry> tmp = new ArrayList<>(items[0]);

            int position = 0;

            for (GalleryEntry item : tmp) {

                if (!isCancelled()) {
                    String url = item.url;

                    position++;

                    try {
                        Bitmap bmp = Glide.with(ArticleImagesPagerActivity.this)
                                .load(url)
                                .asBitmap()
                                .into(-1, -1)
                                .get();

                        if (bmp != null && bmp.getWidth() > 128 && bmp.getHeight() > 128) {
                            m_lastCheckedItem = item;
                            publishProgress(position);
                        } else {
                            m_lastCheckedItem = null;
                            publishProgress(position);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }

                }
            }

            return -1;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            if (!isFinishing() && m_adapter != null) {
                Log.d(TAG, "progr=" + progress[0]);

                m_adapter.notifyDataSetChanged();

                m_progress.setProgress(Integer.valueOf(progress[1]));
            } else {
                cancel(true);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryEntry> result) {
            m_progress.setVisibility(View.GONE);

            CircleIndicator indicator = (CircleIndicator) findViewById(R.id.article_images_indicator);

            if (indicator != null) {
                indicator.setViewPager(m_pager);
                indicator.setVisibility(View.VISIBLE);
            }

        }
    } */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ActivityCompat.postponeEnterTransition(this);

        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setTheme(R.style.DarkTheme);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.article_images_pager);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        m_progress = (ProgressBar) findViewById(R.id.article_images_progress);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().hide();

        if (savedInstanceState == null) {
            m_title = getIntent().getStringExtra("title");
            //m_urls = getIntent().getStringArrayListExtra("urls");
            m_content = getIntent().getStringExtra("content");

            String imgSrcFirst = getIntent().getStringExtra("firstSrc");

            m_items = new ArrayList<GalleryEntry>();

            Document doc = Jsoup.parse(m_content);
            Elements elems = doc.select("img,video");

            boolean firstFound = false;

            for (Element elem : elems) {

                GalleryEntry item = new GalleryEntry();

                if ("video".equals(elem.tagName().toLowerCase())) {
                    String cover = elem.attr("poster");

                    Element source = elem.select("source").first();
                    String src = source.attr("src");

                    if (src.startsWith("//")) {
                        src = "https:" + src;
                    }

                    if (imgSrcFirst.equals(src))
                        firstFound = true;

                    item.url = src;
                    item.coverUrl = cover;
                    item.type = GalleryEntryType.TYPE_VIDEO;

                } else {
                    String src = elem.attr("src");

                    if (src.startsWith("//")) {
                        src = "https:" + src;
                    }

                    if (imgSrcFirst.equals(src))
                        firstFound = true;

                    item.url = src;
                    item.type = GalleryEntryType.TYPE_IMAGE;
                }

                if (firstFound && item.url != null) {
                    m_items.add(item);
                }
            }

        } else {
            m_items = (ArrayList<GalleryEntry>) savedInstanceState.getSerializable("items");
            m_title = savedInstanceState.getString("title");
            m_content = savedInstanceState.getString("content");
        }

        /*if (m_items.size() > 1) {
            m_progress.setProgress(0);
            m_progress.setMax(m_items.size());
            m_checkedItems = new ArrayList<>();

            ArrayList<GalleryEntry> tmp = new ArrayList<>(m_items);

            m_checkedItems.add(tmp.get(0));
            tmp.remove(0);

            ImageCheckTask ict = new ImageCheckTask();
            ict.execute(tmp);
        } else {
            m_checkedItems = new ArrayList<>(m_items);
            m_progress.setVisibility(View.GONE);
        } */

        setTitle(m_title);

        m_adapter = new ArticleImagesPagerAdapter(getSupportFragmentManager(), m_items);

        m_pager = (ViewPager) findViewById(R.id.article_images_pager);
        m_pager.setAdapter(m_adapter);
        m_pager.setPageTransformer(true, new DepthPageTransformer());

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        getMenuInflater().inflate(R.menu.context_article_content_img, menu);

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putSerializable("items", m_items);
        out.putString("title", m_title);
        out.putString("content", m_content);
    }


}
