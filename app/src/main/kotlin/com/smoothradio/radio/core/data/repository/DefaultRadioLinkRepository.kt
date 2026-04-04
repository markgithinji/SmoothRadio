package com.smoothradio.radio.core.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
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
    private val firebaseFirestore: FirebaseFirestore
) : RadioLinkRepository {

    private var linksListenerRegistration: ListenerRegistration? = null
    private var adConfigListenerRegistration: ListenerRegistration? = null

    /**
     * Retrieves a flow of remote stream links from Firestore.
     *
     * This function establishes a real-time listener to the "links" collection in Firestore.
     * It initially attempts to send the locally cached links (if any) via `getLinksFromHelper()`.
     *
     * The Firestore listener observes changes to the "links" collection, ordered by the "index" field.
     * - On successful data retrieval, it maps the documents to a list of link strings (extracting the "link" field).
     *   If new links are found, it emits a `Resource.Success` with the list of links.
     * - If an error occurs during Firestore communication, it emits a `Resource.Error` with a user-friendly error message.
     * - If the Firestore query returns no documents or an empty result, it emits a `Resource.Error` indicating no links were found.
     *
     * The flow will continue to emit new lists of links whenever the "links" collection in Firestore is updated.
     *
     * The listener is automatically removed when the collecting coroutine is cancelled (due to `awaitClose`).
     *
     * @return A [Flow] that emits [Resource] objects.
     *         - [Resource.Success] contains a `List<String>` of stream links.
     *         - [Resource.Error] contains an error message string.
     */
    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = callbackFlow {
        trySend(Resource.Success(getLinksFromHelper()))

        linksListenerRegistration = firebaseFirestore.collection("links")
            .orderBy("index")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    trySend(
                        Resource.Error(
                            context.getString(
                                R.string.error_loading_links,
                                error.message ?: context.getString(R.string.error_unexpected)
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
            linksListenerRegistration?.remove()
        }
    }

    /**
     * Retrieves a flow of remote ad configuration from Firestore.
     *
     * This function establishes a real-time listener to the "config/ad_settings" document in Firestore.
     *
     * The Firestore listener observes changes to the ad configuration document.
     * - On successful data retrieval, it maps the document to an AdConfig object.
     * - If the document doesn't exist, it emits a default AdConfig.
     * - If an error occurs during Firestore communication, it emits a Resource.Error.
     *
     * The flow will continue to emit new configurations whenever the document is updated.
     *
     * The listener is automatically removed when the collecting coroutine is cancelled.
     *
     * @return A [Flow] that emits [Resource] objects.
     *         - [Resource.Success] contains an [AdConfig] object.
     *         - [Resource.Error] contains an error message string.
     */
    override fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>> = callbackFlow {
        adConfigListenerRegistration = firebaseFirestore.collection("config")
            .document("ad_settings")
            .addSnapshotListener { document, error ->
                if (error != null) {
                    trySend(
                        Resource.Error(
                            error.message ?: context.getString(R.string.error_unexpected)
                        )
                    )
                    return@addSnapshotListener
                }

                if (document == null || !document.exists()) {
                    // Return default config if none exists in Firestore
                    trySend(
                        Resource.Success(
                            RemoteAdSettings(
                                adShowIntervalMinutes = AD_SHOW_INTERVAL_MINUTES,
                                maxAdsPerHour = MAX_ADS_PER_HOUR
                            )
                        )
                    )
                    return@addSnapshotListener
                }

                val adConfig = RemoteAdSettings(
                    adShowIntervalMinutes = document.getLong("adShowIntervalMinutes")?.toInt()
                        ?: AD_SHOW_INTERVAL_MINUTES,
                    maxAdsPerHour = document.getLong("maxAdsPerHour")?.toInt() ?: MAX_ADS_PER_HOUR
                )

                trySend(Resource.Success(adConfig))
            }

        awaitClose {
            adConfigListenerRegistration?.remove()
        }
    }

    private fun getLinksFromHelper(): List<String> {
        return RadioStationLinksHelper.RADIO_STATIONS.toList()
    }

    override fun clear() {
        linksListenerRegistration?.remove()
        adConfigListenerRegistration?.remove()
    }

    companion object {
        private const val AD_SHOW_INTERVAL_MINUTES = 4
        private const val MAX_ADS_PER_HOUR = 4
    }
}