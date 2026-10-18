# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Gson
-keep class org.fossify.commons.models.SimpleContact { *; }
-keep class org.fossify.messages.models.Attachment { *; }
-keep class org.fossify.messages.models.MessageAttachment { *; }

# ez-vcard's parameter registries use reflection to enumerate public constants
# and create runtime values for unknown TYPE/MEDIATYPE parameters.
-keepclassmembers,allowobfuscation class ezvcard.parameter.* extends ezvcard.parameter.VCardParameter {
    public static final <fields>;
    <init>(java.lang.String);
}

-keepclassmembers,allowobfuscation class ezvcard.parameter.* extends ezvcard.parameter.MediaTypeParameter {
    <init>(java.lang.String, java.lang.String, java.lang.String);
}

# Commons shows a "You are using a fake version of the app" dialog from BaseSimpleActivity
# whenever the package name does not start with "org.fossify.". This fork deliberately ships its
# own application id, so the void call is marked side effect free and R8 removes it (and the
# dialog string with it). Release builds are minified, which is what is installed.
-assumenosideeffects class org.fossify.commons.extensions.ActivityKt {
    public static void showModdedAppWarning(org.fossify.commons.activities.BaseSimpleActivity);
}

# The compose based screens run the same check from the shared theme wrapper
# (AppTheme -> FakeVersionCheck), which is what fires when a compose dialog - such as the
# customization screen - is opened.
-assumenosideeffects class org.fossify.commons.compose.extensions.ComposeActivityExtensionsKt {
    public static void FakeVersionCheck(androidx.compose.runtime.Composer, int);
}
