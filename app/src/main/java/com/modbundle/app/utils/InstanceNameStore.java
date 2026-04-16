package com.modbundle.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class InstanceNameStore {
    private static final String PREFS = "instance_names";
    private final SharedPreferences prefs;

    public InstanceNameStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getName(String path) {
        return prefs.getString("name_" + path, null);
    }

    public void setName(String path, String name) {
        prefs.edit().putString("name_" + path, name).apply();
    }

    public String getLogo(String path) {
        return prefs.getString("logo_" + path, null);
    }

    public void setLogo(String path, String drawableName) {
        prefs.edit().putString("logo_" + path, drawableName).apply();
    }
}
