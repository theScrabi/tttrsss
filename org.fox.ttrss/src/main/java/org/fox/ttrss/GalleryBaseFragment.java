package org.fox.ttrss;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.MenuItem;

public class GalleryBaseFragment extends Fragment {
    private final String TAG = this.getClass().getSimpleName();
    protected ArticleImagesPagerActivity m_activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    public boolean onImageMenuItemSelected(MenuItem item, String url) {
        switch (item.getItemId()) {
            case R.id.article_img_open:
                if (url != null) {
                    try {
                        m_activity.openUri(Uri.parse(url));
                    } catch (Exception e) {
                        e.printStackTrace();
                        m_activity.toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_img_copy:
                if (url != null) {
                    m_activity.copyToClipboard(url);
                }
                return true;
            case R.id.article_img_share:
                if (url != null) {
                    m_activity.shareText(url);
                }
                return true;
            case R.id.article_img_view_caption:
                if (url != null) {
                    m_activity.displayImageCaption(url, m_activity.m_content);
                }
                return true;
            default:
                Log.d(TAG, "onImageMenuItemSelected, unhandled id=" + item.getItemId());
                return false;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        //m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        m_activity = (ArticleImagesPagerActivity) activity;

    }


}
