# Keep model classes used with Gson reflection
-keep class com.maxjubayeryt.modbundle.model.** {
    *;
}

# Keep application class so night mode and manifest linkage remain intact
-keep class com.maxjubayeryt.modbundle.ModBundleApp {
    *;
}

# Keep any API callback or networking helper classes used by reflection or by default Android entry points
-keep class com.maxjubayeryt.modbundle.api.** {
    *;
}

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okio.**
