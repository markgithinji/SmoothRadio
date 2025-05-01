package com.smoothradio.radio.feature.radio_list;

import static com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PLAYING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PREPARING;

import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class RadioListRecyclerViewAdapter extends RecyclerView.Adapter {
    // Constants for view types
    private static final int AD_VIEW = 0;
    private static final int ITEM_VIEW = 1;
    private static final int EMPTY_FAVOURITE_ITEM = 2;

    // Constants for Advertisement Positions
    private static final int AD_POSITION_1 = 1;
    private static final int AD_POSITION_2 = 9;
    private static final int AD_POSITION_3 = 36;
    private static final int AD_POSITION_4 = 45;
    private static final int AD_POSITION_5 = 54;
    private static final int AD_POSITION_6 = 72;

    // Data collections
    private final List<RadioStation> favouriteList = new ArrayList<>();
    private final List<RadioStation> filteredStationNameList = new ArrayList<>();
    private final List<RadioStation> stationList;
    private final List<RadioStation> recyclerViewItems;

    // State-related fields
    private final RadioStation emptyRadioStation = new RadioStation(0, 0, "", "", "", "", true);
    private DisplayState currentState = DisplayState.POPULAR;
    private String state = "";

    // Current station tracking
    private RadioStation radioStation;

    // Dependencies
    private MainActivity.RadioStationActionHandler radioStationActionHandler;

    public RadioListRecyclerViewAdapter(List<RadioStation> radioStations, MainActivity.RadioStationActionHandler radioStationActionHandler) {
        this.radioStationActionHandler = radioStationActionHandler;

        stationList = new ArrayList<>(radioStations);

        List<RadioStation> displayList = stationList.isEmpty()
                ? new ArrayList<>()
                : injectAdPlaceholders(stationList);

        recyclerViewItems = new ArrayList<>(displayList);

    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

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
        holder.binding.ivPlay.setOnClickListener(new PlayOnclickListener(radioStation));
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
        private int stationId;
        private RadioStation radioStation;

        private PlayOnclickListener(RadioStation radioStation) {
            this.radioStation = radioStation;
            this.stationId = radioStation.getId();
        }

        @Override
        public void onClick(View view) {
            radioStationActionHandler.onStationSelected(radioStation);
            setPlayingStation(stationId); // uses DiffUtil internally
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
            radioStationActionHandler.onRemoveFromFavorites(radioStation.getStationName());

            // Update UI
            itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px);
            radioStationActionHandler.onRequestshowToast( "Removed " + radioStation.getStationName() + " from favorites");

            handlePostFavoriteAction();
        }

        private void addToFavorites(View view) {
            if (favouriteList.size() >= 20) {
                radioStationActionHandler.onRequestshowToast( "Can't add more than 20 favorite stations");
                return;
            }
            // Add to favorites
            radioStationActionHandler.onAddToFavorites(radioStation.getStationName());

            // Update UI
            itemViewViewHolder.binding.ivFavourite.setImageResource(R.drawable.favorite_20px_filled);
            radioStationActionHandler.onRequestshowToast( "Added " + radioStation.getStationName() + " to favorites");

            handlePostFavoriteAction();
        }

        private void handlePostFavoriteAction() {
            radioStationActionHandler.onRequestHideKeyboard();

            if (currentState.equals(DisplayState.FAVORITES)) {
                updateFavoritesDisplay();
            }
        }

        private void updateFavoritesDisplay() {
            List<RadioStation> sortedList = new ArrayList<>(favouriteList);
            StationSortHelper.sortByName(sortedList);
            update(sortedList);
        }

    }

    public RadioStation getStationAtPosition(int position) {
        return recyclerViewItems.get(position);
    }

    public int getPositionOfStation(int stationId) {
        RadioStation lastRadioStation = new RadioStation(stationId, 0, "", "", "", "", true);
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

        update(updatedList);
    }

    public void setFavouriteList(List<String> favouriteListNames) {
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
        recyclerViewItems.addAll(newListWithAds);

        if (currentState == DisplayState.POPULAR) {
            stationList.clear();
            stationList.addAll(newList);
        }

        diffResult.dispatchUpdatesTo(this);
    }

    private List<RadioStation> injectAdPlaceholders(List<RadioStation> originalList) {
        List<RadioStation> modifiedList = new ArrayList<>(originalList);

        RadioStation adPlaceholder = new RadioStation(0, 0, "ad", "", "", "", false);
        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
        for (int pos : adPositions) {
            if (pos <= modifiedList.size()) {
                modifiedList.add(pos, adPlaceholder);
            }
        }

        return modifiedList;
    }

    //--------------------
    //Search
    //-------------------
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

        List<RadioStation> withAds = injectAdPlaceholders(stationList);

        filteredStationNameList.addAll(withAds);
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

    private List<RadioStation> getFavoriteStationsFromNames(List<RadioStation> allStations, List<String> favoriteNames) {
        List<RadioStation> favoriteStations = new ArrayList<>();
        for (RadioStation station : allStations) {
            if (favoriteNames.contains(station.getStationName())) {
                favoriteStations.add(station);
            }
        }
        return favoriteStations;
    }

    //--------------------
    //Sorting Logic
    //-------------------
    public void sortAndDisplay(DisplayState state) {
        currentState = state;

        // Copy and sort
        List<RadioStation> sortedList = new ArrayList<>(stationList);

        if (state == DisplayState.ASCENDING) {
            StationSortHelper.sortByName(sortedList, true);
        } else if (state == DisplayState.DESCENDING) {
            StationSortHelper.sortByName(sortedList, false);
        }

        // Inject ads
        List<RadioStation> withAds = injectAdPlaceholders(sortedList);

       recyclerViewItems.clear();
       recyclerViewItems.addAll(withAds);
    }

    public void sortPopular() {
        currentState = DisplayState.POPULAR;

        List<RadioStation> withAds = injectAdPlaceholders(stationList);

        recyclerViewItems.clear();
        recyclerViewItems.addAll(withAds);

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
    private void showNoFavoritesMessage() {
        radioStationActionHandler.onRequestshowToast("No favorite stations added");
    }

    private void displayEmptyState() {
        recyclerViewItems.clear();
        recyclerViewItems.add(emptyRadioStation);
        notifyDataSetChanged();
    }

    private void displaySortedFavorites() {
        recyclerViewItems.clear();
        recyclerViewItems.addAll(favouriteList);

        notifyDataSetChanged();
    }


    public interface RadioStationActionListener {
        void onStationSelected(RadioStation station);
        void onAddToFavorites(String stationName);
        void onRemoveFromFavorites(String stationName);
        void onRequestHideKeyboard();
        void onRequestshowToast( String message);
    }
    public enum DisplayState {POPULAR, FAVORITES, ASCENDING, DESCENDING, SEARCH}
}
