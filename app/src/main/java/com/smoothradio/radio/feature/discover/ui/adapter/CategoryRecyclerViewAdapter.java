package com.smoothradio.radio.feature.discover.ui.adapter;

import static com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PLAYING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PREPARING;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smoothradio.radio.R;
import com.smoothradio.radio.databinding.CategoryradioitemBinding;
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CategoryRecyclerViewAdapter extends RecyclerView.Adapter {
    private final List<RadioStation> favouriteStations = new ArrayList<>();
    private final List<RadioStation> radioStationItems;
    private final DiscoverFragment.RadioStationActionHandler radioStationActionHandler;
    private final DiscoverRecyclerViewAdapter discoverRecyclerViewAdapter;
    private String state = StreamService.StreamStates.ENDED;
    private RadioStation lastStation;
    private boolean isPlaying;



    CategoryRecyclerViewAdapter(DiscoverRecyclerViewAdapter discoverRecyclerViewAdapter, List<RadioStation> stationLogosList, DiscoverFragment.RadioStationActionHandler radioStationActionHandler) {
        this.discoverRecyclerViewAdapter = discoverRecyclerViewAdapter;
        radioStationItems = new ArrayList<>(stationLogosList);
        this.radioStationActionHandler = radioStationActionHandler;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        CategoryradioitemBinding categoryradioitemBinding = CategoryradioitemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ItemViewViewHolder(categoryradioitemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RadioStation radioStation = radioStationItems.get(position);

        ItemViewViewHolder itemViewViewHolder = (ItemViewViewHolder) holder;

        updateFavoriteIcon(itemViewViewHolder, radioStation);

        itemViewViewHolder.binding.ivCategoryLogo.setImageResource(radioStation.getLogoResource());
        itemViewViewHolder.binding.tvCategoryChannelName.setText(radioStation.getStationName());
        itemViewViewHolder.binding.ivCategoryPlay.setOnClickListener(new PlayOnclickListener(radioStation));
        itemViewViewHolder.binding.ivCategoryFavourite.setOnClickListener(new FavouriteListener(radioStation, itemViewViewHolder));

        updatePlayer(radioStation, itemViewViewHolder);

        //scroll animation, set it only when the view is newly bound
        if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) {
            itemViewViewHolder.binding.clCategoryLayout
                    .setAnimation(AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.recyclerviewscrollanimation));
        }
    }

    private void updatePlayer(RadioStation radioStation, ItemViewViewHolder holder) {
        boolean isPlaying = radioStation.isPlaying();
        if (!isPlaying) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivCategoryPlay.setVisibility(View.VISIBLE);
            holder.binding.ivCategoryPlay.setImageResource(R.drawable.playicon);
            return;
        }

        if (Objects.equals(state, PLAYING)) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivCategoryPlay.setVisibility(View.VISIBLE);
            holder.binding.ivCategoryPlay.setImageResource(R.drawable.pauseicon);
        } else if (Objects.equals(state, PREPARING) || Objects.equals(state, BUFFERING)) {
            holder.binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            holder.binding.ivCategoryPlay.setVisibility(View.INVISIBLE);
        } else {
            holder.binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            holder.binding.ivCategoryPlay.setVisibility(View.VISIBLE);
            holder.binding.ivCategoryPlay.setImageResource(R.drawable.playicon);
        }
    }


    @Override
    public int getItemCount() {
        return radioStationItems.size();
    }


    private class ItemViewViewHolder extends RecyclerView.ViewHolder {
        CategoryradioitemBinding binding;

        public ItemViewViewHolder(@NonNull CategoryradioitemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private class PlayOnclickListener implements View.OnClickListener {
        private final RadioStation radioStation;

        PlayOnclickListener(RadioStation radioStation) {
            this.radioStation = radioStation;
        }

        @Override
        public void onClick(View view) {
            if (lastStation != null) {
                int lastIndex = getPositionOfStation();
                if (lastIndex != RecyclerView.NO_POSITION) {
                    radioStationItems.get(lastIndex).setPlaying(false);
                    notifyItemChanged(lastIndex);
                }
            }

            // Start play
            radioStationActionHandler.onStationSelected(radioStation);

            // Register this adapter as the currently playing one
            discoverRecyclerViewAdapter.setPlayingCategoryAdapter(CategoryRecyclerViewAdapter.this);
        }
    }


    private class FavouriteListener implements View.OnClickListener {
        private final RadioStation radioStation;
        private final ItemViewViewHolder viewHolder;

        FavouriteListener(RadioStation radioStation, ItemViewViewHolder viewHolder) {
            this.radioStation = radioStation;
            this.viewHolder = viewHolder;
        }

        @Override
        public void onClick(View view) {
            boolean isFavorite = favouriteStations.contains(radioStation);

            if (isFavorite) {
                radioStationActionHandler.onRemoveFromFavorites(radioStation.getStationName());
                radioStationActionHandler.onRequestshowToast("Removed from favorites: " + radioStation.getStationName());
            } else {
                if (favouriteStations.size() >= 20) {
                    radioStationActionHandler.onRequestshowToast("Can't add more than 20 favorite stations");
                    return;
                }
                radioStationActionHandler.onAddToFavorites(radioStation.getStationName());
                radioStationActionHandler.onRequestshowToast("Added to favorites: " + radioStation.getStationName());
            }

            // Update UI icon immediately
            updateFavoriteIcon(viewHolder, radioStation);
        }
    }

    private void updateFavoriteIcon(ItemViewViewHolder holder, RadioStation station) {
        int resId = favouriteStations.contains(station)
                ? R.drawable.favorite_20px_filled
                : R.drawable.favorite_20px;
        holder.binding.ivCategoryFavourite.setImageResource(resId);
    }

    public void setFavouriteStations(List<String> favouriteListNames) {
        List<RadioStation> favoriteStations = getFavoriteStationsFromNames(radioStationItems, favouriteListNames);
        favouriteStations.clear();
        favouriteStations.addAll(favoriteStations);
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

    public void setState(String state) {
        this.state = state;
    }

    public void setSelectedStation(RadioStation radioStation) {
        this.lastStation = radioStation;
    }

    public void updateCategory() {
        if (Objects.equals(state, PLAYING)) {
            isPlaying = true;
        } else if (Objects.equals(state, PREPARING) || Objects.equals(state, BUFFERING)) {
            //do nothing
        } else {
            isPlaying = false;
        }
    }

    public void updateStation(RadioStation radioStation) {
        radioStation.setPlaying(radioStation.isPlaying());
        int index = radioStationItems.indexOf(radioStation);
        if (index != RecyclerView.NO_POSITION) {
            notifyItemChanged(index);
        }

    }

    private int getPositionOfStation() {
        return radioStationItems.indexOf(lastStation);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public interface RadioStationActionListener {
        void onStationSelected(RadioStation station);

        void onAddToFavorites(String stationName);

        void onRemoveFromFavorites(String stationName);

        void onRequestshowToast(String message);
    }
}
