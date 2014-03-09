//
//  Copyright (c) 2014 Lior Hass
//
package com.liorhass;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

public class ListPreferenceWithEditText extends ListPreference {
    private Context mContext;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefEditor;
    private String mPrefKey;
    private String mPrefCustomField;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private Handler mHandler = new Handler();
    private String mCustomValue = null;
   
    public ListPreferenceWithEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext    = context;
        mPrefs      = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefEditor = mPrefs.edit();
        mPrefKey    = getKey(); // Get the content of "android:key" field in our record at preferences.xml
        mPrefCustomField = mPrefKey + "_custom"; // Store the last value of the custom field
    }

    public ListPreferenceWithEditText(Context context) {
        this(context, null);
    }
    
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        mEntries     = getEntries();
        mEntryValues = getEntryValues();
        mCustomValue = null; // Forget previous custom value (possible if the user set a custom value and exited with the "Cancel" button)

        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

        builder.setAdapter(new ListPreferenceWithEditTextAdapter(), null);
    
        // Add an "OK" button.
        // Unlike a typical ListPreferences, we need an OK button because we have
        // a free-text field. To avoid confusion, we disable this button until
        // it makes sense for the user to use it.
        builder.setPositiveButton(mContext.getString(R.string.ok),
            new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mPrefEditor.putString(mPrefKey, mCustomValue);
                    mPrefEditor.putString(mPrefCustomField, mCustomValue);
                    mPrefEditor.commit();
                }
            });
    }

    private class ListPreferenceWithEditTextAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private boolean mCustomFiledGotFocus = false;
        private boolean mShouldIgnoreTextChangeCallbacks = false;
        private boolean mKeyboardAndFocusAlreadyEnabled = false;
        private Button mOkButton = null;

        public ListPreferenceWithEditTextAdapter() {
            super();
            mInflater = LayoutInflater.from(mContext);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (position < mEntries.length-1) {
                // If it's not the last row in the dialog it's a "normal" row
                return getNormalRow(position, convertView, parent);
            }
            // Otherwise, if it is the last row in the dialog, it's the custom row
            return getCustomRow(position, convertView, parent);
        }

        /**
         * Return a View containing one "normal" row of the ListPreference (i.e. a row
         * with some text and a radio button)
         */
        private View getNormalRow(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_preference_row_normal, parent, false);
            }

            TextView textView;
            RadioButton radioButton;
            RegularRowHolder holder = (RegularRowHolder)convertView.getTag();
            if (holder == null) {
                textView    = (TextView)convertView.findViewById(R.id.custom_list_view_row_text_view);
                radioButton = (RadioButton)convertView.findViewById(R.id.custom_list_view_row_radio_button);
                convertView.setTag(new RegularRowHolder(textView, radioButton));
            }
            else {
                textView    = holder.getTextView();
                radioButton = holder.getRadioButton();
            }
            
            radioButton.setClickable(false); // This makes click events on the radio-button propagate to the row, and be handled by its click-listener below.
            boolean thisIsTheCurrentlySetRow =
                    mPrefs.getString(mPrefKey, "xx").equals(mEntryValues[position])  &&
                    ! mCustomFiledGotFocus;    // If the custom field got the focus, we're not the active row even if our value matches the current preference value.
            radioButton.setChecked(thisIsTheCurrentlySetRow);
            
            textView.setText(mEntries[position]);
            
            convertView.setClickable(true);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPrefEditor.putString(mPrefKey, mEntryValues[position].toString());
                    mPrefEditor.commit();
                    getDialog().dismiss();
                }
            });
            return convertView;
        }
        
        /**
         * Return a View containing the "custom" row of the ListPreference (i.e. a row
         * with an EditText field and a radio button)
         */
        private View getCustomRow(final int position, View convertView, ViewGroup parent) {
            // When we create and manipulate the EditText field in this method,
            // Android calls the field's TextWatcher's methods (e.g. afterTextChanged()).
            // We don't want these calls, so we protect ourselves with this
            // flag (which we set here and clear at this method's end).
            mShouldIgnoreTextChangeCallbacks = true;

            // Without this, Android won't show the soft-KB when the EditText field gets focus.
            enableSoftKeyboardAndFocus(parent);
    
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_preference_row_with_edit_text, parent, false);
    
                // Disable the "OK" button (it'll be enabled when the user input some text).
                // Save the button in a member variable so we can later change it's state.
                // This code location is somewhat awkward, but we needed a place where we
                // have access to the dialog.
                AlertDialog ad = (AlertDialog)getDialog();
                if (mOkButton == null  &&  ad != null) {
                    mOkButton = ad.getButton(DialogInterface.BUTTON_POSITIVE);
                    mOkButton.setEnabled(false);
                }
            }
    
            EditText editText;
            RadioButton radioButton;
            SpecialRowHolder holder = (SpecialRowHolder)convertView.getTag();
            if (holder == null) {
                editText = (EditText)convertView.findViewById(R.id.custom_list_view_row_edit_text);
                radioButton = (RadioButton)convertView.findViewById(R.id.custom_list_view_row_radio_button);
                convertView.setTag(new SpecialRowHolder(editText, radioButton));

                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(final View v, boolean hasFocus) {
                        if (hasFocus) {
                            if (!mCustomFiledGotFocus) {
                                // This tells the containing ListPreference to re-draw the dialog
                                // so the previously selected button gets un-selected
                                notifyDataSetChanged();

                                if (mCustomValue == null) {
                                    mCustomValue = ((EditText)v).getText().toString();
                                }
    
                                // Show the soft-keyboard
                                setImeVisibility(true, v);
            
                                mOkButton.setEnabled(true);
                            }
                            mCustomFiledGotFocus = true;
                        }
                        else {
                            // In some versions of Android, for some (unknown to me) reason,
                            // the EditText looses focus immediately after gaining it (the
                            // focus goes to the surrounding ListView). Here we grab the
                            // focus back to the EditText view.
                            if (mCustomFiledGotFocus) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        v.requestFocus();
                                    }
                                });
                            }
                        }
                    }
                });

                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!mShouldIgnoreTextChangeCallbacks) {
                            mCustomValue = s.toString();
                        }
                    }
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after){}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count){}
                });

                convertView.setClickable(true);
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { // on clicking the custom-field's radio button
                        SpecialRowHolder rowHolder = (SpecialRowHolder)v.getTag();
                        rowHolder.getEditText().requestFocus();
                    }
                });
            }
            else {
                // convertView != null. Reuse the EditText and RadioButton.
                editText = holder.getEditText();
                radioButton = holder.getRadioButton();
            }
    
            // Make click events on the radio-button propagate to the row, and
            // be handled by its click-listener.
            radioButton.setClickable(false);

            // Determine if this is the currently active row, and if so, turn on
            // the radio button. This is the currently active row if the
            // preference value doesn't match any of the "normal" rows.
            String valueFromPrefs = mPrefs.getString(mPrefKey, "xx");
            boolean currentSelectionIsOneOfTheNormalLines = false;
            for (int i=0 ; i<mEntryValues.length-1 ; i++) {  // Enumerate over the "normal" rows
                if (mEntryValues[i].toString().equals(valueFromPrefs)) {
                    currentSelectionIsOneOfTheNormalLines = true;
                    break;
                }
            }
            if (mCustomFiledGotFocus) {
                currentSelectionIsOneOfTheNormalLines = false;
            }
            radioButton.setChecked(!currentSelectionIsOneOfTheNormalLines);

            if (mCustomValue == null) {
                mCustomValue = mPrefs.getString(mPrefCustomField, mEntryValues[mEntryValues.length-1].toString());
            }
            editText.setText("");
            editText.append(mCustomValue); // Insert the text and place the cursor at its end.
            
            mShouldIgnoreTextChangeCallbacks = false;
            return convertView;
        }
        
        /**
         * Traverse the view's view-hierarchy upwards.
         * @return v itself if its a ListView, or v's closest ancestor which is
         * a ListView. null if no ListView ancestor found.
         */ 
        private ListView getListViewAncestor(View v) {
            while (v != null) {
                if (v instanceof android.widget.ListView) {
                    break;
                }
                ViewParent vp = v.getParent();
                if (! (vp instanceof View)) {
                    v = null;
                    break;
                }
                v = (View)vp;
            }
            return (ListView)v;
        }

        private class RegularRowHolder {
            private TextView mTextView;
            private RadioButton mRadioButton;
            public RegularRowHolder(TextView tv, RadioButton rb) {
                mTextView = tv;
                mRadioButton = rb;
            }
            public TextView getTextView() { return mTextView; }
            public RadioButton getRadioButton() { return mRadioButton; }
        }

        private class SpecialRowHolder {
            private EditText mEditText;
            private RadioButton mRadioButton;
            public SpecialRowHolder(EditText et, RadioButton rb) {
                mEditText = et;
                mRadioButton = rb;
            }
            public EditText getEditText() { return mEditText; }
            public RadioButton getRadioButton() { return mRadioButton; }
        }

        @Override
        public int getCount() {
            return mEntries.length;
        }

        @Override public Object getItem(int position) { return null; } // Shouldn't be called
        @Override public long getItemId(int position) { return position; }
        
        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return 2; // We return 2 types of views: a "normal" row and a "custom-value" row.
        }
        
        @Override
        public int getItemViewType(int position) {
            // All the rows except for the last one are "normal" (type 0).
            // The last row is a "custom" row (type 1).
            return (position < mEntries.length-1 ? 0 : 1);
        }


        // Normally, Android doesn't let an EditText that resides inside
        // a Dialog (such as ours) gain focus and show the soft KB.
        // This method fixes this.
        private void enableSoftKeyboardAndFocus(View view) {
            if (!mKeyboardAndFocusAlreadyEnabled) {
                Dialog dialog = getDialog();
                if (dialog != null) {
                    Window theWindow = dialog.getWindow();
                    WindowManager.LayoutParams lp = theWindow.getAttributes();
                    if ((lp.softInputMode & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) == 0) {
                        lp.gravity       = Gravity.TOP | Gravity.FILL_HORIZONTAL;
                        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                        theWindow.setAttributes(lp);
                    }
    
                    // http://stackoverflow.com/questions/9102074/android-edittext-in-dialog-doesnt-pull-up-soft-keyboard/9118027#9118027
                    // Ask Android to show the soft keyboard when the EditText field gets the focus.
                    theWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                         WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    theWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                    // Find our ListView and tell it to allow its children to get focus.
                    // Otherwise it doesn't let our EditText field get focus.
                    ListView parent = getListViewAncestor(view);
                    parent.setItemsCanFocus(true);
                    parent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

                    mKeyboardAndFocusAlreadyEnabled = true;
                }
            }
        }

        // http://stackoverflow.com/a/13306632/1071117
        private ShowImeRunnable mShowImeRunnable = new ShowImeRunnable();
        /** Show/Hide the soft keyboard */
        private void setImeVisibility(boolean kbVisible, View view) {
            mHandler.removeCallbacks(mShowImeRunnable);
            if (kbVisible) {
                mShowImeRunnable.setView(view);
                // Hack alert: On some versions of Android, for some (unknown
                // to me) reason if the post is not delayed, the soft keyboard
                // is not displayed.
                mHandler.postDelayed(mShowImeRunnable, 200);
            }
            else {
                // Hide the soft keyboard
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        }
        private class ShowImeRunnable implements Runnable {
            private View mView;
            public void setView(View view) {
                mView = view;
            }
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mView, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }
    }
}