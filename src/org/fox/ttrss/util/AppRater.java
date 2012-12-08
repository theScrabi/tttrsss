package org.fox.ttrss.util;

// From http://androidsnippets.com/prompt-engaged-users-to-rate-your-app-in-the-android-market-appirater

import java.util.List;

import org.fox.ttrss.R;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppRater {
    private final static String APP_TITLE = "Tiny Tiny RSS";
    private final static String APP_PNAME = "org.fox.ttrss";
    private final static String DONATE_APP_PNAME = "org.fox.ttrss.key";
    
    private final static int DAYS_UNTIL_PROMPT = 3;
    private final static int LAUNCHES_UNTIL_PROMPT = 7;

    private final static int DONATION_DAYS_UNTIL_PROMPT = DAYS_UNTIL_PROMPT * 3;
    private final static int DONATION_LAUNCHES_UNTIL_PROMPT = LAUNCHES_UNTIL_PROMPT * 3;

    public static boolean isDonationApkInstalled(Context mContext) {
		List<PackageInfo> pkgs = mContext.getPackageManager()
				.getInstalledPackages(0);

		for (PackageInfo p : pkgs) {
			if (DONATE_APP_PNAME.equals(p.packageName)) {
				return true;
			}
		}
		
		return false;
    }
    
    public static void appLaunched(Context mContext) {
        SharedPreferences prefs = mContext.getSharedPreferences("apprater", 0);

        if (prefs.getBoolean("dontshowagain", false) && 
        		prefs.getBoolean("donate_dontshowagain", false)) { return ; }
        
        SharedPreferences.Editor editor = prefs.edit();
        
        // Increment launch counter
        long launch_count = prefs.getLong("launch_count", 0) + 1;
        editor.putLong("launch_count", launch_count);

        // Get date of first launch
        Long date_firstLaunch = prefs.getLong("date_firstlaunch", 0);
        if (date_firstLaunch == 0) {
            date_firstLaunch = System.currentTimeMillis();
            editor.putLong("date_firstlaunch", date_firstLaunch);
        }
        
        boolean rateDialogShown = false;
        
        // Wait at least n days before opening
        if (launch_count >= LAUNCHES_UNTIL_PROMPT && 
        		!prefs.getBoolean("dontshowagain", false) && 
        		System.currentTimeMillis() >= date_firstLaunch + (DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000)) {
        	
        	rateDialogShown = true;
   			showRateDialog(mContext, editor);
        }

        if (launch_count >= DONATION_LAUNCHES_UNTIL_PROMPT &&
        		!prefs.getBoolean("donate_dontshowagain", false) &&
        		!rateDialogShown &&
        		!isDonationApkInstalled(mContext) && 
        		System.currentTimeMillis() >= date_firstLaunch + (DONATION_DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000)) {
        	
        	showDonationDialog(mContext, editor);
        }

        editor.commit();
    }   
    
    public static void showDonationDialog(final Context mContext, final SharedPreferences.Editor editor) {
        final Dialog dialog = new Dialog(mContext);
        dialog.setTitle("Donate to " + APP_TITLE);

        LinearLayout ll = new LinearLayout(mContext);
        ll.setOrientation(LinearLayout.VERTICAL);
        
        TextView tv = new TextView(mContext);
        tv.setText("If you enjoy using " + APP_TITLE + ", please consider donating. Your $1.99 could go a long way towards continued support of this project. Thanks!");
        tv.setWidth(240);
        tv.setPadding(4, 0, 4, 10);
        ll.addView(tv);
        
        Button b1 = new Button(mContext);
        b1.setText("Donate!");
        b1.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	try {
            		mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + DONATE_APP_PNAME)));
            	} catch (ActivityNotFoundException e) {
            		mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + DONATE_APP_PNAME)));
            	}
                dialog.dismiss();
            }
        });        
        ll.addView(b1);

        Button b2 = new Button(mContext);
        b2.setText("Remind me later");
        b2.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        ll.addView(b2);

        Button b3 = new Button(mContext);
        b3.setText("No, thanks");
        b3.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (editor != null) {
                    editor.putBoolean("donate_dontshowagain", true);
                    editor.commit();
                }
                dialog.dismiss();
            }
        });
        ll.addView(b3);

        dialog.setContentView(ll);        
        dialog.show();        
    }
    
    public static void showRateDialog(final Context mContext, final SharedPreferences.Editor editor) {
        final Dialog dialog = new Dialog(mContext);
        dialog.setTitle("Rate " + APP_TITLE);

        LinearLayout ll = new LinearLayout(mContext);
        ll.setOrientation(LinearLayout.VERTICAL);
        
        TextView tv = new TextView(mContext);
        tv.setText("If you enjoy using " + APP_TITLE + ", please take a moment to rate it. Thanks for your support!");
        tv.setWidth(240);
        tv.setPadding(4, 0, 4, 10);
        ll.addView(tv);
        
        Button b1 = new Button(mContext);
        b1.setText("Rate " + APP_TITLE);
        b1.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	try {
            		mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + APP_PNAME)));
            	} catch (ActivityNotFoundException e) {
            		mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + APP_PNAME)));
            	}
                dialog.dismiss();
            }
        });        
        ll.addView(b1);

        Button b2 = new Button(mContext);
        b2.setText("Remind me later");
        b2.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        ll.addView(b2);

        Button b3 = new Button(mContext);
        b3.setText("No, thanks");
        b3.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (editor != null) {
                    editor.putBoolean("dontshowagain", true);
                    editor.commit();
                }
                dialog.dismiss();
            }
        });
        ll.addView(b3);

        dialog.setContentView(ll);        
        dialog.show();        
    }
}