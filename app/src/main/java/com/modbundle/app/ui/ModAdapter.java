package com.modbundle.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.modbundle.app.R;
import com.modbundle.app.model.ModResult;

import java.util.List;

public class ModAdapter extends RecyclerView.Adapter<ModAdapter.ModViewHolder> {

    public interface OnInstallClickListener {
        void onInstallClick(ModResult mod);
        default void onModClick(ModResult mod) { onInstallClick(mod); }
    }

    private final List<ModResult> mods;
    private final OnInstallClickListener listener;
    private final Context context;

    public ModAdapter(Context ctx, List<ModResult> mods, OnInstallClickListener listener) {
        this.context = ctx;
        this.mods = mods;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mod, parent, false);
        return new ModViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
        ModResult mod = mods.get(position);
        holder.title.setText(mod.title);
        holder.description.setText(mod.description);
        holder.downloads.setText(formatDownloads(mod.downloads) + " downloads");

        if (mod.iconUrl != null && !mod.iconUrl.isEmpty()) {
            Glide.with(context).load(mod.iconUrl)
                    .placeholder(R.drawable.ic_mod_default)
                    .into(holder.icon);
        } else {
            holder.icon.setImageResource(R.drawable.ic_mod_default);
        }
        // Open detail on card click
        holder.itemView.setOnClickListener(v -> listener.onModClick(mod));
    }

    @Override
    public int getItemCount() { return mods.size(); }
    public List<ModResult> getMods() { return mods; }

    private String formatDownloads(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000f);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000f);
        return String.valueOf(n);
    }

    static class ModViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, description, downloads;

        ModViewHolder(View itemView) {
            super(itemView);
            icon        = itemView.findViewById(R.id.mod_icon);
            title       = itemView.findViewById(R.id.mod_title);
            description = itemView.findViewById(R.id.mod_description);
            downloads   = itemView.findViewById(R.id.mod_downloads);
        }
    }
}
