package com.maxjubayeryt.modbundle.ui;

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
import com.maxjubayeryt.modbundle.R;
import com.maxjubayeryt.modbundle.model.ModResult;
import com.maxjubayeryt.modbundle.utils.InstalledIndex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModAdapter extends RecyclerView.Adapter<ModAdapter.ModViewHolder> {

    public interface OnInstallClickListener {
        void onInstallClick(ModResult mod);
        default void onModClick(ModResult mod) { onInstallClick(mod); }
    }

    private final List<ModResult> mods;
    private final OnInstallClickListener listener;
    private final Context context;

    // Install-state lookup, resolved locally with no network call per row — see InstalledIndex.
    private InstalledIndex installedIndex;
    private String instanceKey;
    // Projects whose install is currently running, shown with a spinner in place of the button.
    private final Set<String> installingProjects = new HashSet<>();

    public ModAdapter(Context ctx, List<ModResult> mods, OnInstallClickListener listener) {
        this.context = ctx;
        this.mods = mods;
        this.listener = listener;
    }

    /** Supplies the install-state source. Call again whenever the active instance changes. */
    public void setInstalledIndex(InstalledIndex index, String instanceKey) {
        this.installedIndex = index;
        this.instanceKey = instanceKey;
        notifyDataSetChanged();
    }

    /** Toggles the inline spinner in place of the install button for one project. */
    public void setInstalling(String projectId, boolean installing) {
        if (projectId == null) return;
        if (installing) installingProjects.add(projectId); else installingProjects.remove(projectId);
        for (int i = 0; i < mods.size(); i++) {
            if (projectId.equals(mods.get(i).projectId)) { notifyItemChanged(i); return; }
        }
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

        boolean installing = mod.projectId != null && installingProjects.contains(mod.projectId);
        boolean installed = installedIndex != null && installedIndex.isInstalled(instanceKey, mod.projectId);

        if (installing) {
            holder.installButton.setVisibility(View.GONE);
            holder.installProgress.setVisibility(View.VISIBLE);
        } else {
            holder.installProgress.setVisibility(View.GONE);
            holder.installButton.setVisibility(View.VISIBLE);
            holder.installButton.setText(installed ? "Installed" : "Install");
            // "Installed" stays tappable so it can be used to reinstall/switch, matching
            // Copper — it's a state label, not a disabled button.
            holder.installButton.setOnClickListener(v -> listener.onInstallClick(mod));
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
        Button installButton;
        com.google.android.material.progressindicator.CircularProgressIndicator installProgress;

        ModViewHolder(View itemView) {
            super(itemView);
            icon        = itemView.findViewById(R.id.mod_icon);
            title       = itemView.findViewById(R.id.mod_title);
            description = itemView.findViewById(R.id.mod_description);
            downloads   = itemView.findViewById(R.id.mod_downloads);
            installButton   = itemView.findViewById(R.id.btn_row_install);
            installProgress = itemView.findViewById(R.id.progress_row_install);
        }
    }
}
