package io.github.marcocipriani01.livephotoview.sample;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Image adapter
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageViewHolder> {

    Listener listener;

    public ImageAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageViewHolder holder = ImageViewHolder.inflate(parent);
        holder.itemView.setOnClickListener(view -> listener.onImageClicked(view));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 20;
    }

    public interface Listener {
        void onImageClicked(View view);
    }
}
