package org.fox.ttrss;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTheme(android.R.style.Theme_Holo_Light);
        
        setContentView(R.layout.main);
    }
}