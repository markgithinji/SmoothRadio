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
    private val _remoteStreamLinks = MutableStateFlow<Resource<List<String>>>(Resource.loading())
    val remoteStreamLinks: StateFlow<Resource<List<String>>> = _remoteStreamLinks

    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        fetchRemoteStreamLinks()
    }

    private fun fetchRemoteStreamLinks() {
        scope.launch {
            getRemoteStreamLinksFlow().collect { resource ->
                _remoteStreamLinks.value = resource
            }
        }
    }

    private fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = callbackFlow {
        trySend(Resource.loading())

        listenerRegistration = firebaseFirestore.collection("links")
            .orderBy("index")
            .addSnapshotListener { value, error ->
                if (error != null || value == null || value.isEmpty) {
                    trySend(Resource.success(getLinksFromHelper()))
                    return@addSnapshotListener
                }

                val newLinks = mutableListOf<String>()
                for (doc: DocumentSnapshot in value.documents) {
                    val link = doc.getString("link")
                    if (!link.isNullOrEmpty()) {
                        newLinks.add(link)
                    }
                }

                trySend(
                    if (newLinks.isEmpty()) {
                        Resource.success(getLinksFromHelper())
                    } else {
                        Resource.success(newLinks)
                    }
                )
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