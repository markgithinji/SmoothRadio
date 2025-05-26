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
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class DefaultRadioLinkRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var context: Context
    private lateinit var repository: RadioLinkRepository
    private lateinit var listenerRegistration: ListenerRegistration

    @Before
    fun setup() {
        firestore = mock()
        context = mock()
        listenerRegistration = mock()
        repository = DefaultRadioLinkRepository(context, firestore)
    }

    @Test
    fun getRemoteStreamLinksFlow_shouldEmitFallbackLinksImmediately() = runTest {
        simulateFirestoreSuccess()

        val result = repository.getRemoteStreamLinksFlow().first()

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val data = (result as Resource.Success).data
        assertThat(data).containsExactlyElementsIn(RadioStationLinksHelper.RADIO_STATIONS)
    }


    @Test
    fun getRemoteStreamLinksFlow_shouldEmitNewLinksFromFirestore() = runTest {

        simulateFirestoreSuccess()

        val emissions = mutableListOf<Resource<List<String>>>()

        val job = launch {
            repository.getRemoteStreamLinksFlow().take(2).collect { emissions.add(it) }
        }

        advanceUntilIdle()

        assertThat(emissions).hasSize(2)
        assertThat(emissions[1]).isInstanceOf(Resource.Success::class.java)
        val data = (emissions[1] as Resource.Success).data
        assertThat(data).containsExactly("remote-link-1")

        job.cancel()
    }


    @Test
    fun getRemoteStreamLinksFlow_shouldNotEmit_onFirestoreError() = runTest {
        simulateFirestoreFailure()

        val emissions = mutableListOf<Resource<List<String>>>()

        val job = launch {
            repository.getRemoteStreamLinksFlow().take(1).collect { emissions.add(it) }
        }

        advanceUntilIdle()

        assertThat(emissions).hasSize(1)
        assertThat(emissions[0]).isInstanceOf(Resource.Success::class.java)
        val data = (emissions[0] as Resource.Success).data
        assertThat(data).containsExactlyElementsIn(RadioStationLinksHelper.RADIO_STATIONS)

        job.cancel()
    }


    @Test
    fun clear_shouldRemoveListener() = runTest {
        simulateFirestoreSuccess()

        val job = launch {
            repository.getRemoteStreamLinksFlow().collect {}
        }

        advanceUntilIdle()

        repository.clear()

        verify(listenerRegistration).remove()

        job.cancel()
    }

    private fun simulateFirestoreSuccess() {
        val collectionReference: CollectionReference = mock()
        val query: Query = mock()
        val snapshot: QuerySnapshot = mock()
        val document: DocumentSnapshot = mock()

        whenever(snapshot.isEmpty).thenReturn(false)
        whenever(snapshot.documents).thenReturn(listOf(document))
        whenever(document.getString("link")).thenReturn("remote-link-1")

        whenever(firestore.collection("links")).thenReturn(collectionReference)
        whenever(collectionReference.orderBy("index")).thenReturn(query)

        whenever(query.addSnapshotListener(any())).thenAnswer { invocation ->
            val listener: EventListener<QuerySnapshot> = invocation.getArgument(0)
            listener.onEvent(snapshot, null)
            listenerRegistration
        }
    }

    private fun simulateFirestoreFailure() {
        val collectionReference: CollectionReference = mock()
        val query: Query = mock()
        val exception: FirebaseFirestoreException = mock()

        whenever(exception.message).thenReturn("Simulated Firestore failure")

        whenever(firestore.collection("links")).thenReturn(collectionReference)
        whenever(collectionReference.orderBy("index")).thenReturn(query)

        whenever(query.addSnapshotListener(any())).thenAnswer { invocation ->
            val listener: EventListener<QuerySnapshot> = invocation.getArgument(0)
            listener.onEvent(null, exception)
            listenerRegistration
        }
    }
}
