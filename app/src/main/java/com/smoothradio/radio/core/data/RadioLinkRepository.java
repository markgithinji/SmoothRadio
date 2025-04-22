package com.smoothradio.radio.core.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.smoothradio.radio.core.util.RadioStationData;
import com.smoothradio.radio.core.util.Resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RadioLinkRepository {
    private static final String FILE_NAME = "file.txt";
    private final Context context;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration listenerRegistration;

    public RadioLinkRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // Method to create an initial file with some default radio links
    public void createInitialTxt() {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String url : RadioStationData.RADIO_STATIONS) {
                    writer.write(url);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // Method to load links from the file
    public LiveData<Resource<List<String>>> getLocalLinks() {
        MutableLiveData<Resource<List<String>>> localRadioLinksLiveData = new MutableLiveData<>();
        List<String> links = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    links.add(line);
                }
                localRadioLinksLiveData.setValue(Resource.success(links));
            } catch (IOException e) {
                e.printStackTrace();
                localRadioLinksLiveData.setValue(Resource.error("Error reading file"));
            }
        } else {
            localRadioLinksLiveData.setValue(Resource.error("File does not exist"));
        }

        return localRadioLinksLiveData;
    }

    // Method to update the links file with a new set of links
    public void updateLinks(List<String> newLinks) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String link : newLinks) {
                writer.write(link != null ? link : "");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public LiveData<Resource<List<String>>> getRemoteStreamLinks() {
        MutableLiveData<Resource<List<String>>> remoteRadioLinksLiveData = new MutableLiveData<>();
        remoteRadioLinksLiveData.postValue(Resource.loading());

        listenerRegistration = db.collection("links")
                .orderBy("index")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) {
                        remoteRadioLinksLiveData.postValue(Resource.error("Error loading links"));
                        return;
                    }

                    List<String> links = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String link = doc.getString("link");
                        links.add(link != null ? link : "");
                    }
                    updateLinks(links);

                    remoteRadioLinksLiveData.postValue(Resource.success(links));
                });

        return remoteRadioLinksLiveData;
    }

    public void removeListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

}
