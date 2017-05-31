package org.fox.ttrss.util;

// http://www.lukehorvat.com/blog/android-seekbardialogpreference/

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import org.fox.ttrss.R;

/**
 * A {@link DialogPreference} that provides a user with the means to select an
 * integer from a {@link SeekBar}, and persist it.
 * 
 * @author lukehorvat
 * 
 */
public class FontSizeDialogPreference extends DialogPreference {
	private static final int DEFAULT_MIN_PROGRESS = 9;
	private static final int DEFAULT_MAX_PROGRESS = 24;
	private static final String DEFAULT_PROGRESS = "0";

	private int mMinProgress = DEFAULT_MIN_PROGRESS;
	private int mMaxProgress = DEFAULT_MAX_PROGRESS;
	private int mProgress;
	private CharSequence mProgressTextSuffix;
	private TextView mProgressText;
	private SeekBar mSeekBar;

	public FontSizeDialogPreference(Context context) {
		this(context, null);
	}

	public FontSizeDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		setProgressTextSuffix(" " + context.getString(R.string.font_size_dialog_suffix));
		
		// set layout
		setDialogLayoutResource(R.layout.settings_select_font_size);
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);
		setDialogIcon(null);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue) {
		setProgress(restore ? Integer.valueOf(getPersistedString(DEFAULT_PROGRESS))
				: Integer.valueOf((String)defaultValue));
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getString(index);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);

		mProgressText = (TextView) view.findViewById(R.id.text_progress);

		mSeekBar = (SeekBar) view.findViewById(R.id.seek_bar);
		mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				// update text that displays the current SeekBar progress value
				// note: this does not persist the progress value. that is only
				// ever done in setProgress()
				String progressStr = String.valueOf(progress + mMinProgress);
				mProgressText.setText(mProgressTextSuffix == null ? progressStr
						: progressStr.concat(mProgressTextSuffix.toString()));
				mProgressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, progress + mMinProgress);
			}
		});
		
		mSeekBar.setMax(mMaxProgress - mMinProgress);
		mSeekBar.setProgress(mProgress - mMinProgress);
	}

	public int getMinProgress() {
		return mMinProgress;
	}

	public void setMinProgress(int minProgress) {
		mMinProgress = minProgress;
		setProgress(Math.max(mProgress, mMinProgress));
	}

	public int getMaxProgress() {
		return mMaxProgress;
	}

	public void setMaxProgress(int maxProgress) {
		mMaxProgress = maxProgress;
		setProgress(Math.min(mProgress, mMaxProgress));
	}

	public int getProgress() {
		return mProgress;
	}

	public void setProgress(int progress) {
		progress = Math.max(Math.min(progress, mMaxProgress), mMinProgress);

		if (progress != mProgress) {
			mProgress = progress;
			persistString(String.valueOf(progress));
			notifyChanged();
		}
	}

	public CharSequence getProgressTextSuffix() {
		return mProgressTextSuffix;
	}

	public void setProgressTextSuffix(CharSequence progressTextSuffix) {
		mProgressTextSuffix = progressTextSuffix;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		// when the user selects "OK", persist the new value
		if (positiveResult) {
			int seekBarProgress = mSeekBar.getProgress() + mMinProgress;
			if (callChangeListener(seekBarProgress)) {
				setProgress(seekBarProgress);
			}
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		// save the instance state so that it will survive screen orientation
		// changes and other events that may temporarily destroy it
		final Parcelable superState = super.onSaveInstanceState();

		// set the state's value with the class member that holds current
		// setting value
		final SavedState myState = new SavedState(superState);
		myState.minProgress = getMinProgress();
		myState.maxProgress = getMaxProgress();
		myState.progress = getProgress();

		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		// check whether we saved the state in onSaveInstanceState()
		if (state == null || !state.getClass().equals(SavedState.class)) {
			// didn't save the state, so call superclass
			super.onRestoreInstanceState(state);
			return;
		}

		// restore the state
		SavedState myState = (SavedState) state;
		setMinProgress(myState.minProgress);
		setMaxProgress(myState.maxProgress);
		setProgress(myState.progress);

		super.onRestoreInstanceState(myState.getSuperState());
	}

	private static class SavedState extends BaseSavedState {
		int minProgress;
		int maxProgress;
		int progress;

		public SavedState(Parcelable superState) {
			super(superState);
		}

		public SavedState(Parcel source) {
			super(source);

			minProgress = source.readInt();
			maxProgress = source.readInt();
			progress = source.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);

			dest.writeInt(minProgress);
			dest.writeInt(maxProgress);
			dest.writeInt(progress);
		}

		@SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}