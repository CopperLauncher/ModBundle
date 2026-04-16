package com.modbundle.app.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.modbundle.app.R;
import java.util.List;

public class SavedPathsAdapter extends RecyclerView.Adapter<SavedPathsAdapter.ViewHolder> {
    public interface Listener {
        void onUse(Uri uri);
        void onRemove(Uri uri);
    }

    private final List<Uri> paths;
    private final Uri activePath;
    private final Listener listener;
    private final Context ctx;

    public SavedPathsAdapter(Context ctx, List<Uri> paths, Uri activePath, Listener listener) {
        this.ctx = ctx;
        this.paths = paths;
        this.activePath = activePath;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_saved_path, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Uri uri = paths.get(position);
        // Show last 2 segments as name
        String uriStr = uri.toString();
        String[] parts = uriStr.split("%2F|/");
        String name = parts.length >= 2 ? parts[parts.length - 2] + "/" + parts[parts.length - 1] : uriStr;
        h.name.setText(name);
        h.uri.setText(uriStr);

        boolean isActive = uri.equals(activePath);
        h.useBtn.setText(isActive ? "✓ Active" : "Use");
        h.useBtn.setAlpha(isActive ? 0.6f : 1f);
        h.useBtn.setOnClickListener(v -> listener.onUse(uri));
        h.removeBtn.setOnClickListener(v -> listener.onRemove(uri));
    }

    @Override public int getItemCount() { return paths.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, uri;
        Button useBtn, removeBtn;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.path_name);
            uri = v.findViewById(R.id.path_uri);
            useBtn = v.findViewById(R.id.btn_use_path);
            removeBtn = v.findViewById(R.id.btn_remove_path);
        }
    }
}
