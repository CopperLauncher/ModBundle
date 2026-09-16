package com.maxjubayeryt.modbundle.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.maxjubayeryt.modbundle.R;
import com.maxjubayeryt.modbundle.utils.InstanceNameStore;
import java.io.File;
import java.util.List;

public class InstanceAdapter extends RecyclerView.Adapter<InstanceAdapter.ViewHolder> {
    public static class InstanceEntry {
        public final String path;
        public final boolean contentUri;
        public InstanceEntry(String path, boolean contentUri) {
            this.path = path;
            this.contentUri = contentUri;
        }
    }

    public interface OnSelectListener { void onSelect(InstanceEntry instance, String instanceName); }
    public interface OnRenameListener { void onRename(InstanceEntry instance, String currentName); }
    public interface OnLogoListener { void onChangeLogo(InstanceEntry instance, String currentPath); }
    public interface OnDeleteListener { void onDelete(InstanceEntry instance, String currentPath); }

    private final List<InstanceEntry> instances;
    private final OnSelectListener selectListener;
    private OnRenameListener renameListener;
    private OnLogoListener logoListener;
    private OnDeleteListener deleteListener;
    private final Context ctx;
    private final InstanceNameStore nameStore;
    private String activeInstancePath = null;

    public InstanceAdapter(Context ctx, List<InstanceEntry> instances, OnSelectListener selectListener) {
        this.ctx = ctx;
        this.instances = instances;
        this.selectListener = selectListener;
        this.nameStore = new InstanceNameStore(ctx);
    }

    public void setRenameListener(OnRenameListener l) { this.renameListener = l; }
    public void setLogoListener(OnLogoListener l) { this.logoListener = l; }
    public void setDeleteListener(OnDeleteListener l) { this.deleteListener = l; }
    public void setActiveInstancePath(String path) { this.activeInstancePath = path; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_instance, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstanceEntry instance = instances.get(position);
        String path = instance.path;

        String customName = nameStore.getName(path);
        String displayName;
        if (customName != null && !customName.trim().isEmpty()) {
            displayName = customName;
        } else if (instance.contentUri) {
            // Tree URIs are typically "primary:FolderName" (or "1234-ABCD:FolderName" for
            // removable storage) — strip the volume prefix so the instance shows its actual
            // folder name instead of the raw URI segment.
            String candidate = Uri.parse(path).getLastPathSegment();
            if (candidate != null && candidate.contains(":")) {
                candidate = candidate.substring(candidate.lastIndexOf(':') + 1);
            }
            displayName = candidate != null && !candidate.trim().isEmpty() ? candidate : path;
        } else {
            String fromFile = new File(path).getName();
            displayName = fromFile != null && !fromFile.trim().isEmpty() ? fromFile : path;
        }
        holder.name.setText(displayName);
        holder.path.setText(path.length() > 55 ? "..." + path.substring(path.length() - 55) : path);

        // Loader badge
        String loader = nameStore.getLoader(path);
        if (loader != null && !loader.isEmpty()) {
            holder.loaderBadge.setText(loader);
            holder.loaderBadge.setVisibility(View.VISIBLE);
        } else {
            holder.loaderBadge.setVisibility(View.GONE);
        }

        // Version badge
        String version = nameStore.getVersion(path);
        if (version != null && !version.isEmpty()) {
            holder.versionBadge.setText(version);
            holder.versionBadge.setVisibility(View.VISIBLE);
        } else {
            holder.versionBadge.setVisibility(View.GONE);
        }

        // Logo
        String logoName = nameStore.getLogo(path);
        if (logoName != null) {
            if (logoName.startsWith("content://") || logoName.startsWith("file://")) {
                try {
                    holder.logo.setImageURI(Uri.parse(logoName));
                } catch (Exception e) {
                    holder.logo.setImageResource(R.drawable.ic_mod_default);
                }
            } else {
                int resId = ctx.getResources().getIdentifier(logoName, "drawable", ctx.getPackageName());
                holder.logo.setImageResource(resId != 0 ? resId : R.drawable.ic_mod_default);
            }
        } else {
            holder.logo.setImageResource(R.drawable.ic_mod_default);
        }

        // Active state - show Selected button differently
        boolean isActive = path.equals(activeInstancePath);
        holder.select.setText(isActive ? "✓ Active" : "Select");
        holder.select.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            isActive ? com.maxjubayeryt.modbundle.utils.ThemeColors.surfaceContainer(holder.select) : com.maxjubayeryt.modbundle.utils.ThemeColors.primary(holder.select)));

        holder.logo.setOnClickListener(v -> { if (logoListener != null) logoListener.onChangeLogo(instance, path); });
        holder.btnRename.setOnClickListener(v -> { if (renameListener != null) renameListener.onRename(instance, displayName); });
        holder.btnDelete.setOnClickListener(v -> { if (deleteListener != null) deleteListener.onDelete(instance, path); });
        holder.select.setOnClickListener(v -> selectListener.onSelect(instance, displayName));
    }

    @Override public int getItemCount() { return instances.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView logo;
        TextView name, path, loaderBadge, versionBadge;
        ImageButton btnRename, btnDelete;
        Button select;
        ViewHolder(View v) {
            super(v);
            logo = v.findViewById(R.id.instance_logo);
            name = v.findViewById(R.id.instance_name);
            path = v.findViewById(R.id.instance_path);
            loaderBadge = v.findViewById(R.id.instance_loader_badge);
            versionBadge = v.findViewById(R.id.instance_version_badge);
            btnRename = v.findViewById(R.id.btn_rename_instance);
            btnDelete = v.findViewById(R.id.btn_delete_instance);
            select = v.findViewById(R.id.btn_select_instance);
        }
    }
}
