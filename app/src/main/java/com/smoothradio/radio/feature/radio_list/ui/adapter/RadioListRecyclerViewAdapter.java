package com.smoothradio.radio.feature.radio_list.ui.adapter;

import static com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PLAYING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PREPARING;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.core.model.AdItem;
import com.smoothradio.radio.core.model.ListItem;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.databinding.AdviewBinding;
import com.smoothradio.radio.databinding.EmptyFavouritiesBinding;
import com.smoothradio.radio.databinding.RadioitemBinding;
import com.smoothradio.radio.feature.radio_list.util.StationDiffUtilCallback;
import com.smoothradio.radio.feature.radio_list.util.StationSortHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class RadioListRecyclerViewAdapter extends RecyclerView.Adapter {
    // Constants for view types
    private static final int AD_VIEW = 0;
    private static final int ITEM_VIEW = 1;
    private static final int EMPTY_ITEM = 2;

    // Constants for Advertisement Positions
    private static final int AD_POSITION_1 = 1;
    private static final int AD_POSITION_2 = 9;
    private static final int AD_POSITION_3 = 36;
    private static final int AD_POSITION_4 = 45;
    private static final int AD_POSITION_5 = 54;
    private static final int AD_POSITION_6 = 72;

    // Data collections
    public final List<RadioStation> favouriteList = new ArrayList<>();
    private final List<ListItem> filteredStationNameList = new ArrayList<>();

    private final List<RadioStation> stationList;
    private final List<ListItem> recyclerViewItems;

    // State-related fields
    private final RadioStation emptyRadioStation = new RadioStation(0, 0, "", "", "", "", false, false);
    private DisplayState currentState = DisplayState.POPULAR;
    private String state = "";

    // Dependencies
    private final MainActivity.RadioStationActionHandler radioStationActionHandler;

    public RadioListRecyclerViewAdapter(List<RadioStation> radioStations, MainActivity.RadioStationActionHandler radioStationActionHandler) {
        this.radioStationActionHandler = radioStationActionHandler;

        stationList = new ArrayList<>(radioStations);

        List<ListItem> displayList = stationList.isEmpty()
                ? new ArrayList<>()
                : injectAdItems(stationList);


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
        ListItem item = recyclerViewItems.get(position);

        if (holder instanceof ItemViewViewHolder && item instanceof RadioStation) {
            bindRadioStation((ItemViewViewHolder) holder, (RadioStation) item);
        } else if (holder instanceof AdViewHolder && item instanceof AdItem) {
            bindAdView((AdViewHolder) holder);
        }
    }

    private void bindRadioStation(@NonNull ItemViewViewHolder holder, @NonNull RadioStation radioStation) {

        // Favorite icon
        int favIconRes = radioStation.isFavorite()
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
        ListItem item = recyclerViewItems.get(position);

        // Empty states
        if ((currentState.equals(DisplayState.SEARCH) && filteredStationNameList.isEmpty()) ||
                (currentState.equals(DisplayState.FAVORITES) && favouriteList.isEmpty())) {
            return EMPTY_ITEM;
        }
        if (item instanceof AdItem) {
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
            setPlayingStation(stationId);
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
            boolean isFavorite = radioStation.isFavorite();
            radioStation.setFavorite(!isFavorite);

            radioStationActionHandler.onToggleFavorite(radioStation, !isFavorite);

            // Update UI icon
            itemViewViewHolder.binding.ivFavourite.setImageResource(
                    isFavorite ? R.drawable.favorite_20px : R.drawable.favorite_20px_filled
            );

//            handlePostFavoriteAction();
        }

        private void handlePostFavoriteAction() {
            radioStationActionHandler.onRequestHideKeyboard();

            if (currentState.equals(DisplayState.FAVORITES)) {
                sortFavourites();
            }
        }

    }

    public RadioStation getStationAtPosition(int position) {
        ListItem item = recyclerViewItems.get(position);
        if (item instanceof RadioStation) {
            return (RadioStation) item;
        }
        return emptyRadioStation;
    }


    public int getPositionOfStation(int stationId) {
        RadioStation lastRadioStation = new RadioStation(stationId, 0, "", "", "", "", true, false);
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

    public void setPlayingStation(int stationId) {

        List<RadioStation> updatedList = new ArrayList<>();

        for (RadioStation station : stationList) {
            RadioStation copy = new RadioStation(station);
            copy.setPlaying(station.getId() == stationId);
            updatedList.add(copy);
        }

        update(updatedList);
    }

    public void setFavouriteStations(List<RadioStation> favouriteStations) {
        Log.d("RadioListRecyclerViewAdapter", "setFavouriteStations: " + favouriteStations.size() );
        favouriteList.clear();
        favouriteList.addAll(favouriteStations);
        if (currentState == DisplayState.FAVORITES) {
//            sortFavourites();
        }
    }

    public void update(List<RadioStation> newList) {
        stationList.clear();
        stationList.addAll(newList);

        if (currentState == DisplayState.FAVORITES) return;

        List<ListItem> oldList = new ArrayList<>(recyclerViewItems);
        List<ListItem> newListWithAds = injectAdItems(newList);

        if (recyclerViewItems.isEmpty()) {
            recyclerViewItems.addAll(newListWithAds);
            notifyDataSetChanged();
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new StationDiffUtilCallback(oldList, newListWithAds)
        );

        recyclerViewItems.clear();
        recyclerViewItems.addAll(newListWithAds);

        diffResult.dispatchUpdatesTo(this);
    }

    public void updateFavorites(List<RadioStation> newFavorites) {
        favouriteList.clear();
        favouriteList.addAll(newFavorites);

        if (currentState == DisplayState.POPULAR) return;

        List<ListItem> oldList = new ArrayList<>(recyclerViewItems);
        List<ListItem> newFavoriteList = new ArrayList<>(newFavorites);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new StationDiffUtilCallback(oldList, newFavoriteList)
        );

        recyclerViewItems.clear();
        recyclerViewItems.addAll(newFavoriteList);

        diffResult.dispatchUpdatesTo(this);
    }

    private List<ListItem> injectAdItems(List<RadioStation> originalList) {
        List<ListItem> modifiedList = new ArrayList<>(originalList);

        int[] adPositions = {AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6};
        for (int pos : adPositions) {
            if (pos <= modifiedList.size()) {
                modifiedList.add(pos, new AdItem());
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

        List<ListItem> withAds = injectAdItems(stationList);


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
        List<ListItem> withAds = injectAdItems(sortedList);


        recyclerViewItems.clear();
        recyclerViewItems.addAll(withAds);

        notifyDataSetChanged();
    }

    public void sortPopular() {
        currentState = DisplayState.POPULAR;

        List<ListItem> withAds = injectAdItems(stationList);

        recyclerViewItems.clear();
        recyclerViewItems.addAll(withAds);

        notifyDataSetChanged();
    }

    public void sortFavourites() {
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

        void onToggleFavorite(RadioStation station, boolean isFavorite);

        void onRequestHideKeyboard();

        void onRequestshowToast(String message);
    }

    public enum DisplayState {POPULAR, FAVORITES, ASCENDING, DESCENDING, SEARCH}
}
