package com.smoothradio.radio.feature.discover;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.model.Category;
import com.smoothradio.radio.model.RadioStation;

import java.util.ArrayList;
import java.util.List;

public class DiscoverRecyclerViewAdapter extends RecyclerView.Adapter {
    MainActivity mainActivity;
    List<Category> CategoryList;


    public DiscoverRecyclerViewAdapter(List<Category> CategoryList) {
        this.CategoryList = new ArrayList<>(CategoryList);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull  ViewGroup parent, int viewType) {
        mainActivity = (MainActivity) parent.getContext();

        LayoutInflater categoryItemLayoutInflater = LayoutInflater.from(mainActivity);
        View categoryItemView = categoryItemLayoutInflater.inflate(R.layout.categoryitem,parent,false);
        return new CategoryItemViewHolder(categoryItemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CategoryItemViewHolder categoryItemViewHolder= (CategoryItemViewHolder)holder;

        String categoryLabel;
        Category category = CategoryList.get(position);

        categoryLabel= category.label;
        List<RadioStation>CategoryArraylist= new ArrayList<>(category.categoryRadioStationList);

        categoryItemViewHolder.tvLabel.setText(categoryLabel);
        CategoryRecyclerViewAdapter adapter = new CategoryRecyclerViewAdapter(CategoryArraylist);
        categoryItemViewHolder.rvCategory.setAdapter(adapter);
        LinearLayoutManager LLM = new LinearLayoutManager(mainActivity);
        LLM.setOrientation(RecyclerView.HORIZONTAL);
        categoryItemViewHolder.rvCategory.setLayoutManager(LLM);
        categoryItemViewHolder.rvCategory.setHasFixedSize(true);
        categoryItemViewHolder.rvCategory.addItemDecoration(new DividerItemDecoration(mainActivity,1));
    }

    @Override
    public int getItemCount() {
        return CategoryList.size();
    }

    class CategoryItemViewHolder extends RecyclerView.ViewHolder
    {
        TextView tvLabel;
        RecyclerView rvCategory;
        public CategoryItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvCategoryLabel);
            rvCategory =itemView.findViewById(R.id.rvCategory);

        }
    }


}
