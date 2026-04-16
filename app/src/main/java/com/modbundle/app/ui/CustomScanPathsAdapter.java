package com.modbundle.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.modbundle.app.R;
import java.util.List;

public class CustomScanPathsAdapter extends RecyclerView.Adapter<CustomScanPathsAdapter.ViewHolder> {
    public interface OnRemoveListener { void onRemove(String path); }
    private final List<String> paths;
    private final OnRemoveListener listener;

    public CustomScanPathsAdapter(List<String> paths, OnRemoveListener listener) {
        this.paths = paths;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_custom_path, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String path = paths.get(position);
        holder.tvPath.setText(path);
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(path));
    }

    @Override public int getItemCount() { return paths.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPath;
        ImageButton btnRemove;
        ViewHolder(View v) {
            super(v);
            tvPath = v.findViewById(R.id.tv_custom_path);
            btnRemove = v.findViewById(R.id.btn_remove_custom_path);
        }
    }
}
