package org.fox.ttrss;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.livefront.bridge.Bridge;
import com.livefront.bridge.SavedStateHandler;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import icepick.Icepick;

@ReportsCrashes(mode = ReportingInteractionMode.SILENT,
        excludeMatchingSharedPreferencesKeys = {"password"},
        resDialogText = R.string.crash_dialog_text,
        formUri = "https://tt-rss.org/acra/submit/")
public class Application extends android.app.Application {
	private static Application m_singleton;
	
	public ArticleList tmpArticleList;
	public Article tmpArticle;

	public int m_selectedArticleId;
	public String m_sessionId;
	public int m_apiLevel;
	public static Application getInstance(){
		return m_singleton;
	}
	
	@Override
	public final void onCreate() {
		super.onCreate();

        if (!BuildConfig.DEBUG) {
            ACRA.init(this);
        }

		Bridge.initialize(getApplicationContext(), new SavedStateHandler() {
			@Override
			public void saveInstanceState(@NonNull Object target, @NonNull Bundle state) {
				Icepick.saveInstanceState(target, state);
			}

			@Override
			public void restoreInstanceState(@NonNull Object target, @Nullable Bundle state) {
				Icepick.restoreInstanceState(target, state);
			}
		});

		m_singleton = this;
	}
	
	public void save(Bundle out) {
		
		out.setClassLoader(getClass().getClassLoader());
		out.putString("gs:sessionId", m_sessionId);
		out.putInt("gs:apiLevel", m_apiLevel);
		out.putInt("gs:selectedArticleId", m_selectedArticleId);
	}
	
	public void load(Bundle in) {
		if (in != null) {
			m_sessionId = in.getString("gs:sessionId");
			m_apiLevel = in.getInt("gs:apiLevel");
			m_selectedArticleId = in.getInt("gs:selectedArticleId");
		}
				
	}
}
