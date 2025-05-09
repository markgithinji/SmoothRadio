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
                when (i) {
                    0 -> mainActivity?.radioListRecyclerViewAdapter?.sortPopular()
                    1 -> mainActivity?.radioListRecyclerViewAdapter?.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.ASCENDING)
                    2 -> mainActivity?.radioListRecyclerViewAdapter?.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.DESCENDING)
                    3 -> mainActivity?.radioListRecyclerViewAdapter?.sortFavourites()
                }
            }
            .create()
    }
}
