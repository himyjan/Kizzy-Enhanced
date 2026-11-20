# ============================================================
# Attributes & Stack Traces
# ============================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-allowaccessmodification

# ============================================================
# kotlinx.serialization Models & Companions
# ============================================================
-keep @kotlinx.serialization.Serializable class * {
    public <init>(...);
    public static kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Domain, Remote & Gateway Data Models
-keepclassmembers class com.my.kizzy.domain.model.** {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keepclassmembers class com.my.kizzy.data.remote.** {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keepclassmembers class kizzy.gateway.entities.** {
    public <init>(...);
    public <fields>;
    public <methods>;
}

# ============================================================
# Hilt / Dagger DI & ViewModels
# ============================================================
-keep @interface dagger.hilt.android.HiltAndroidApp
-keep @interface dagger.hilt.android.AndroidEntryPoint
-keep @interface dagger.hilt.EntryPoint

-keep @dagger.hilt.EntryPoint class * {
    <methods>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ============================================================
# Dynamic Activity Detection & Tink Crypto
# ============================================================
-keepclassmembers class com.my.kizzy.feature_rpc_base.services.DetectedAiApp {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class com.my.kizzy.feature_rpc_base.services.DetectedGameApp {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class com.my.kizzy.feature_rpc_base.services.DynamicActivityDetectionEngine {
    <init>(...);
    <fields>;
    <methods>;
}

-keepclassmembers class com.google.crypto.tink.** {
    public <init>(...);
    public <methods>;
}

# ============================================================
# Targeted Warning / Note Suppressions
# ============================================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.my.kizzy.resources.R$drawable
-dontwarn dagger.hilt.internal.**
-dontwarn dagger.hilt.lifecycle.**
-dontwarn com.google.auto.service.AutoService
-dontwarn android.content.pm.ApplicationInfo
-dontwarn android.app.role.RoleManager
-dontwarn android.provider.Settings$Secure

-dontnote javax.annotation.processing.Processor
-dontnote jakarta.annotation.processing.Processor

# ============================================================
# Release Build Analysis Reports (Optional - keep for R8 tuning)
# ============================================================
-printconfiguration build/outputs/mapping/release/configuration.txt
-printmapping build/outputs/mapping/release/mapping.txt
-printseeds build/outputs/mapping/release/seeds.txt
-printusage build/outputs/mapping/release/usage.txt