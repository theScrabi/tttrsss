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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import me.relex.circleindicator.CircleIndicator;

public class ArticleImagesPagerActivity extends CommonActivity {
    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<String> m_urls;
    private ArrayList<String> m_checkedUrls;
    private String m_title;
    private ArticleImagesPagerAdapter m_adapter;
    public String m_content;
    private ProgressBar m_progress;
    private ViewPager m_pager;

    private class ArticleImagesPagerAdapter extends FragmentStatePagerAdapter {
        private List<String> m_urls;

        public ArticleImagesPagerAdapter(FragmentManager fm, List<String> urls) {
            super(fm);
            m_urls = urls;
        }

        @Override
        public int getCount() {
            return m_urls.size();
        }

        @Override
        public Fragment getItem(int position) {
            ArticleImageFragment frag = new ArticleImageFragment();

            Log.d(TAG, "getItem: " + position + " " + m_urls.get(position));

            frag.initialize(m_urls.get(position));

            return frag;
        }
    }

    private class ImageCheckTask extends AsyncTask<List<String>, String, Integer> {
        @Override
        protected Integer doInBackground(List<String>... urls) {
            int position = 0;

            for (String url : urls[0]) {
                if (!isCancelled()) {
                    position++;

                    try {
                        Bitmap bmp = Glide.with(ArticleImagesPagerActivity.this)
                                .load(url)
                                .asBitmap()
                                .into(-1, -1)
                                .get();

                        if (bmp != null && bmp.getWidth() > 128 && bmp.getHeight() > 128) {
                            publishProgress(url, String.valueOf(position));
                        } else {
                            publishProgress(null, String.valueOf(position));
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
        protected void onProgressUpdate(String... checkedUrl) {

            if (!isFinishing() && m_adapter != null) {
                if (checkedUrl[0] != null) {
                    m_checkedUrls.add(checkedUrl[0]);
                    m_adapter.notifyDataSetChanged();
                }

                Log.d(TAG, "progr=" + checkedUrl[1]);

                m_progress.setProgress(Integer.valueOf(checkedUrl[1]));
            } else {
                cancel(true);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            m_progress.setVisibility(View.GONE);

            CircleIndicator indicator = (CircleIndicator) findViewById(R.id.article_images_indicator);

            if (indicator != null) {
                indicator.setViewPager(m_pager);
                indicator.setVisibility(View.VISIBLE);
            }

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

            m_urls = new ArrayList<String>();

            Document doc = Jsoup.parse(m_content);
            Elements imgs = doc.select("img");

            boolean firstFound = false;

            for (Element img : imgs) {
                String imgSrc = img.attr("src");

                if (imgSrc.startsWith("//")) {
                    imgSrc = "https:" + imgSrc;
                }

                if (imgSrcFirst.equals(imgSrc))
                    firstFound = true;

                if (firstFound) {
                    m_urls.add(imgSrc);
                }
            }

        } else {
            m_urls = savedInstanceState.getStringArrayList("urls");
            m_title = savedInstanceState.getString("title");
            m_content = savedInstanceState.getString("content");
        }

        if (m_urls.size() > 1) {
            m_progress.setProgress(0);
            m_progress.setMax(m_urls.size());
            m_checkedUrls = new ArrayList<String>();

            ArrayList<String> tmp = new ArrayList<String>(m_urls);

            m_checkedUrls.add(tmp.get(0));
            tmp.remove(0);

            ImageCheckTask ict = new ImageCheckTask();
            ict.execute(tmp);
        } else {
            m_checkedUrls = new ArrayList<String>(m_urls);
            m_progress.setVisibility(View.GONE);
        }

        setTitle(m_title);

        m_adapter = new ArticleImagesPagerAdapter(getSupportFragmentManager(), m_urls);

        m_pager = (ViewPager) findViewById(R.id.article_images_pager);
        m_pager.setAdapter(m_adapter);
        m_pager.setPageTransformer(true, new DepthPageTransformer());

    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
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

        out.putStringArrayList("urls", m_urls);
        out.putString("title", m_title);
        out.putString("content", m_content);
    }


}
