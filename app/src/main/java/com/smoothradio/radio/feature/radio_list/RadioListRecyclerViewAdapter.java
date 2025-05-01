package com.smoothradio.radio.feature.radio_list;

import static com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PLAYING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PREPARING;

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
import com.google.android.material.snackbar.Snackbar;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.core.ui.StationSortHelper;
import com.smoothradio.radio.core.util.StationDiffUtilCallback;
import com.smoothradio.radio.databinding.AdviewBinding;
import com.smoothradio.radio.databinding.EmptyFavouritiesBinding;
import com.smoothradio.radio.databinding.RadioitemBinding;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


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

    RadioStation emptyRadioStation = new RadioStation(0, 0, "", "", "", "",true);
    private DisplayState currentState = DisplayState.POPULAR;

    int lastStationId;

    String state = "";

    RadioStation radioStation;

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
                EmptyFavouritiesBinding binding = EmptyFavouritiesBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new EmptyFavouriteListViewHolder(binding);
        }
    }




    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        RadioStation radioStation = recyclerViewItems.get(position);

        if (viewType == ITEM_VIEW) {
            bindRadioStation((ItemViewViewHolder) holder, radioStation);
        } else if (viewType == AD_VIEW) {
            bindAdView((AdViewHolder) holder);
        }
    }
    private void bindRadioStation(@NonNull ItemViewViewHolder holder, @NonNull RadioStation radioStation) {
        // Favorite icon
        int favIconRes = favouriteList.contains(radioStation)
                ? R.drawable.favorite_20px_filled
                : R.drawable.favorite_20px;
        holder.binding.ivFavourite.setImageResource(favIconRes);

        // Basic UI
        holder.binding.ivLogo.setBackgroundResource(radioStation.getLogoResource());
        holder.binding.tvChannelName.setText(radioStation.getStationName());
        holder.binding.tvFrequency.setText(radioStation.getFrequency());
        holder.binding.tvLocation.setText(radioStation.getLocation());

        // Play button click
        holder.binding.ivPlay.setImageResource(R.drawable.playicon);
        holder.binding.ivPlay.setOnClickListener(new PlayOnclickListener(radioStation, holder));
        holder.binding.ivFavourite.setOnClickListener(new FavouriteListener(radioStation, holder));

        // Animation on scroll
        holder.binding.clLayout.setAnimation(
                AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.recyclerviewscrollanimation)
        );

        // Handle playing states
        updatePlayerUI(holder, radioStation);
    }
    private void updatePlayerUI(@NonNull ItemViewViewHolder holder, @NonNull RadioStation station) {
        boolean isPlaying = station.isPlaying();

        if (!isPlaying) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivPlay.setVisibility(View.VISIBLE);
            holder.binding.ivPlay.setImageResource(R.drawable.playicon);
            return;
        }

        if (Objects.equals(state, PLAYING)) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivPlay.setVisibility(View.VISIBLE);
            holder.binding.ivPlay.setImageResource(R.drawable.pauseicon);
        } else if (Objects.equals(state, PREPARING) || Objects.equals(state, BUFFERING)) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            holder.binding.ivPlay.setVisibility(View.INVISIBLE);
        } else {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivPlay.setVisibility(View.VISIBLE);
            holder.binding.ivPlay.setImageResource(R.drawable.playicon);
        }
    }
    private void bindAdView(@NonNull AdViewHolder holder) {
        AdRequest adRequest = new AdRequest.Builder().build();
        holder.binding.adViewRecyclerviewItem.loadAd(adRequest);
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
        EmptyFavouritiesBinding binding;
        public EmptyFavouriteListViewHolder(@NonNull EmptyFavouritiesBinding binding) {
            super(binding.getRoot());
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
            this.largeLogo = radioStation.getLogoResource();
            this.stationName = radioStation.getStationName();
            this.currentItemViewViewHolder = itemViewViewHolder;
            this.stationId = radioStation.getId();
        }

        @Override
        public void onClick(View view) {
            mainActivity.radioViewModel.setSelectedStation(radioStation);
            setPlayingStation(stationId); // uses DiffUtil internally
        }
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
            StationSortHelper.sortByName(recyclerViewItems);
            notifyDataSetChanged();
        }

        private void showSnackbar(View view, String message, int duration) {
            snackbar = Snackbar.make(view, message, duration);
            snackbar.setAnchorView(mainActivity.binding.miniPlayer.bottomSheetLayout);
            snackbar.show();
        }

    }

    public RadioListRecyclerViewAdapter(List<RadioStation> radioStations) {
        stationList = new ArrayList<>(radioStations);
        recyclerViewItems = new ArrayList<>(stationList);
        //for ads
        if (!radioStations.isEmpty()) {
            addAdPlaceholders(recyclerViewItems);
        }
    }

    public RadioStation getStationAtPosition(int position) {
        return recyclerViewItems.get(position);
    }
    public int getPositionOfStation(int stationId) {
        RadioStation lastRadioStation = new RadioStation(stationId,0, "", "", "", "", true);
        return recyclerViewItems.indexOf(lastRadioStation);
    }

    public void updateStation(RadioStation station) {
        int position = recyclerViewItems.indexOf(station);
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position);
        }
    }
    public boolean listIsEmpty() {
        return recyclerViewItems.isEmpty();
    }
    public void setState(String state) {
        this.state = state;
    }

    public void setSelectedStationId(int stationId) {
        lastStationId = stationId;

        if (radioStation == null) {// used when populating list for the first time
            radioStation = new RadioStation(stationId,0, "", "", "", "", false);
        }
    }

    public void setSelectedStation(RadioStation radioStation) {
        this.radioStation = radioStation;
    }
    public void setPlayingStation(int stationId) {
        List<RadioStation> updatedList = new ArrayList<>();

        for (RadioStation station : stationList) {
            RadioStation copy = new RadioStation(station);
            copy.setPlaying(station.getId() == stationId);
            updatedList.add(copy);
        }

        update(updatedList); // uses DiffUtil
        lastStationId = stationId;
    }

    public void setFavouriteList(List<String> favouriteListNames) {//TODO: Fix synchronization
        List<RadioStation> favoriteStations = getFavoriteStationsFromNames(stationList, favouriteListNames);
        favouriteList.clear();
        favouriteList.addAll(favoriteStations);
    }


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

        RadioStation adPlaceholder = new RadioStation(0,0, "ad", "", "", "", false);
        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
        for (int pos : adPositions) {
            if (pos <= modifiedList.size()) {
                modifiedList.add(pos, adPlaceholder);
            }
        }

        return modifiedList;
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

    private void addAdPlaceholders(List<RadioStation> list) {
        final int[] AD_POSITIONS = {1, 9, 36, 45, 54, 72};
        for (int position : AD_POSITIONS) {
            if (position < list.size()) {
                list.add(position, emptyRadioStation);
            }
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
        StationSortHelper.sortByName(recyclerViewItems);
        notifyDataSetChanged();
    }

    public void sortAscending() {
        currentState = DisplayState.ASCENDING;

        // Clear and repopulate the list
        recyclerViewItems.clear();
        recyclerViewItems.addAll(stationList);

        StationSortHelper.sortByName(recyclerViewItems, true);

        notifyDataSetChanged();
    }



    public void sortDescending() {
        // Update state using enum
        currentState = DisplayState.DESCENDING;

        // Clear and repopulate the list
        recyclerViewItems.clear();
        recyclerViewItems.addAll(stationList);

        // Sort in descending order
        StationSortHelper.sortByName(recyclerViewItems, false);

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
}
