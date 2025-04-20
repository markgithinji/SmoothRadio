package com.smoothradio.radio.feature.radio_list;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;


public class RadioListFragment extends Fragment {
RecyclerView rvRadioList;
RadioListRecyclerViewAdapter adapter;
LinearLayoutManager LLM;

    public RadioListFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_music_list, container, false);

        MainActivity mainActivity = (MainActivity) getActivity();
        adapter= mainActivity.radioListRecyclerViewAdapter;

        rvRadioList = root.findViewById(R.id.rvList);
        rvRadioList.setAdapter(adapter);
        LLM = new LinearLayoutManager(root.getContext());
        LLM.setOrientation(RecyclerView.VERTICAL);
        rvRadioList.setLayoutManager(LLM);
        rvRadioList.setHasFixedSize(true);
        rvRadioList.addItemDecoration(new DividerItemDecoration(getContext(),0));
        return root;
    }
}