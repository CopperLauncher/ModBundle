package com.modbundle.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.modbundle.app.R;
import com.modbundle.app.model.ModVersion;
import com.modbundle.app.utils.ModDownloader;
import java.util.List;

public class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.ViewHolder> {
    public interface OnDownloadListener { void onDownload(ModVersion version, ModVersion.VersionFile file); }
    private final List<ModVersion> versions;
    private final OnDownloadListener listener;

    public VersionAdapter(List<ModVersion> versions, OnDownloadListener listener) {
        this.versions = versions;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModVersion v = versions.get(position);
        holder.name.setText(v.versionNumber);

        // Release type badge
        String type = v.versionType != null ? v.versionType : "release";
        holder.typeBadge.setText(type.substring(0, 1).toUpperCase() + type.substring(1));
        int badgeColor = "release".equals(type) ? 0xFF2D7D46 : "beta".equals(type) ? 0xFF9649b8 : 0xFF666666;
        holder.typeBadge.setTextColor(badgeColor);

        // Game versions
        if (v.gameVersions != null && !v.gameVersions.isEmpty()) {
            holder.gameVersions.setText(String.join(" • ", v.gameVersions.subList(0, Math.min(3, v.gameVersions.size()))));
        }
        // Loaders
        if (v.loaders != null && !v.loaders.isEmpty()) {
            holder.loaders.setText(String.join(", ", v.loaders));
        }
        // Date
        if (v.datePublished != null) {
            holder.date.setText(v.datePublished.substring(0, Math.min(10, v.datePublished.length())));
        }

        holder.btnDownload.setOnClickListener(view -> {
            ModVersion.VersionFile file = ModDownloader.getPrimaryFile(v);
            if (file != null) listener.onDownload(v, file);
        });
    }

    @Override public int getItemCount() { return versions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, typeBadge, gameVersions, loaders, date;
        Button btnDownload;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.version_name);
            typeBadge = v.findViewById(R.id.version_type_badge);
            gameVersions = v.findViewById(R.id.version_game_versions);
            loaders = v.findViewById(R.id.version_loaders);
            date = v.findViewById(R.id.version_date);
            btnDownload = v.findViewById(R.id.btn_download_version);
        }
    }
}
