package com.smoothradio.radio.core.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smoothradio.radio.R

class SortDialog : DialogFragment() {

    private var sortOptionListener: SortOptionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sortOptionListener = (context as? SortOptionListener)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort By:")
            .setItems(R.array.sortOptions) { _, index ->
                handleSelection(index)
            }
            .create()
    }

    private fun handleSelection(index: Int) {
        val option = when (index) {
            0 -> SortOption.POPULAR
            1 -> SortOption.ASCENDING
            2 -> SortOption.DESCENDING
            3 -> SortOption.FAVORITES
            else -> null
        }
        option?.let { sortOptionListener?.onSortOptionSelected(it) }
    }

    override fun onDetach() {
        sortOptionListener = null
        super.onDetach()
    }
}



