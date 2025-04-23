package com.smoothradio.radio.feature.radio_list;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.databinding.AdviewBinding;
import com.smoothradio.radio.databinding.RadioitemBinding;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class RadioListRecyclerViewAdapter extends RecyclerView.Adapter {
    static MainActivity mainActivity;

    Boolean reverse = false;//sort asc/desc

    //RVitems
    final int AD_VIEW = 0;
    final int ITEM_VIEW = 1;
    final int EMPTY_FAVOURITE_ITEM = 2;

    private static final int AD_POSITION_1 = 1;
    private static final int AD_POSITION_2 = 9;
    private static final int AD_POSITION_3 = 36;
    private static final int AD_POSITION_4 = 45;
    private static final int AD_POSITION_5 = 54;
    private static final int AD_POSITION_6 = 72;

    //containers
    List<RadioStation> favouriteList = new ArrayList<>();
    public List<RadioStation> radioStationItems;
    public List<RadioStation> stationListCopy;
    public List<RadioStation> stationListPopular;
    List<RadioStation> filteredStationNameList = new ArrayList<>();

    RadioStation emptyRadioStation = new RadioStation(0, "", "", "", "", 0);

    //sharedPref
    SharedPreferences sharedPreferences;
    private DisplayState currentState = DisplayState.POPULAR;

    int lastStationId;

    BottomSheetBehavior<View> bottomSheetBehavior;

    public RadioListRecyclerViewAdapter(List<RadioStation> radioStations, BottomSheetBehavior<View> bottomSheetBehavior) {
        this.bottomSheetBehavior = bottomSheetBehavior;

        stationListCopy = new ArrayList<>(radioStations);
        radioStationItems = new ArrayList<>(radioStations);
        stationListPopular = new ArrayList<>(radioStations);
        //for ads
        if (!radioStations.isEmpty()) {
            addAdPlaceholders();
        }
    }

    public void update(List<RadioStation> radioStationsList) {
        stationListCopy.clear();
        stationListPopular.clear();
        radioStationItems.clear();
        stationListCopy.addAll(radioStationsList);
        stationListPopular.addAll(radioStationsList);
        radioStationItems.addAll(radioStationsList);
        sortPopular();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
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


        switch (viewType) {
            case ITEM_VIEW:
                RadioitemBinding radioitemBinding = RadioitemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new ItemViewViewHolder(radioitemBinding);
            case AD_VIEW:
                AdviewBinding adviewBinding = AdviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new AdViewHolder(adviewBinding);
            default:
                LayoutInflater emptyFavLayoutInflater = LayoutInflater.from(mainActivity);
                View emptyFavItemView = emptyFavLayoutInflater.inflate(R.layout.empty_favourities, parent, false);
                return new EmptyFavouriteListViewHolder(emptyFavItemView);
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

            //favourite stations
            refreshFavoritesList();
            if (favouriteList.contains(radioStation)) {
                itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
            } else {
                itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px);
            }

            itemViewViewHolder.binding.ivLogo.setBackgroundResource(radioStation.getSmallLogo());
            itemViewViewHolder.binding.tvChannelName.setText(radioStation.getStationName());
            itemViewViewHolder.binding.tvFrequency.setText(radioStation.getFrequency());
            itemViewViewHolder.binding.tvLocation.setText(radioStation.getLocation());
            itemViewViewHolder.binding.ivPlay.setImageResource(R.drawable.playicon);
            itemViewViewHolder.binding.ivPlay.setOnClickListener(new PlayOnclickListener(radioStation, itemViewViewHolder));
            itemViewViewHolder.binding.ivFavourite.setOnClickListener(new FavouriteListener(radioStation, itemViewViewHolder));

            //scroll animation
            itemViewViewHolder.binding.clLayout.setAnimation(AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.recyclerviewscrollanimation));

            //playing
            if (mainActivity.playerFragment.radioStation.getId() == radioStation.getId() && mainActivity.playerFragment.state.equals("Playing")) {
                itemViewViewHolder.binding.ivPlay.setImageResource(R.drawable.pauseicon);
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.VISIBLE);
            } else if (mainActivity.playerFragment.state.equals("Preparing Audio") && mainActivity.playerFragment.radioStation.getId() == radioStation.getId()) {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);

            } else if (mainActivity.playerFragment.state.equals("Buffering") && radioStation.getId() == lastStationId) {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);

            } else {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.VISIBLE);
            }

        } else if (viewType == AD_VIEW) {
            AdViewHolder adViewHolder = (AdViewHolder) holder;
            AdRequest adRequest = new AdRequest.Builder().build();
            adViewHolder.binding.adViewRecyclerviewItem.loadAd(adRequest);
        }
    }


    @Override
    public int getItemViewType(int position) {
        // Empty states
        if ((currentState.equals(DisplayState.SEARCH) && filteredStationNameList.isEmpty()) ||
                (currentState.equals(DisplayState.FAVORITES) && favouriteList.isEmpty())) {
            return EMPTY_FAVOURITE_ITEM;
        }

        // Ad placeholder check
        boolean isAdPosition = position == AD_POSITION_1 || position == AD_POSITION_2 ||
                position == AD_POSITION_3 || position == AD_POSITION_4 ||
                position == AD_POSITION_5 || position == AD_POSITION_6;

        if ((currentState.equals(DisplayState.POPULAR) || currentState.equals(DisplayState.ASCENDING)
                || currentState.equals(DisplayState.DESCENDING)) && isAdPosition) {
            return AD_VIEW;
        }

        return ITEM_VIEW;
    }

    @Override
    public int getItemCount() {
        return radioStationItems.size();
    }

    class ItemViewViewHolder extends RecyclerView.ViewHolder {
        private final RadioitemBinding binding;

        public ItemViewViewHolder(@NonNull RadioitemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }


    public class AdViewHolder extends RecyclerView.ViewHolder {
        private final AdviewBinding binding;

        public AdViewHolder(@NonNull AdviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
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
            if (stationId == lastStationId) {
                mainActivity.radioViewModel.setSelectedStation(radioStation);
                notifyItemChanged(getPosOfStation(stationId));

            } else {

                // not necessary but done to make play feel more instant
                currentItemViewViewHolder.binding.ivPlay.setImageResource(R.drawable.pauseicon);
                currentItemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
                currentItemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);
                //
                mainActivity.radioViewModel.setStreamState(StreamService.StreamStates.PREPARING);
                notifyItemChanged(getPosOfStation(lastStationId));// update prev station item
                notifyItemChanged(getPosOfStation(stationId));// update current station item
                mainActivity.radioViewModel.setSelectedStation(radioStation);
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
            // Check current favorite state
            boolean isFavorite = isStationFavorite(radioStation);
            refreshFavoritesList();
            if (isFavorite) {
                removeFromFavorites(view);
            } else {
                addToFavorites(view);
            }
        }

        private boolean isStationFavorite(RadioStation station) {
            return sharedPreferences.contains("favouriteStationName" + station.getStationName());
        }

        private void removeFromFavorites(View view) {
            sharedPreferences.edit()
                    .remove("favouriteStationName" + radioStation.getStationName())
                    .apply();
            // Update UI
            itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px);
            showSnackbar(view, "Removed " + radioStation.getStationName() + " from favorites", Snackbar.LENGTH_SHORT);

            handlePostFavoriteAction();
        }

        private void addToFavorites(View view) {
            if (favouriteList.size() >= 20) {
                showSnackbar(view, "Can't add more than 20 favorite stations", Snackbar.LENGTH_LONG);
                return;
            }
            // Add to favorites
            sharedPreferences.edit()
                    .putString("favouriteStationName" + radioStation.getStationName(), radioStation.getStationName())
                    .apply();
            // Update UI
            itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
            showSnackbar(view, "Added " + radioStation.getStationName() + " to favorites", Snackbar.LENGTH_LONG);

            handlePostFavoriteAction();
        }

        private void handlePostFavoriteAction() {
            mainActivity.hideKeyboard();
            refreshFavoritesList();

            if (currentState.equals(DisplayState.FAVORITES)) {
                updateFavoritesDisplay();
            }
        }

        private void updateFavoritesDisplay() {
            radioStationItems.clear();
            radioStationItems.addAll(favouriteList);
            sortStationsByName(radioStationItems);
            notifyDataSetChanged();
        }

        private void showSnackbar(View view, String message, int duration) {
            snackbar = Snackbar.make(view, message, duration);
            snackbar.setAnchorView(mainActivity.binding.miniPlayer.bottomSheetLayout);
            snackbar.show();
        }

    }


    public void filter(String input) {
        // Reset to popular if currently in special modes
        if (currentState != DisplayState.POPULAR && currentState != DisplayState.SEARCH) {
            sortPopular();
        }
        // Clear previous results
        filteredStationNameList.clear();

        if (TextUtils.isEmpty(input)) {
            handleEmptySearch();
        } else {
            handleSearchQuery(input);
        }

        updateDisplayedResults();
    }

    private void handleEmptySearch() {
        currentState = DisplayState.POPULAR;
        filteredStationNameList.addAll(stationListPopular);
        addAdPlaceholders(filteredStationNameList);
    }

    private void handleSearchQuery(String query) {
        currentState = DisplayState.SEARCH;
        String lowerQuery = query.toLowerCase();

        for (RadioStation station : stationListPopular) {
            if (station.getStationName().toLowerCase().contains(lowerQuery)) {
                filteredStationNameList.add(station);
            }
        }
    }

    private void updateDisplayedResults() {
        radioStationItems.clear();

        if (filteredStationNameList.isEmpty()) {
            radioStationItems.add(emptyRadioStation);
        } else {
            radioStationItems.addAll(filteredStationNameList);
        }

        notifyDataSetChanged();
    }

    // Consolidated ad placement method
    private void addAdPlaceholders(List<RadioStation> list) {
        final int[] AD_POSITIONS = {1, 9, 36, 45, 54, 72};
        for (int position : AD_POSITIONS) {
            if (position < list.size()) {
                list.add(position, emptyRadioStation);
            }
        }
    }

    public void sortAscending() {
        currentState = DisplayState.ASCENDING;

        // Clear and repopulate the list
        radioStationItems.clear();
        radioStationItems.addAll(stationListPopular);

        sortStationsByName(radioStationItems, true);

        notifyDataSetChanged();
    }

    // Reusable sorting method
    private void sortStationsByName(List<RadioStation> stations, boolean ascending) {
        Collections.sort(stations, (s1, s2) -> {
            String name1 = s1.getStationName().replace(" ", "");
            String name2 = s2.getStationName().replace(" ", "");
            int comparison = name1.compareToIgnoreCase(name2);
            return ascending ? comparison : -comparison; // Reverse for descending
        });
    }


    public void sortDescending() {
        // Update state using enum
        currentState = DisplayState.DESCENDING;

        // Clear and repopulate the list
        radioStationItems.clear();
        radioStationItems.addAll(stationListPopular);

        // Sort in descending order
        sortStationsByName(radioStationItems, false);

        // Notify adapter of changes
        notifyDataSetChanged();
    }

    public void sortFavourites() {
        // Update state
        currentState = DisplayState.FAVORITES;

        // Refresh favorites list from shared preferences
        refreshFavoritesList();

        if (favouriteList.isEmpty()) {
            showNoFavoritesMessage();
            displayEmptyState();
        } else {
            displaySortedFavorites();
        }
    }

    private void refreshFavoritesList() {
        favouriteList.clear();
        for (RadioStation station : stationListPopular) {
            String prefKey = "favouriteStationName" + station.getStationName();
            if (sharedPreferences.contains(prefKey)) {
                favouriteList.add(station);
            }
        }
    }

    private void showNoFavoritesMessage() {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() ->
                    Toast.makeText(mainActivity, "No favorite stations added!", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void displayEmptyState() {
        radioStationItems.clear();
        radioStationItems.add(emptyRadioStation);
        notifyDataSetChanged();
    }

    private void displaySortedFavorites() {
        radioStationItems.clear();
        radioStationItems.addAll(favouriteList);
        sortStationsByName(radioStationItems);
        notifyDataSetChanged();
    }

    // Reusable sorting utility
    private void sortStationsByName(List<RadioStation> stations) {
        Collections.sort(stations, (s1, s2) ->
                s1.getStationName().replace(" ", "")
                        .compareToIgnoreCase(s2.getStationName().replace(" ", ""))
        );
    }

    public void sortPopular() {
        currentState = DisplayState.POPULAR;
        radioStationItems.clear();
        radioStationItems.addAll(stationListPopular);
        addAdPlaceholders();
        notifyDataSetChanged();
    }

    private enum DisplayState {
        POPULAR,
        FAVORITES,
        ASCENDING,
        DESCENDING,
        SEARCH
    }

    // Helper Methods
    private void addAdPlaceholders() {
        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
        for (int pos : adPositions) {
            radioStationItems.add(pos, emptyRadioStation);
        }
    }
}
