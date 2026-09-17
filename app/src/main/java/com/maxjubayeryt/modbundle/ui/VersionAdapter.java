package com.maxjubayeryt.modbundle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.maxjubayeryt.modbundle.R;
import com.maxjubayeryt.modbundle.model.ModVersion;
import com.maxjubayeryt.modbundle.utils.ModDownloader;
import com.maxjubayeryt.modbundle.utils.ThemeColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Version list for both the mod detail screen and the switch-version dialog.
 *
 * Ported from Copper's ModItemAdapter/InstalledModAdapter behavior: the action button label
 * reflects install state rather than always reading "Download", and versions that don't
 * match the instance's game version/loader can be hidden behind a toggle instead of being
 * silently dropped.
 */
public class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.ViewHolder> {

    /** Matches Copper's InstallButtonState. */
    public enum InstallState { INSTALL, INSTALLED, UPDATE, DOWNGRADE }

    public interface OnDownloadListener { void onDownload(ModVersion version, ModVersion.VersionFile file); }

    private final List<ModVersion> allVersions;
    private List<ModVersion> visibleVersions;
    private final OnDownloadListener listener;

    // Index into allVersions of the currently installed version, or -1 if not installed.
    // Versions come back newest-first, so a LOWER index than this is newer (Update) and a
    // HIGHER index is older (Downgrade) — same ordering assumption Copper relies on.
    private int installedIndex = -1;
    private String filterGameVersion = "";
    private String filterLoader = "";
    private boolean showIncompatible = false;

    public VersionAdapter(List<ModVersion> versions, OnDownloadListener listener) {
        this.allVersions = versions;
        this.visibleVersions = new ArrayList<>(versions);
        this.listener = listener;
    }

    /**
     * Marks which version is currently installed, by the on-disk filename. Strips a
     * trailing ".disabled" so a disabled file still resolves to its version.
     */
    public void setInstalledFileName(String fileName) {
        installedIndex = -1;
        if (fileName != null) {
            String normalized = fileName.endsWith(".disabled")
                    ? fileName.substring(0, fileName.length() - ".disabled".length())
                    : fileName;
            outer:
            for (int i = 0; i < allVersions.size(); i++) {
                ModVersion v = allVersions.get(i);
                if (v.files == null) continue;
                for (ModVersion.VersionFile f : v.files) {
                    if (f.filename != null && f.filename.equals(normalized)) { installedIndex = i; break outer; }
                }
            }
        }
        notifyDataSetChanged();
    }

    /** Sets what counts as "compatible" for the incompatible-version toggle. */
    public void setCompatibilityFilter(String gameVersion, String loader) {
        this.filterGameVersion = gameVersion != null ? gameVersion : "";
        this.filterLoader = loader != null ? loader : "";
        applyFilter();
    }

    public void setShowIncompatible(boolean show) {
        this.showIncompatible = show;
        applyFilter();
    }

    private boolean isCompatible(ModVersion v) {
        if (!filterGameVersion.isEmpty()
                && (v.gameVersions == null || !v.gameVersions.contains(filterGameVersion))) return false;
        // Resource packs and shader packs carry no loader tags at all, so an empty loader
        // list is treated as "not loader-specific" rather than incompatible.
        if (!filterLoader.isEmpty() && v.loaders != null && !v.loaders.isEmpty()
                && !v.loaders.contains(filterLoader)) return false;
        return true;
    }

    private void applyFilter() {
        List<ModVersion> visible = new ArrayList<>();
        for (int i = 0; i < allVersions.size(); i++) {
            ModVersion v = allVersions.get(i);
            // The installed version always stays visible even if it no longer matches the
            // instance's filter (e.g. the instance's MC version was changed afterward).
            if (!showIncompatible && !isCompatible(v) && i != installedIndex) continue;
            visible.add(v);
        }
        // Defensive fallback, same as Copper's: if filtering would leave the list empty
        // despite versions having actually been fetched, show everything rather than
        // render a blank list with no explanation.
        visibleVersions = visible.isEmpty() ? new ArrayList<>(allVersions) : visible;
        notifyDataSetChanged();
    }

    private InstallState computeState(ModVersion version) {
        if (installedIndex < 0) return InstallState.INSTALL;
        int index = allVersions.indexOf(version);
        if (index < 0) return InstallState.INSTALL;
        if (index == installedIndex) return InstallState.INSTALLED;
        return index < installedIndex ? InstallState.UPDATE : InstallState.DOWNGRADE;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModVersion v = visibleVersions.get(position);
        holder.name.setText(v.versionNumber);

        // Release type badge
        String type = v.versionType != null ? v.versionType : "release";
        holder.typeBadge.setText(type.substring(0, 1).toUpperCase() + type.substring(1));
        int badgeColor = "release".equals(type) ? ThemeColors.success(holder.typeBadge)
                : "beta".equals(type) ? ThemeColors.primary(holder.typeBadge)
                : ThemeColors.onSurfaceVariant(holder.typeBadge);
        holder.typeBadge.setTextColor(badgeColor);

        boolean compatible = isCompatible(v);
        holder.incompatibleBadge.setVisibility(compatible ? View.GONE : View.VISIBLE);
        holder.incompatibleBadge.setTextColor(ThemeColors.error(holder.incompatibleBadge));

        // Game versions
        if (v.gameVersions != null && !v.gameVersions.isEmpty()) {
            holder.gameVersions.setText(String.join(" • ", v.gameVersions.subList(0, Math.min(3, v.gameVersions.size()))));
        } else {
            holder.gameVersions.setText("");
        }
        // Loaders
        if (v.loaders != null && !v.loaders.isEmpty()) {
            holder.loaders.setText(String.join(", ", v.loaders));
        } else {
            holder.loaders.setText("");
        }
        // Date
        if (v.datePublished != null) {
            holder.date.setText(v.datePublished.substring(0, Math.min(10, v.datePublished.length())));
        } else {
            holder.date.setText("");
        }

        switch (computeState(v)) {
            case INSTALLED:  holder.btnDownload.setText("Installed"); break;
            case UPDATE:     holder.btnDownload.setText("Update");    break;
            case DOWNGRADE:  holder.btnDownload.setText("Downgrade"); break;
            default:         holder.btnDownload.setText("Install");   break;
        }

        holder.btnDownload.setOnClickListener(view -> {
            ModVersion.VersionFile file = ModDownloader.getPrimaryFile(v);
            if (file != null) listener.onDownload(v, file);
        });
    }

    @Override public int getItemCount() { return visibleVersions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, typeBadge, gameVersions, loaders, date, incompatibleBadge;
        Button btnDownload;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.version_name);
            typeBadge = v.findViewById(R.id.version_type_badge);
            incompatibleBadge = v.findViewById(R.id.version_incompatible_badge);
            gameVersions = v.findViewById(R.id.version_game_versions);
            loaders = v.findViewById(R.id.version_loaders);
            date = v.findViewById(R.id.version_date);
            btnDownload = v.findViewById(R.id.btn_download_version);
        }
    }
}
