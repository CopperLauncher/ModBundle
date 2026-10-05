package com.maxjubayeryt.modbundle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.maxjubayeryt.modbundle.R;
import com.maxjubayeryt.modbundle.model.ModResult;

import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {

    public interface OnImageClick { void onClick(ModResult.GalleryImage image); }

    private final List<ModResult.GalleryImage> images;
    private final OnImageClick listener;

    public GalleryAdapter(List<ModResult.GalleryImage> images, OnImageClick listener) {
        this.images = images;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ModResult.GalleryImage img = images.get(position);
        Glide.with(h.image).load(img.url).centerCrop().into(h.image);
        boolean hasTitle = img.title != null && !img.title.trim().isEmpty();
        h.caption.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        if (hasTitle) h.caption.setText(img.title);
        h.itemView.setOnClickListener(v -> listener.onClick(img));
    }

    @Override
    public int getItemCount() { return images.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView caption;
        ViewHolder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.gallery_image);
            caption = v.findViewById(R.id.gallery_caption);
        }
    }
}
