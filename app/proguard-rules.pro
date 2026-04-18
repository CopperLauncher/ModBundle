# Keep model classes used with Gson reflection
-keep class com.modbundle.app.model.** {
    *;
}

# Keep application class so night mode and manifest linkage remain intact
-keep class com.modbundle.app.ModBundleApp {
    *;
}

# Keep any API callback or networking helper classes used by reflection or by default Android entry points
-keep class com.modbundle.app.api.** {
    *;
}

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okio.**
