package com.maxjubayeryt.modbundle.utils;

import android.view.View;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

/**
 * Resolves the app's current M3 theme colors at runtime. Several places in the UI set
 * colors programmatically (tab selection states, badges, chips) instead of through layout
 * XML, which meant they kept a hardcoded hex (mostly the old #9649b8 purple) even after the
 * rest of the app moved to M3 theme attributes — that's what caused purple to keep showing
 * up in some menus, and it also meant none of those spots followed Material You dynamic
 * color. Every method here takes the View that's about to be colored so MaterialColors can
 * resolve the attribute against that view's actual theme.
 */
public class ThemeColors {
    public static int primary(View v) { return MaterialColors.getColor(v, R.attr.colorPrimary); }
    public static int onPrimary(View v) { return MaterialColors.getColor(v, R.attr.colorOnPrimary); }
    public static int primaryContainer(View v) { return MaterialColors.getColor(v, R.attr.colorPrimaryContainer); }
    public static int onSurface(View v) { return MaterialColors.getColor(v, R.attr.colorOnSurface); }
    public static int onSurfaceVariant(View v) { return MaterialColors.getColor(v, R.attr.colorOnSurfaceVariant); }
    public static int surfaceContainer(View v) { return MaterialColors.getColor(v, R.attr.colorSurfaceContainer); }
    public static int error(View v) { return MaterialColors.getColor(v, R.attr.colorError); }
    public static int success(View v) {
        return v.getContext().getResources().getColor(com.maxjubayeryt.modbundle.R.color.md_success, v.getContext().getTheme());
    }
}
