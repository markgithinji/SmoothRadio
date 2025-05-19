package com.smoothradio.radio.feature.radio_list.util

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter

class SortDialog : DialogFragment() {

    private var mainActivity: MainActivity? = null


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mainActivity = activity as? MainActivity

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort By:")
            .setItems(R.array.sortOptions) { _, i ->
                val adapter = mainActivity?.radioListRecyclerViewAdapter ?: return@setItems

                when (i) {
                    0 -> adapter.sortPopular()
                    1 -> adapter.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.ASCENDING)
                    2 -> adapter.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.DESCENDING)
                    3 -> adapter.sortFavourites()
                }
            }
            .create()
    }

}
