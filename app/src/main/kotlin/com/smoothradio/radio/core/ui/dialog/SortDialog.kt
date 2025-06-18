package com.smoothradio.radio.core.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smoothradio.radio.R

class SortDialog(private var sortOptionListener: SortOptionListener) : DialogFragment() {

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
            INDEX_POPULAR -> SortOption.POPULAR
            INDEX_ASCENDING -> SortOption.ASCENDING
            INDEX_DESCENDING -> SortOption.DESCENDING
            INDEX_FAVORITES -> SortOption.FAVORITES
            else -> null
        }
        option?.let { sortOptionListener.onSortOptionSelected(it) }
    }

    companion object {
        private const val INDEX_POPULAR = 0
        private const val INDEX_ASCENDING = 1
        private const val INDEX_DESCENDING = 2
        private const val INDEX_FAVORITES = 3
    }
}
