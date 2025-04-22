package com.smoothradio.radio.feature.radio_list;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;


import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class RadioListRecyclerViewAdapter extends RecyclerView.Adapter {
    static MainActivity mainActivity;

    Boolean reverse = false;//sort asc/desc
    Boolean showingFavourites = false;
    Boolean showingAscending = false;
    Boolean showingDescending = false;
    Boolean showingSearch = false;
    Boolean showingPopular = true;

    //RVitems
    final int AD_VIEW = 0;
    final int ITEM_VIEW = 1;
    final int EMPTY_FAVOURITE_ITEM = 2;

    //containers
    List<RadioStation> favouriteList = new ArrayList<>();
    public List<RadioStation> radioStationItems;
    List<RadioStation> radioStationItemsWithAds;
    public List<RadioStation> stationListCopy;
    public List<RadioStation> stationListCopyCopy;
    List<RadioStation> filteredStationNameList = new ArrayList<>();

    RadioStation emptyRadioStation = new RadioStation(0, "", "", "", "", 0);
    //OnclickListener
    PlayOnclickListener playOnclickListener;
    FavouriteListener favouriteListener;
    //sharedPref
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor prefsEditor;
    int lastStationId;

    public RadioListRecyclerViewAdapter(List<RadioStation> stationLogosList) {
        stationListCopy = new ArrayList<>(stationLogosList);
        radioStationItems = new ArrayList<>(stationListCopy);
        stationListCopyCopy = new ArrayList<>(stationListCopy);
        radioStationItemsWithAds = new ArrayList<>(stationListCopy);
        //for ads
        if(!stationLogosList.isEmpty())
        {
            radioStationItemsWithAds.add(1, emptyRadioStation);
            radioStationItemsWithAds.add(9, emptyRadioStation);
            radioStationItemsWithAds.add(36, emptyRadioStation);
            radioStationItemsWithAds.add(45, emptyRadioStation);
            radioStationItemsWithAds.add(54, emptyRadioStation);
            radioStationItemsWithAds.add(72, emptyRadioStation);
            radioStationItems.clear();
            radioStationItems.addAll(radioStationItemsWithAds);
        }

    }

    public void update(List<RadioStation> radioStationsList)
    {
        stationListCopy.clear();
        stationListCopyCopy.clear();
        radioStationItems.clear();
        stationListCopy.addAll(radioStationsList);
        stationListCopyCopy.addAll(radioStationsList);
        radioStationItems.addAll(radioStationsList);
        sortPopular();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!recyclerView.canScrollVertically(1)) {
                    mainActivity.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    mainActivity.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        });
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mainActivity = (MainActivity) parent.getContext();

//        //sharedpref
        sharedPreferences = mainActivity.getSharedPreferences("PlayerFragmentSharedPref", Context.MODE_PRIVATE);
//        prefsEditor = sharedPreferences.edit();
//        //for updating last played radio item
//        lastStationId = sharedPreferences.getInt("stationId", 0);

        switch (viewType) {
            case ITEM_VIEW:
                LayoutInflater itemLayoutInflater = LayoutInflater.from(mainActivity);
                View itemView = itemLayoutInflater.inflate(R.layout.radioitem, parent, false);
                return new ItemViewViewHolder(itemView);
            case EMPTY_FAVOURITE_ITEM:
                LayoutInflater emptyFavLayoutInflater = LayoutInflater.from(mainActivity);
                View emptyFavItemView = emptyFavLayoutInflater.inflate(R.layout.empty_favourities, parent, false);
                return new EmptyFavouriteListViewHolder(emptyFavItemView);
            case AD_VIEW:
            default:
                LayoutInflater adInflater = LayoutInflater.from(mainActivity);
                View adItemView = adInflater.inflate(R.layout.adview, parent, false);
                return new AdViewHolder(adItemView);
        }
    }

    public void setSelectedStationId(int stationId) {
        lastStationId = stationId;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        RadioStation radioStation = radioStationItems.get(position);


        if (viewType == ITEM_VIEW) {
            ItemViewViewHolder itemViewViewHolder = (ItemViewViewHolder) holder;

            //favslist
            favouriteList.clear();
            for (int rsIndex = radioStationItems.size() - 1; rsIndex >= 0; rsIndex--) {
                String snName = radioStationItems.get(rsIndex).getStationName();
                if (sharedPreferences.contains("favouriteStationName" + snName)) {
                    favouriteList.add(radioStationItems.get(rsIndex));
                }
            }
            if (favouriteList.contains(radioStation)) {
                itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
            } else {
                itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px);
            }

            /////////
            itemViewViewHolder.ivChannelLogo.setBackgroundResource(radioStation.getSmallLogo());
            itemViewViewHolder.tvChannelName.setText(radioStation.getStationName());
            itemViewViewHolder.tvChannelFrequency.setText(radioStation.getFrequency());
            itemViewViewHolder.tvChannelLocation.setText(radioStation.getLocation());
            playOnclickListener = new PlayOnclickListener(radioStation, itemViewViewHolder);
            itemViewViewHolder.ivPlay.setImageResource(R.drawable.playicon);


            itemViewViewHolder.ivPlay.setOnClickListener(playOnclickListener);
            favouriteListener = new FavouriteListener(radioStation, itemViewViewHolder);
            itemViewViewHolder.ivFavourite.setOnClickListener(favouriteListener);
            //scroll animation
            itemViewViewHolder.clParent.setAnimation(AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.recyclerviewscrollanimation));

            //playing
            if (mainActivity.playerFragment.radioStation.getId() == radioStation.getId() && mainActivity.playerFragment.state.equals("Playing")) {
                itemViewViewHolder.ivPlay.setImageResource(R.drawable.pauseicon);
                itemViewViewHolder.loadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.ivPlay.setVisibility(View.VISIBLE);
            } else if (mainActivity.playerFragment.state.equals("Preparing Audio") && mainActivity.playerFragment.radioStation.getId() == radioStation.getId()) {
                itemViewViewHolder.loadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.ivPlay.setVisibility(View.INVISIBLE);

            } else if (mainActivity.playerFragment.state.equals("Buffering") && radioStation.getId() == lastStationId) {
                itemViewViewHolder.loadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.ivPlay.setVisibility(View.INVISIBLE);

            } else {
                itemViewViewHolder.loadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.ivPlay.setVisibility(View.VISIBLE);
            }

        } else if (viewType == AD_VIEW) {
            AdViewHolder adViewHolder = (AdViewHolder) holder;
            AdRequest adRequest = new AdRequest.Builder().build();
            adViewHolder.adView.loadAd(adRequest);

        }
    }


    @Override
    public int getItemViewType(int position) {
        /*
         * change added ads positions here, at sortPopular, at sortAscending,at sortDescending, at filter() and constuctor
         */
        if (showingSearch) {
            if (!(filteredStationNameList.size() > 0)) {
                return EMPTY_FAVOURITE_ITEM;
            }
        }
        if (showingFavourites) {
            if (!(favouriteList.size() > 0)) {
                return EMPTY_FAVOURITE_ITEM;
            }
        }
        boolean b = position == 1 || position == 9 || position == 36 || position == 45 || position == 54 || position == 72;
        if (showingDescending) {
            if (b) {
                return AD_VIEW;//shows Ads on pos 1 & 9 etc
            }
        }
        if (showingAscending) {
            if (b) {
                return AD_VIEW;//shows Ads on pos 1 & 9 etc
            }
        }
        if (showingPopular) {
            if (b) {
                return AD_VIEW;//shows Ads on pos 1 & 9 etc
            }
        }
        return ITEM_VIEW;
    }

    @Override
    public int getItemCount() {
        return radioStationItems.size();
    }

    class ItemViewViewHolder extends RecyclerView.ViewHolder {
        TextView tvChannelName;
        TextView tvChannelLocation;
        TextView tvChannelFrequency;
        ImageView ivChannelLogo;
        ConstraintLayout clParent;
        ImageView ivFavourite;
        ImageView ivPlay;
        LottieAnimationView loadingAnimation;

        public ItemViewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvChannelLocation = itemView.findViewById(R.id.tvLocation);
            tvChannelFrequency = itemView.findViewById(R.id.tvFrequency);
            ivChannelLogo = itemView.findViewById(R.id.ivLogo);
            ivFavourite = itemView.findViewById(R.id.ivFavrite);
            clParent = itemView.findViewById(R.id.clLayout);
            ivPlay = itemView.findViewById(R.id.ivPlay);
            loadingAnimation = itemView.findViewById(R.id.lottie_loading_animation);
        }
    }

    class AdViewHolder extends RecyclerView.ViewHolder {
        AdView adView;

        public AdViewHolder(@NonNull View itemView) {
            super(itemView);
            adView = itemView.findViewById(R.id.adViewRecyclerviewItem);
        }
    }

    class EmptyFavouriteListViewHolder extends RecyclerView.ViewHolder {
        public EmptyFavouriteListViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    class PlayOnclickListener implements View.OnClickListener {
        String url;
        int largeLogo;
        String stationName;
        ItemViewViewHolder currentItemViewViewHolder;
        int stationId;

        RadioStation radioStation;

        PlayOnclickListener(RadioStation radioStation, ItemViewViewHolder itemViewViewHolder) {
            this.radioStation = radioStation;
            this.url = radioStation.getUrl();
            this.largeLogo = radioStation.getSmallLogo();
            this.stationName = radioStation.getStationName();
            this.currentItemViewViewHolder = itemViewViewHolder;
            this.stationId = radioStation.getId();
        }

        @Override
        public void onClick(View view) {
            Log.d("Adapter", "playFromMainActivity: " + radioStation.getId()+" "+radioStation.getStationName());



            if (stationId == lastStationId) {
//                mainActivity.playerFragment.playOrStop();

                mainActivity.radioViewModel.setSelectedStation(radioStation);
                notifyItemChanged(getPosOfStation(stationId));

//                Log.d("AdapterSame", "playFromMainActivity: " + radioStation.getId());

            } else {
                Log.d("AdapterDifferent", "playFromMainActivity: " + radioStation.getId());


                // not necessary but done to make play feel more instant
                currentItemViewViewHolder.ivPlay.setImageResource(R.drawable.pauseicon);
                currentItemViewViewHolder.loadingAnimation.setVisibility(View.VISIBLE);
                currentItemViewViewHolder.ivPlay.setVisibility(View.INVISIBLE);
                //
//
                mainActivity.radioViewModel.setStreamState(StreamService.StreamStates.PREPARING);
                notifyItemChanged(getPosOfStation(lastStationId));// update prev station item
                notifyItemChanged(getPosOfStation(stationId));// update current station item
                mainActivity.radioViewModel.setSelectedStation(radioStation);

//                mainActivity.play(radioStation);
//                lastStationId = radioStation.getId();
            }

        }
    }



    public void updateStationList() {
        notifyItemChanged(getPosOfStation(lastStationId));
    }

    public int getPosOfStation(int stationId) {
        RadioStation lastRadioStation = new RadioStation(0, "", "", "", "", stationId);
        return radioStationItems.indexOf(lastRadioStation);
    }


    class FavouriteListener implements View.OnClickListener {
        RadioStation radioStation;
        ItemViewViewHolder itemViewViewHolder;
        Snackbar snackbar;

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
                    snackbar = Snackbar.make(view, "added " + radioStation.getStationName() + " to favourites.", Snackbar.LENGTH_LONG);
                } else {
                    snackbar = Snackbar.make(view, "CAN'T add more than 20 favourite stations", Snackbar.LENGTH_LONG);
                }
                snackbar.setAnchorView(mainActivity.binding.miniPlayer.bottomSheetLayout);
                snackbar.show();
                mainActivity.hideKeyboard();
            } else {
                prefsEditor.remove("favouriteStationName" + radioStation.getStationName());
                prefsEditor.commit();
                favouriteList.remove(radioStation);
                itemViewViewHolder.ivFavourite.setImageResource(R.drawable.favorite_20px);
                snackbar = Snackbar.make(view, "removed " + radioStation.getStationName() + " from favourites.", Snackbar.LENGTH_SHORT);
                snackbar.setAnchorView(mainActivity.binding.miniPlayer.bottomSheetLayout);
                snackbar.show();
                mainActivity.hideKeyboard();
                if (showingFavourites) {
                    radioStationItems.clear();
                    radioStationItems.addAll(favouriteList);
                    Collections.sort(radioStationItems, new Comparator<RadioStation>() {
                        @Override
                        public int compare(RadioStation radioStation, RadioStation t1) {
                            return radioStation.getStationName().replace(" ", "").compareTo(t1.getStationName().replace(" ", ""));
                        }
                    });
                    notifyDataSetChanged();
                }
            }
        }
    }



    public void filter(String input) {
        if (showingFavourites || showingAscending || showingDescending) {
            sortPopular();//avoid searching while in other states
        }
        filteredStationNameList.clear();

        if (input == null || input.length() == 0) {
            showingSearch = false;
            showingPopular = true;
            filteredStationNameList.addAll(stationListCopyCopy);
            //ads
            filteredStationNameList.add(1, emptyRadioStation);
            filteredStationNameList.add(9, emptyRadioStation);
            filteredStationNameList.add(36, emptyRadioStation);
            filteredStationNameList.add(45, emptyRadioStation);
            filteredStationNameList.add(54, emptyRadioStation);
            filteredStationNameList.add(72, emptyRadioStation);
        } else {
            showingSearch = true;
            showingPopular = false;
            for (RadioStation radioStation : stationListCopy) {
                if (radioStation.getStationName().toLowerCase().contains(input.toLowerCase())) {
                    filteredStationNameList.add(radioStation);
                }
            }
        }
        radioStationItems.clear();
        radioStationItems.addAll(filteredStationNameList);
        if (!(filteredStationNameList.size() > 0)) {
            radioStationItems.clear();
            radioStationItems.add(emptyRadioStation);
        }
        notifyDataSetChanged();
    }

    public void sortFavourites() {
        showingPopular = false;
        showingSearch = false;
        showingFavourites = true;
        showingAscending = false;
        showingDescending = false;
        favouriteList.clear();
        for (int rsIndex = stationListCopyCopy.size() - 1; rsIndex >= 0; rsIndex--) {
            String snName = stationListCopyCopy.get(rsIndex).getStationName();
            if (sharedPreferences.contains("favouriteStationName" + snName)) {
                favouriteList.add(stationListCopyCopy.get(rsIndex));
            }
        }
        if (!(favouriteList.size() > 0)) {
            Toast.makeText(mainActivity, "no Favourite Stations added!", Toast.LENGTH_SHORT).show();
            radioStationItems.clear();
            radioStationItems.add(emptyRadioStation);
            notifyDataSetChanged();
        } else {
            radioStationItems.clear();
            radioStationItems.addAll(favouriteList);
            Collections.sort(radioStationItems, new Comparator<RadioStation>() {
                @Override
                public int compare(RadioStation radioStation, RadioStation t1) {
                    return radioStation.getStationName().replace(" ", "").compareTo(t1.getStationName().replace(" ", ""));
                }
            });
            notifyDataSetChanged();
        }


    }

    public void sortAscending() {
        showingPopular = false;
        showingFavourites = false;
        showingAscending = true;
        showingDescending = false;
        showingSearch = false;
        radioStationItems.clear();
        radioStationItems.addAll(stationListCopyCopy);
        Collections.sort(radioStationItems, new Comparator<RadioStation>() {
            @Override
            public int compare(RadioStation radioStation, RadioStation t1) {
                return radioStation.getStationName().replace(" ", "").compareTo(t1.getStationName().replace(" ", ""));
            }
        });
        //ads
        radioStationItems.add(1, emptyRadioStation);
        radioStationItems.add(9, emptyRadioStation);
        radioStationItems.add(36, emptyRadioStation);
        radioStationItems.add(45, emptyRadioStation);
        radioStationItems.add(54, emptyRadioStation);
        radioStationItems.add(72, emptyRadioStation);
        notifyDataSetChanged();
        reverse = true;
    }

    public void sortDescending() {
        showingPopular = false;
        showingFavourites = false;
        showingAscending = false;
        showingDescending = true;
        showingSearch = false;
        radioStationItems.clear();
        radioStationItems.addAll(stationListCopyCopy);
        Collections.sort(radioStationItems, new Comparator<RadioStation>() {
            @Override
            public int compare(RadioStation radioStation, RadioStation t1) {
                return radioStation.getStationName().replace(" ", "").compareTo(t1.getStationName().replace(" ", ""));
            }
        });
        reverse = true;
        if (reverse) {
            Collections.reverse(radioStationItems);
            //ads
            radioStationItems.add(1, emptyRadioStation);
            radioStationItems.add(9, emptyRadioStation);
            radioStationItems.add(36, emptyRadioStation);
            radioStationItems.add(45, emptyRadioStation);
            radioStationItems.add(54, emptyRadioStation);
            radioStationItems.add(72, emptyRadioStation);

            notifyDataSetChanged();
            reverse = false;
        } else {
            //ads
            radioStationItems.add(1, emptyRadioStation);
            radioStationItems.add(9, emptyRadioStation);
            radioStationItems.add(36, emptyRadioStation);
            radioStationItems.add(45, emptyRadioStation);
            radioStationItems.add(54, emptyRadioStation);
            radioStationItems.add(72, emptyRadioStation);
            notifyDataSetChanged();
        }
    }

    public void sortPopular() {
        showingPopular = true;
        showingFavourites = false;
        showingAscending = false;
        showingDescending = false;
        showingSearch = false;
        radioStationItems.clear();
        radioStationItems.addAll(stationListCopyCopy);
        //ads
        radioStationItems.add(1, emptyRadioStation);
        radioStationItems.add(9, emptyRadioStation);
        radioStationItems.add(36, emptyRadioStation);
        radioStationItems.add(45, emptyRadioStation);
        radioStationItems.add(54, emptyRadioStation);
        radioStationItems.add(72, emptyRadioStation);
        notifyDataSetChanged();
    }


}
