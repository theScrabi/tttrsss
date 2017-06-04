package org.fox.ttrss.types;

import java.io.Serializable;

public class GalleryEntry implements Serializable {
    public enum GalleryEntryType { TYPE_IMAGE, TYPE_VIDEO };

    public String url;
    public GalleryEntryType type;
    public String coverUrl;

    public boolean equals(GalleryEntry obj) {
        if (obj.url != null && url != null) {
            return obj.url.equals(url);
        } else {
            return super.equals(obj);
        }
    }
}
