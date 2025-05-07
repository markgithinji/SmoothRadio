package com.smoothradio.radio.feature.discover.ui.adapter;

import static com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PLAYING;
import static com.smoothradio.radio.service.StreamService.StreamStates.PREPARING;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smoothradio.radio.R;
import com.smoothradio.radio.databinding.CategoryradioitemBinding;
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.feature.radio_list.util.StationDiffUtilCallback;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CategoryRecyclerViewAdapter extends RecyclerView.Adapter {
    private final List<RadioStation> radioStationItems;
    private RadioStation lastStation;
    private final DiscoverFragment.RadioStationActionHandler radioStationActionHandler;
    private String state = StreamService.StreamStates.ENDED;



    CategoryRecyclerViewAdapter(List<RadioStation> stationLogosList, DiscoverFragment.RadioStationActionHandler radioStationActionHandler) {
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
            if (lastStation != null) {// update last station
                int lastIndex = getPositionOfStation();
                if (lastIndex != RecyclerView.NO_POSITION) {
                    radioStationItems.get(lastIndex).setPlaying(false);
                    notifyItemChanged(lastIndex);
                }
            }

            // Start play
            radioStationActionHandler.onStationSelected(radioStation);
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
            boolean isFavorite = radioStation.isFavorite();
            radioStation.setFavorite(!isFavorite);

            radioStationActionHandler.onToggleFavorite(radioStation, !isFavorite);
            radioStationActionHandler.onRequestshowToast(
                    (isFavorite ? "Removed from favorites: " : "Added to favorites: ") + radioStation.getStationName()
            );

            updateFavoriteIcon(viewHolder, radioStation);
        }
    }

    private void updateFavoriteIcon(ItemViewViewHolder holder, RadioStation station) {
        int resId = station.isFavorite()
                ? R.drawable.favorite_20px_filled
                : R.drawable.favorite_20px;
        holder.binding.ivCategoryFavourite.setImageResource(resId);
    }

    public void setSelectedStationWithState(RadioStation station, String state) {
        this.state = state;

        // Reset all to not playing
        for (RadioStation s : radioStationItems) {
            s.setPlaying(false);
        }

        // Find the matching station and set as playing
        int index = getPositionOfStationById(station.getId());
        if (index != RecyclerView.NO_POSITION) {
            radioStationItems.get(index).setPlaying(true);
        }

        notifyDataSetChanged();
    }

    private int getPositionOfStationById(int id) {
        for (int i = 0; i < radioStationItems.size(); i++) {
            if (radioStationItems.get(i).getId() == id) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private int getPositionOfStation() {
        return radioStationItems.indexOf(lastStation);
    }

    public interface RadioStationActionListener {
        void onStationSelected(RadioStation station);
        void onToggleFavorite(RadioStation station, boolean isFavorite);
        void onRequestshowToast(String message);
    }


}
