package org.fox.ttrss;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;

public class GalleryImageFragment extends GalleryBaseFragment {
    private final String TAG = this.getClass().getSimpleName();

    String m_url;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery_entry, container, false);

        if (savedInstanceState != null) {
            m_url = savedInstanceState.getString("url");
        }

        Log.d(TAG, "called for URL: " + m_url);

        ImageView imgView = (ImageView) view.findViewById(R.id.flavor_image);

        // TODO: ImageMatrixTouchHandler doesn't like context menus
        ImageMatrixTouchHandler touchHandler = new ImageMatrixTouchHandler(view.getContext());

        imgView.setOnTouchListener(touchHandler);

        // shared element transitions stop GIFs from playing
        if (m_url.toLowerCase().indexOf(".gif") == -1) {
            ViewCompat.setTransitionName(imgView, "gallery:" + m_url);
        }

        registerForContextMenu(imgView);

        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.flavor_image_progress);
        final View errorMessage = view.findViewById(R.id.flavor_image_error);

        final GlideDrawableImageViewTarget glideImage = new GlideDrawableImageViewTarget(imgView);

        Glide.with(this)
                .load(m_url)
                //.dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .listener(new RequestListener<String, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        errorMessage.setVisibility(View.VISIBLE);

                        ActivityCompat.startPostponedEnterTransition(m_activity);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        errorMessage.setVisibility(View.GONE);

                        ActivityCompat.startPostponedEnterTransition(m_activity);
                        return false;
                    }
                })
                .into(glideImage);

        return view;
    }

    public void initialize(String url) {
        m_url = url;
    }

    /*@Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = m_pager.getCurrentItem();
        String url = m_checkedUrls.get(position);

        if (!onImageMenuItemSelected(item, url))
            return super.onContextItemSelected(item);
        else
            return true;
    }*/

    @Override
    public void onSaveInstanceState (Bundle out) {
        super.onSaveInstanceState(out);

        out.setClassLoader(getClass().getClassLoader());
        out.putString("url", m_url);
    }

}
