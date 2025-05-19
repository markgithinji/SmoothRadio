package com.smoothradio.radio.core.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class DefaultRadioLinkRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var context: Context
    private lateinit var repository: DefaultRadioLinkRepository

    @Before
    fun setup() {
        firestore = mock()
        context = mock()
        repository = DefaultRadioLinkRepository(context, firestore)
    }

    @Test
    fun getRemoteStreamLinksFlow_shouldEmitFallbackLinksImmediately() = runTest {
        mockFirestoreChain(firestore)

        val result = repository.getRemoteStreamLinksFlow().first()

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val data = (result as Resource.Success).data
        assertThat(data).containsExactlyElementsIn(RadioStationLinksHelper.RADIO_STATIONS)
        assertThat(data.size).isEqualTo(231) // 231 current links set only
    }


    @Test
    fun getRemoteStreamLinksFlow_shouldEmitNewLinksFromFirestore() = runTest {
        val listenerCaptor = argumentCaptor<EventListener<QuerySnapshot>>()
        mockFirestoreChain(firestore, listenerCaptor)

        val emissions = mutableListOf<Resource<List<String>>>()

        val job = launch {
            repository.getRemoteStreamLinksFlow().take(2).collect { emissions.add(it) }
        }

        advanceUntilIdle()

        val document = mock<DocumentSnapshot>()
        whenever(document.getString("link")).thenReturn("remote-link-1")

        val snapshot = mock<QuerySnapshot>()
        whenever(snapshot.isEmpty).thenReturn(false)
        whenever(snapshot.documents).thenReturn(listOf(document))

        listenerCaptor.firstValue.onEvent(snapshot, null)

        advanceUntilIdle()

        assertThat(emissions).hasSize(2)
        assertThat(emissions[1]).isInstanceOf(Resource.Success::class.java)
        val data = (emissions[1] as Resource.Success).data
        assertThat(data).containsExactly("remote-link-1")

        job.cancel()
    }


    @Test
    fun getRemoteStreamLinksFlow_shouldNotEmit_onFirestoreError() = runTest {
        val listenerCaptor = argumentCaptor<EventListener<QuerySnapshot>>()
        mockFirestoreChain(firestore, listenerCaptor)

        val emissions = mutableListOf<Resource<List<String>>>()

        val job = launch {
            repository.getRemoteStreamLinksFlow().take(1).collect { emissions.add(it) }
        }

        advanceUntilIdle()

        val exception = mock<FirebaseFirestoreException>()
        whenever(exception.message).thenReturn("Fake Firestore error")

        listenerCaptor.firstValue.onEvent(null, exception)

        assertThat(emissions).hasSize(1)
        assertThat(emissions[0]).isInstanceOf(Resource.Success::class.java)
        val data = (emissions[0] as Resource.Success).data
        assertThat(data).containsExactlyElementsIn(RadioStationLinksHelper.RADIO_STATIONS)

        job.cancel()
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clear_shouldRemoveListener() = runTest {
        val listenerRegistration = mock<ListenerRegistration>()
        val orderedQuery = mock<Query>()
        val collection = mock<CollectionReference>()

        whenever(firestore.collection("links")).thenReturn(collection)
        whenever(collection.orderBy("index")).thenReturn(orderedQuery)
        whenever(orderedQuery.addSnapshotListener(any())).thenReturn(listenerRegistration)

        val job = launch {
            repository.getRemoteStreamLinksFlow().collect {}
        }

        advanceUntilIdle()

        repository.clear()

        verify(listenerRegistration).remove()

        job.cancel()
    }

    // Reusable helper function for mocking Firestore chain
    private fun mockFirestoreChain(
        firestore: FirebaseFirestore,
        listenerCaptor: KArgumentCaptor<EventListener<QuerySnapshot>>? = null
    ) {
        val collectionReference = mock<CollectionReference>()
        val orderedQuery = mock<Query>()
        val listenerRegistration = mock<ListenerRegistration>()

        whenever(firestore.collection("links")).thenReturn(collectionReference)
        whenever(collectionReference.orderBy("index")).thenReturn(orderedQuery)

        if (listenerCaptor != null) {
            whenever(orderedQuery.addSnapshotListener(listenerCaptor.capture()))
                .thenReturn(listenerRegistration)
        } else {
            whenever(orderedQuery.addSnapshotListener(any())).thenReturn(listenerRegistration)
        }
    }
}
