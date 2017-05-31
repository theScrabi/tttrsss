package org.fox.ttrss;

import android.content.Context;

import java.util.HashMap;

class FeedsLoader extends ApiLoader {
    private int m_catId;

    public FeedsLoader(Context context, HashMap<String, String> params, int catId) {
        super(context, params);

        m_catId = catId;
    }

    public int getCatId() {
        return m_catId;
    }
}
