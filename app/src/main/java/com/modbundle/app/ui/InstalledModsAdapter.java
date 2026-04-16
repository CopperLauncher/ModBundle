package com.modbundle.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.modbundle.app.R;
import com.modbundle.app.utils.ModIconLoader;
import com.modbundle.app.utils.ModMetadata;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstalledModsAdapter extends RecyclerView.Adapter<InstalledModsAdapter.ViewHolder> {

    public interface OnDeleteListener { void onDelete(Object mod); }
    public interface OnDisableListener { void onDisable(Object mod); }
    public interface OnUpdateListener { void onUpdate(Object mod, ModMetadata meta); }

    private final List<Object> mods;
    private final OnDeleteListener deleteListener;
    private final OnDisableListener disableListener;
    private final OnUpdateListener updateListener;
    private boolean showDisable = true;
    private boolean showCheckboxes = false;
    private String currentType = "mods";

    // Cache metadata per filename
    private final Map<String, ModMetadata> metaCache = new HashMap<>();
    // Track selected items
    private final List<Object> selectedMods = new ArrayList<>();

    public InstalledModsAdapter(List<Object> mods, OnDeleteListener deleteListener,
                                 OnDisableListener disableListener, OnUpdateListener updateListener) {
        this.mods = mods;
        this.deleteListener = deleteListener;
        this.disableListener = disableListener;
        this.updateListener = updateListener;
    }

    public void setShowDisable(boolean show) { this.showDisable = show; }
    public void setCurrentType(String type) { this.currentType = type; }
    public void setShowCheckboxes(boolean show) { this.showCheckboxes = show; selectedMods.clear(); notifyDataSetChanged(); }
    public List<Object> getSelectedMods() { return new ArrayList<>(selectedMods); }
    public void selectAll() { selectedMods.clear(); selectedMods.addAll(mods); notifyDataSetChanged(); }
    public void deselectAll() { selectedMods.clear(); notifyDataSetChanged(); }
    public void updateMetaCache(String filename, ModMetadata meta) {
        metaCache.put(filename, meta);
        notifyDataSetChanged();
    }
    public Map<String, ModMetadata> getMetaCache() { return metaCache; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_installed_mod, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object mod = mods.get(position);
        String name = "";
        long size = 0;

        if (mod instanceof DocumentFile) {
            DocumentFile df = (DocumentFile) mod;
            name = df.getName() != null ? df.getName() : "";
            size = df.length();
        } else if (mod instanceof File) {
            File f = (File) mod;
            name = f.getName();
            size = f.length();
        }

        // Checkbox
        holder.checkbox.setVisibility(showCheckboxes ? View.VISIBLE : View.GONE);
        final Object modRef = mod;
        final String modName = name;
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(selectedMods.contains(mod));
        holder.checkbox.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) { if (!selectedMods.contains(modRef)) selectedMods.add(modRef); }
            else selectedMods.remove(modRef);
        });

        // Icon
        holder.icon.setImageResource(R.drawable.ic_mod_default);
        holder.icon.setTag(name);
        ModIconLoader.FileType fileType = getFileType();
        final String tagName = name;
        final android.widget.ImageView iconView = holder.icon;
        if (mod instanceof DocumentFile) {
            ModIconLoader.load(iconView.getContext(), (DocumentFile) mod, fileType, new android.widget.ImageView(iconView.getContext()) {
                public void setImageBitmap(android.graphics.Bitmap bm) { if (tagName.equals(iconView.getTag())) iconView.setImageBitmap(bm); }
                public void setImageResource(int res) { if (tagName.equals(iconView.getTag())) iconView.setImageResource(res); }
            });
        } else if (mod instanceof File) {
            ModIconLoader.load(iconView.getContext(), (File) mod, fileType, new android.widget.ImageView(iconView.getContext()) {
                public void setImageBitmap(android.graphics.Bitmap bm) { if (tagName.equals(iconView.getTag())) iconView.setImageBitmap(bm); }
                public void setImageResource(int res) { if (tagName.equals(iconView.getTag())) iconView.setImageResource(res); }
            });
        }

        holder.name.setText(name);
        holder.size.setText(formatSize(size));

        boolean isDisabled = name.endsWith(".disabled");
        String ext = isDisabled ? ".disabled" : name.endsWith(".jar") ? ".jar" : ".zip";
        holder.typeBadge.setText(ext);
        holder.typeBadge.setTextColor(isDisabled ? 0xFF888888 : 0xFF2D7D46);
        holder.itemView.setAlpha(isDisabled ? 0.5f : 1f);

        // Update badge
        ModMetadata meta = metaCache.get(name);
        if (meta != null && meta.hasUpdate) {
            holder.btnUpdate.setVisibility(View.VISIBLE);
            holder.btnUpdate.setOnClickListener(v -> { if (updateListener != null) updateListener.onUpdate(modRef, meta); });
        } else {
            holder.btnUpdate.setVisibility(View.GONE);
        }

        // Disable button
        holder.btnDisable.setVisibility(showDisable ? View.VISIBLE : View.GONE);
        holder.btnDisable.setImageResource(isDisabled ? R.drawable.ic_play : R.drawable.ic_pause);
        holder.btnDisable.setColorFilter(isDisabled ? 0xFF4CAF50 : 0xFF888888);
        holder.btnDisable.setOnClickListener(v -> { if (disableListener != null) disableListener.onDisable(modRef); });

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(modRef));
    }

    private ModIconLoader.FileType getFileType() {
        if ("shaderpacks".equals(currentType)) return ModIconLoader.FileType.SHADER;
        if ("resourcepacks".equals(currentType)) return ModIconLoader.FileType.RESOURCEPACK;
        return ModIconLoader.FileType.MOD;
    }

    @Override public int getItemCount() { return mods.size(); }

    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / (1024f * 1024f));
        return String.format("%.1f KB", bytes / 1024f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;
        android.widget.ImageView icon;
        TextView name, size, typeBadge;
        ImageButton btnDelete, btnDisable, btnUpdate;
        ViewHolder(View v) {
            super(v);
            checkbox = v.findViewById(R.id.mod_checkbox);
            icon = v.findViewById(R.id.mod_icon);
            name = v.findViewById(R.id.mod_filename);
            size = v.findViewById(R.id.mod_size);
            typeBadge = v.findViewById(R.id.mod_type_badge);
            btnDelete = v.findViewById(R.id.btn_delete_mod);
            btnDisable = v.findViewById(R.id.btn_disable_mod);
            btnUpdate = v.findViewById(R.id.btn_update_mod);
        }
    }
}
