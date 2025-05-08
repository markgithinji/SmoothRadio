package com.smoothradio.radio.core.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.smoothradio.radio.core.util.RadioStationLinksHelper;
import com.smoothradio.radio.core.util.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

public class RadioLinkRepository {
    private final FirebaseFirestore firebaseFirestore;
    private ListenerRegistration listenerRegistration;

    @Inject
    public RadioLinkRepository(FirebaseFirestore firebaseFirestore) {
        this.firebaseFirestore = firebaseFirestore;
    }

    public LiveData<Resource<List<String>>> getRemoteStreamLinks() {
        MutableLiveData<Resource<List<String>>> remoteRadioLinksLiveData = new MutableLiveData<>();
        remoteRadioLinksLiveData.postValue(Resource.loading());

        listenerRegistration = firebaseFirestore.collection("links")
                .orderBy("index")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) {
                        List<String> fallbackLinks = getLinksFromHelper();
                        remoteRadioLinksLiveData.setValue(Resource.success(fallbackLinks));
                        return;
                    }

                    List<String> newLinks = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String link = doc.getString("link");
                        if (link != null && !link.isEmpty()) {
                            newLinks.add(link);
                        }
                    }

                    if (newLinks.isEmpty()) {
                        newLinks = getLinksFromHelper();
                    }

                    remoteRadioLinksLiveData.setValue(Resource.success(newLinks));
                });

        return remoteRadioLinksLiveData;
    }

    private List<String> getLinksFromHelper() {
        List<String> fallbackLinks = new ArrayList<>();
        Collections.addAll(fallbackLinks, RadioStationLinksHelper.RADIO_STATIONS);
        return fallbackLinks;
    }

    public void removeListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

}
