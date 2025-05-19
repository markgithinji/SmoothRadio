package com.smoothradio.radio.core.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smoothradio.radio.R
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRadioLinkRepository @Inject constructor(
    private val context: Context,
    private val firebaseFirestore: FirebaseFirestore,
) : RadioLinkRepository {

    private var listenerRegistration: ListenerRegistration? = null

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = callbackFlow {
        trySend(Resource.Success(getLinksFromHelper()))

        listenerRegistration = firebaseFirestore.collection("links")
            .orderBy("index")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    trySend(
                        Resource.Error(
                            context.getString(
                                R.string.error_loading_links,
                                error.message ?: "Unknown error"
                            )
                        )
                    )
                    return@addSnapshotListener
                }

                if (value == null || value.isEmpty) {
                    trySend(Resource.Error(context.getString(R.string.error_no_links_found)))
                    return@addSnapshotListener
                }

                val newLinks = value.documents.mapNotNull { it.getString("link") }
                if (newLinks.isNotEmpty()) {
                    trySend(Resource.Success(newLinks))
                }
            }

        awaitClose {
            listenerRegistration?.remove()
        }
    }


    private fun getLinksFromHelper(): List<String> {
        return RadioStationLinksHelper.RADIO_STATIONS.toList()
    }

    override fun clear() {
        listenerRegistration?.remove()
    }
}
