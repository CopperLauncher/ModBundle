package com.modbundle.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.modbundle.app.R;
import com.modbundle.app.utils.InstanceNameStore;
import java.io.File;
import java.util.List;

public class InstanceAdapter extends RecyclerView.Adapter<InstanceAdapter.ViewHolder> {
    public interface OnSelectListener { void onSelect(File instance, String instanceName); }
    public interface OnRenameListener { void onRename(File instance, String currentName); }
    public interface OnLogoListener { void onChangeLogo(File instance, String currentPath); }

    private final List<File> instances;
    private final OnSelectListener selectListener;
    private OnRenameListener renameListener;
    private OnLogoListener logoListener;
    private final Context ctx;
    private final InstanceNameStore nameStore;

    public InstanceAdapter(Context ctx, List<File> instances, OnSelectListener selectListener) {
        this.ctx = ctx;
        this.instances = instances;
        this.selectListener = selectListener;
        this.nameStore = new InstanceNameStore(ctx);
    }

    public void setRenameListener(OnRenameListener l) { this.renameListener = l; }
    public void setLogoListener(OnLogoListener l) { this.logoListener = l; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_instance, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File instance = instances.get(position);
        String path = instance.getAbsolutePath();

        // Display name - use custom name if set, otherwise folder name
        String customName = nameStore.getName(path);
        String displayName = (customName != null && !customName.isEmpty()) ? customName : instance.getName();
        holder.name.setText(displayName);
        holder.path.setText(path.length() > 55 ? "..." + path.substring(path.length() - 55) : path);

        // Logo
        String logoName = nameStore.getLogo(path);
        if (logoName != null) {
            int resId = ctx.getResources().getIdentifier(logoName, "drawable", ctx.getPackageName());
            if (resId != 0) holder.logo.setImageResource(resId);
            else holder.logo.setImageResource(R.drawable.ic_launcher_monochrome);
        } else {
            holder.logo.setImageResource(R.drawable.ic_launcher_monochrome);
        }

        // Logo click - change logo
        holder.logo.setOnClickListener(v -> {
            if (logoListener != null) logoListener.onChangeLogo(instance, path);
        });

        // Rename click
        holder.btnRename.setOnClickListener(v -> {
            if (renameListener != null) renameListener.onRename(instance, displayName);
        });

        // Select click
        holder.select.setOnClickListener(v -> selectListener.onSelect(instance, displayName));
    }

    @Override public int getItemCount() { return instances.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView logo;
        TextView name, path;
        ImageButton btnRename;
        Button select;
        ViewHolder(View v) {
            super(v);
            logo = v.findViewById(R.id.instance_logo);
            name = v.findViewById(R.id.instance_name);
            path = v.findViewById(R.id.instance_path);
            btnRename = v.findViewById(R.id.btn_rename_instance);
            select = v.findViewById(R.id.btn_select_instance);
        }
    }
}
