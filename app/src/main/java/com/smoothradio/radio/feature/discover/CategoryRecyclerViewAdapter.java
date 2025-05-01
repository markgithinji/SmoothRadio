package com.smoothradio.radio.feature.discover;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.model.RadioStation;

import java.util.ArrayList;
import java.util.List;

public class CategoryRecyclerViewAdapter extends RecyclerView.Adapter {
    MainActivity mainActivity;
    //containers
    List<RadioStation> favouriteList = new ArrayList<>();
    List<RadioStation> radioStationItems;

    //OnclickListener
    PlayOnclickListener playOnclickListener;
    FavouriteListener favouriteListener;
    //sharedPref
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor prefsEditor;

    CategoryRecyclerViewAdapter(List<RadioStation> stationLogosList) {
        radioStationItems = new ArrayList<>(stationLogosList);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mainActivity = (MainActivity) parent.getContext();
        //sharedpref
        sharedPreferences = mainActivity.getSharedPreferences("PlayerFragmentSharedPref", Context.MODE_PRIVATE);
        prefsEditor = sharedPreferences.edit();
        //favourite list from shared preferences
        favouriteList.clear();
        for (int rsIndex = radioStationItems.size() - 1; rsIndex >= 0; rsIndex--) {
            String snName = radioStationItems.get(rsIndex).getStationName();
            if (sharedPreferences.contains("favouriteStationName" + snName)) {
                favouriteList.add(radioStationItems.get(rsIndex));
            }
        }

        LayoutInflater itemLayoutInflater = LayoutInflater.from(mainActivity);
        View itemView = itemLayoutInflater.inflate(R.layout.categoryradioitem, parent, false);
        return new ItemViewViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RadioStation radioStation = radioStationItems.get(position);

        ItemViewViewHolder itemViewViewHolder = (ItemViewViewHolder) holder;

        if (favouriteList.contains(radioStation)) {
            itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
        } else {
            itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px);
        }

        /////////
        itemViewViewHolder.ivChannelLogo.setBackgroundResource(radioStation.getLogoResource());
        itemViewViewHolder.tvChannelName.setText(radioStation.getStationName());
        playOnclickListener = new PlayOnclickListener(radioStation);
        itemViewViewHolder.ivPlay.setBackgroundResource(R.drawable.playicon);
        itemViewViewHolder.ivPlay.setOnClickListener(playOnclickListener);
        favouriteListener = new FavouriteListener(radioStation, itemViewViewHolder);
        itemViewViewHolder.ivFavourite.setOnClickListener(favouriteListener);
        //scroll animation
        itemViewViewHolder.clParent.setAnimation(AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.recyclerviewscrollanimation));
    }


    @Override
    public int getItemCount() {
        return radioStationItems.size();
    }

    class ItemViewViewHolder extends RecyclerView.ViewHolder {
        TextView tvChannelName;
        ImageView ivChannelLogo;
        ConstraintLayout clParent;
        ImageView ivFavourite;
        ImageView ivPlay;

        public ItemViewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChannelName = itemView.findViewById(R.id.tvCategoryChannelName);
            ivChannelLogo = itemView.findViewById(R.id.ivCategoryLogo);
            ivFavourite = itemView.findViewById(R.id.ivCategoryFavrite);
            clParent = itemView.findViewById(R.id.clCategoryLayout);
            ivPlay = itemView.findViewById(R.id.ivCategoryPlay);
        }
    }

    class PlayOnclickListener implements View.OnClickListener {
        RadioStation radioStation;

        PlayOnclickListener(RadioStation radioStation) {
            this.radioStation = radioStation;
        }

        @Override
        public void onClick(View view) {

            //start playing
//            mainActivity.play(radioStation);
            mainActivity.binding.viewPager.setCurrentItem(1);

//            //update ui
//            if(!mainActivity.playerFragment.getIsPlaying())/////notplaying
//            {
//                currentItemViewViewHolder.ivPlay.setBackgroundResource(R.drawable.exo_notification_pause);
//                //start playing
//                mainActivity.play(url,largeLogo,stationName,position);
//                Toast.makeText(mainActivity, "notplaying", Toast.LENGTH_SHORT).show();
//            }
//            else
//            {
//                Toast.makeText(mainActivity, "isplaying", Toast.LENGTH_SHORT).show();
//                if(position==mainActivity.getCurrentCategoryPosition())
//                {
//                    currentItemViewViewHolder.ivPlay.setBackgroundResource(R.drawable.outline_play_circle_outline_white_48);
//                    mainActivity.playerFragment.playOrStop();
//                }
//                else
//                {
//                    currentItemViewViewHolder.ivPlay.setBackgroundResource(R.drawable.exo_notification_pause);
//                    //start playing
//                    mainActivity.play(url,largeLogo,stationName,position);
//                }
//            }

        }
    }


    class FavouriteListener implements View.OnClickListener {
        RadioStation radioStation;
        ItemViewViewHolder itemViewViewHolder;

        FavouriteListener(RadioStation radioStation, ItemViewViewHolder itemViewViewHolder) {
            this.radioStation = radioStation;
            this.itemViewViewHolder = itemViewViewHolder;
        }

        @Override
        public void onClick(View view) {
            favouriteList.clear();
            for (int rsIndex = radioStationItems.size() - 1; rsIndex >= 0; rsIndex--) {
                String snName = radioStationItems.get(rsIndex).getStationName();
                if (sharedPreferences.contains("favouriteStationName" + snName)) {
                    favouriteList.add(radioStationItems.get(rsIndex));
                }
            }
            if (!favouriteList.contains(radioStation)) {
                if (favouriteList.size() < 20) {
                    favouriteList.add(radioStation);
                    prefsEditor.putString("favouriteStationName" + radioStation.getStationName(), radioStation.getStationName());
                    prefsEditor.commit();
                    itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
                    Snackbar.make(view, "added " + radioStation.getStationName() + " to favourites.", Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(view, "CAN'T add more than 20 favourite stations", Snackbar.LENGTH_LONG).show();
                }
            } else {
                prefsEditor.remove("favouriteStationName" + radioStation.getStationName());
                prefsEditor.commit();
                favouriteList.remove(radioStation);
                itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px);
                Snackbar.make(view, "removed " + radioStation.getStationName() + " from favourites.", Snackbar.LENGTH_SHORT).show();
            }
        }
    }
}
