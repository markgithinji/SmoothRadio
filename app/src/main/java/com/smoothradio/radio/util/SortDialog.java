package com.smoothradio.radio.util;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.feature.radio_list.RadioListRecyclerViewAdapter;

public class SortDialog extends DialogFragment {
    MainActivity mainActivity;
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mainActivity=(MainActivity) getActivity();
        MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(mainActivity);
        builder.setTitle("Sort By:").setItems(R.array.sortOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(i==0)
                {
                    mainActivity.getAdapter().sortPopular();//popular
                }
                if(i==1) {
                    mainActivity.getAdapter().sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.ASCENDING);
                }
                if(i==2) {
                    mainActivity.getAdapter().sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.DESCENDING);
                }
                if(i==3) {
                    mainActivity.getAdapter().sortFavourites();//favourites
                }

                }
        });
        return builder.create();
    }
}