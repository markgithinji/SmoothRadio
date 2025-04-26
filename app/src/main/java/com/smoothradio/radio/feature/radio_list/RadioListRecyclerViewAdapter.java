package com.smoothradio.radio.feature.radio_list;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;

import com.smoothradio.radio.core.util.StationDiffUtilCallback;
import com.smoothradio.radio.databinding.AdviewBinding;
import com.smoothradio.radio.databinding.RadioitemBinding;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class RadioListRecyclerViewAdapter extends RecyclerView.Adapter {
    static MainActivity mainActivity;

    //RVitems
    final int AD_VIEW = 0;
    final int ITEM_VIEW = 1;
    final int EMPTY_FAVOURITE_ITEM = 2;

    private final int AD_POSITION_1 = 1;
    private final int AD_POSITION_2 = 9;
    private final int AD_POSITION_3 = 36;
    private final int AD_POSITION_4 = 45;
    private final int AD_POSITION_5 = 54;
    private final int AD_POSITION_6 = 72;

    //containers
    private List<RadioStation> favouriteList = new ArrayList<>();
    public List<RadioStation> recyclerViewItems;
    public List<RadioStation> stationList;
    List<RadioStation> filteredStationNameList = new ArrayList<>();

    RadioStation emptyRadioStation = new RadioStation(0, "", "", "", "", 0);
    private DisplayState currentState = DisplayState.POPULAR;

    int lastStationId;

    String state="";

    RadioStation radioStation;

    BottomSheetBehavior<View> bottomSheetBehavior;

    public RadioListRecyclerViewAdapter(List<RadioStation> radioStations, BottomSheetBehavior<View> bottomSheetBehavior) {
        this.bottomSheetBehavior = bottomSheetBehavior;
        stationList = new ArrayList<>(radioStations);
        recyclerViewItems = new ArrayList<>(stationList);
        //for ads
        if (!radioStations.isEmpty()) {
            addAdPlaceholders(recyclerViewItems);
        }
    }

//    public void update(List<RadioStation> newList) {
//        recyclerViewItems.clear();
//        recyclerViewItems.addAll(injectAdPlaceholders(newList));
//        notifyDataSetChanged();
//    }

    public void update(List<RadioStation> newList) {
        // First-time full refresh
        if (recyclerViewItems.isEmpty()) {
            stationList.addAll(newList);
            recyclerViewItems.addAll(injectAdPlaceholders(newList));
            notifyDataSetChanged();
            return;
        }

        // Prepare lists for diffing
        List<RadioStation> oldList = new ArrayList<>(recyclerViewItems);
        List<RadioStation> newListWithAds = injectAdPlaceholders(newList);

        // Calculate diff
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new StationDiffUtilCallback(oldList, newListWithAds)
        );

        // Apply update
        recyclerViewItems.clear();
        stationList.clear();
        stationList.addAll(newList);
        recyclerViewItems.addAll(newListWithAds);
        diffResult.dispatchUpdatesTo(this);
    }

    private List<RadioStation> injectAdPlaceholders(List<RadioStation> originalList) {
        List<RadioStation> modifiedList = new ArrayList<>(originalList);

        RadioStation adPlaceholder = new RadioStation(-1, "ad", "", "", "", -1);
        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
        for (int pos : adPositions) {
            if (pos <= modifiedList.size()) {
                modifiedList.add(pos, adPlaceholder);
            }
        }

        return modifiedList;
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

    public void setState(String state) {
        this.state = state;
    }
    public void setSelectedStationId(int stationId) {
        lastStationId = stationId;
        if(radioStation==null)
        {
            radioStation = new RadioStation(0, "", "", "", "", stationId);
        }
    }
    public void setSelectedStation(RadioStation radioStation) {
        this.radioStation = radioStation;
    }

    public RadioStation getStationAtPosition(int position) {
        return stationList.get(position);
    }

    public void setFavouriteList(List<String> favouriteListNames) {//TODO: Fix synchronization
        List<RadioStation> favoriteStations = getFavoriteStationsFromNames(stationList, favouriteListNames);
        favouriteList.clear();
        favouriteList.addAll(favoriteStations);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        RadioStation radioStation = recyclerViewItems.get(position);


        if (viewType == ITEM_VIEW) {
            ItemViewViewHolder itemViewViewHolder = (ItemViewViewHolder) holder;

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

            //show playing state
            if (this.radioStation.getId() == radioStation.getId() && this.state.equals(StreamService.StreamStates.PLAYING)) {
                itemViewViewHolder.binding.ivPlay.setImageResource(R.drawable.pauseicon);
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.VISIBLE);
            } else if (this.state.equals(StreamService.StreamStates.PREPARING) && this.radioStation.getId() == radioStation.getId()) {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);

            } else if (this.state.equals(StreamService.StreamStates.BUFFERING) && this.radioStation.getId() == radioStation.getId()) {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);

            } else {
                itemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
                itemViewViewHolder.binding.ivPlay.setVisibility(View.VISIBLE);
            }

        }
        else if (viewType == AD_VIEW) {
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
        return recyclerViewItems.size();
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
//                currentItemViewViewHolder.binding.ivPlay.setImageResource(R.drawable.pauseicon);
//                currentItemViewViewHolder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
//                currentItemViewViewHolder.binding.ivPlay.setVisibility(View.INVISIBLE);
                //
                mainActivity.radioViewModel.setStreamState(StreamService.StreamStates.PREPARING);
                notifyItemChanged(getPosOfStation(lastStationId));// update prev station item
                notifyItemChanged(getPosOfStation(stationId));// update current station item
                mainActivity.radioViewModel.setSelectedStation(radioStation);
            }

        }
    }
    public int getPosOfStation(int stationId) {
        RadioStation lastRadioStation = new RadioStation(0, "", "", "", "", stationId);
        return recyclerViewItems.indexOf(lastRadioStation);
    }

    public boolean stationListIsEmpty() {
        return stationList.isEmpty();
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
            if (isFavorite) {
                removeFromFavorites(view);
            } else {
                addToFavorites(view);
            }
        }

        private boolean isStationFavorite(RadioStation station) {
            return favouriteList.contains(station);
        }

        private void removeFromFavorites(View view) {
            mainActivity.radioViewModel.removeFromFavorites(radioStation.getStationName());

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
            mainActivity.radioViewModel.addToFavorites(radioStation.getStationName());

            // Update UI
            itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
            showSnackbar(view, "Added " + radioStation.getStationName() + " to favorites", Snackbar.LENGTH_LONG);

            handlePostFavoriteAction();
        }

        private void handlePostFavoriteAction() {
            mainActivity.hideKeyboard();

            if (currentState.equals(DisplayState.FAVORITES)) {
                updateFavoritesDisplay();
            }
        }

        private void updateFavoritesDisplay() {
            recyclerViewItems.clear();
            recyclerViewItems.addAll(favouriteList);
            sortStationsByName(recyclerViewItems);
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
        filteredStationNameList.addAll(stationList);
        addAdPlaceholders(filteredStationNameList);
    }

    private void handleSearchQuery(String query) {
        currentState = DisplayState.SEARCH;
        String lowerQuery = query.toLowerCase();

        for (RadioStation station : stationList) {
            if (station.getStationName().toLowerCase().contains(lowerQuery)) {
                filteredStationNameList.add(station);
            }
        }
    }

    private void updateDisplayedResults() {
        recyclerViewItems.clear();

        if (filteredStationNameList.isEmpty()) {
            recyclerViewItems.add(emptyRadioStation);
        } else {
            recyclerViewItems.addAll(filteredStationNameList);
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
        recyclerViewItems.clear();
        recyclerViewItems.addAll(stationList);

        sortStationsByName(recyclerViewItems, true);

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
        recyclerViewItems.clear();
        recyclerViewItems.addAll(stationList);

        // Sort in descending order
        sortStationsByName(recyclerViewItems, false);

        // Notify adapter of changes
        notifyDataSetChanged();
    }

    public void sortFavourites() {
        // Update state
        currentState = DisplayState.FAVORITES;

        if (favouriteList.isEmpty()) {
            showNoFavoritesMessage();
            displayEmptyState();
        } else {
            displaySortedFavorites();
        }
    }

    private List<RadioStation> getFavoriteStationsFromNames(List<RadioStation> allStations, List<String> favoriteNames) {
        List<RadioStation> favoriteStations = new ArrayList<>();
        for (RadioStation station : allStations) {
            if (favoriteNames.contains(station.getStationName())) {
                favoriteStations.add(station);
            }
        }
        return favoriteStations;
    }


    private void showNoFavoritesMessage() {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() ->
                    Toast.makeText(mainActivity, "No favorite stations added!", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void displayEmptyState() {
        recyclerViewItems.clear();
        recyclerViewItems.add(emptyRadioStation);
        notifyDataSetChanged();
    }

    private void displaySortedFavorites() {
        recyclerViewItems.clear();
        recyclerViewItems.addAll(favouriteList);
        sortStationsByName(recyclerViewItems);
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
        recyclerViewItems.clear();
        recyclerViewItems.addAll(stationList);
        addAdPlaceholders(recyclerViewItems);
        notifyDataSetChanged();
    }

    private enum DisplayState {
        POPULAR,
        FAVORITES,
        ASCENDING,
        DESCENDING,
        SEARCH
    }

//    private void addAdPlaceholders() {
//        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
//        for (int pos : adPositions) {
//            recyclerViewItems.add(pos, emptyRadioStation);
//        }
//    }
}
