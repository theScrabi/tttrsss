package org.fox.ttrss;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.viewpagerindicator.UnderlinePageIndicator;

import java.util.ArrayList;
import java.util.List;

public class ArticleImagesPagerActivity extends ActionBarActivity {
    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<String> m_urls;
    private String m_title;

    private class ArticleImagesPagerAdapter extends PagerAdapter implements View.OnClickListener {
        private List<String> m_urls;

        public ArticleImagesPagerAdapter(List<String> urls) {
            super();

            m_urls = urls;
        }

        public ArticleImagesPagerAdapter() {
            super();
        }

        @Override
        public int getCount() {
            return m_urls.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            String url = m_urls.get(position);

            Log.d(TAG, "called for URL: " + url);

            LayoutInflater inflater = (LayoutInflater) container.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = inflater.inflate(R.layout.article_images_image, null);

            ImageView imgView = (ImageView) view.findViewById(R.id.flavor_image);
            imgView.setOnClickListener(this);

            DisplayImageOptions options = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .resetViewBeforeLoading(true)
                    .cacheOnDisk(true)
                    .displayer(new FadeInBitmapDisplayer(200))
                    .build();

            ImageAware imageAware = new ImageViewAware(imgView, false);

            final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.flavor_image_progress);
            final View errorMessage = view.findViewById(R.id.flavor_image_error);

            ImageLoader.getInstance().displayImage(url, imageAware, options, new ImageLoadingListener() {
                @Override
                public void onLoadingStarted(String s, View view) {

                }

                @Override
                public void onLoadingFailed(String s, View view, FailReason failReason) {
                    progressBar.setVisibility(View.GONE);
                    errorMessage.setVisibility(View.VISIBLE);
                }

                @Override
                public void onLoadingComplete(String s, View view, Bitmap bitmap) {
                    if (bitmap != null) {
                        if (bitmap.getWidth() < 128 || bitmap.getHeight() < 128) {
                            view.setVisibility(View.INVISIBLE);
                            errorMessage.setVisibility(View.VISIBLE);
                        } else {
                            view.setTag(s);
                        }
                    }

                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingCancelled(String s, View view) {

                }
            });

            ((ViewPager) container).addView(view, 0);

            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((FrameLayout)object);
        }

        @Override
        public void onClick(View view) {
            String url = (String) view.getTag();

            if (url != null) {
                Log.d(TAG, "click to open:" + url);

                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.DarkTheme);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.article_images_pager);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            m_urls = getIntent().getStringArrayListExtra("urls");
            m_title = getIntent().getStringExtra("title");
        } else {
            m_urls = savedInstanceState.getStringArrayList("urls");
            m_title = savedInstanceState.getString("title");
        }

        setTitle(m_title);

        Log.d(TAG, "urls size: " + m_urls.size());

        ArticleImagesPagerAdapter adapter = new ArticleImagesPagerAdapter(m_urls);

        ViewPager pager = (ViewPager) findViewById(R.id.article_images_pager);

        pager.setAdapter(adapter);

        UnderlinePageIndicator indicator = (UnderlinePageIndicator)findViewById(R.id.article_images_indicator);
        indicator.setViewPager(pager);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putStringArrayList("urls", m_urls);
        out.putString("title", m_title);
    }

    /* @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.article_images_pager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    } */
}
