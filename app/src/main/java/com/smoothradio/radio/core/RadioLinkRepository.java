package com.smoothradio.radio.core;

import android.content.Context;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
    private static final int RADIO_LINK_COUNT = 231;
    private final Context context;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final MutableLiveData<Resource<List<String>>> streamLinksLiveData = new MutableLiveData<>();
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
    public ArrayList<String> loadLinks() {
        ArrayList<String> links = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    links.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return links;
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
        streamLinksLiveData.postValue(Resource.loading());

        listenerRegistration = db.collection("links")
                .orderBy("index")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) {
                        streamLinksLiveData.postValue(Resource.error("Error loading links"));
                        return;
                    }

                    List<String> links = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String link = doc.getString("link");
                        links.add(link != null ? link : "");
                    }
                    updateLinks(links);

                    streamLinksLiveData.postValue(Resource.success(links));
                });

        return streamLinksLiveData;
    }

    public void removeListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

}
