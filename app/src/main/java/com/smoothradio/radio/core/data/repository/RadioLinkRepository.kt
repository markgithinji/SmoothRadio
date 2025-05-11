package com.smoothradio.radio.core.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioLinkRepository @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore
) {
    private var listenerRegistration: ListenerRegistration? = null

    fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = callbackFlow {
        // Emit helper links immediately so UI is populated fast
        trySend(Resource.success(getLinksFromHelper()))

        // Now listen for remote changes
        listenerRegistration = firebaseFirestore.collection("links")
            .orderBy("index")
            .addSnapshotListener { value, error ->
                if (error != null || value == null || value.isEmpty) {
                    return@addSnapshotListener // do nothing if error; we already showed fallback
                }

                val newLinks = value.documents.mapNotNull { it.getString("link") }

                if (newLinks.isNotEmpty()) {
                    trySend(Resource.success(newLinks))
                }
            }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    private fun getLinksFromHelper(): List<String> {
        return RadioStationLinksHelper.RADIO_STATIONS.toList()
    }

    fun clear() {
        listenerRegistration?.remove()
    }
}