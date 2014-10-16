package android.support.v4.app;

// http://code.google.com/p/android/issues/detail?id=37484
// Thanks for your amazing code quality, Google.	

import android.os.Bundle;
import android.view.ViewGroup;

public class ClassloaderWorkaroundFragmentStatePagerAdapter extends
		FragmentStatePagerAdapter {

	public ClassloaderWorkaroundFragmentStatePagerAdapter(FragmentManager fm) {
		super(fm);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Fragment getItem(int arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		Fragment f = (Fragment) super.instantiateItem(container, position);
		Bundle savedFragmentState = f.mSavedFragmentState;
		if (savedFragmentState != null) {
			savedFragmentState.setClassLoader(f.getClass().getClassLoader());
		}
		return f;
	}
	
	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return 0;
	}

}
