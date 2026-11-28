# ============================================================
# Stack Traces, Attributes & Coroutines (R8 Full Mode)
# ============================================================
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,RuntimeVisibleAnnotations
-keep class kotlin.coroutines.Continuation

# ============================================================
# kotlinx.serialization (Named Companions Only, if used)
# ============================================================
# Standard @Serializable classes & companions are provided via consumer rules.
# Add -if rules only for specific classes using a NAMED companion object:
# -if @kotlinx.serialization.Serializable class com.my.kizzy.path.ToNamedCompanionClass {
#     public static ** NamedCompanion;
# }
# -keep class com.my.kizzy.path.ToNamedCompanionClass$NamedCompanion {
#     public kotlinx.serialization.KSerializer serializer(...);
# }

# ============================================================
# Dynamic Activity Detection
# ============================================================
# Use <init>(...); only if instantiated with a Class/KClass/constructor reference.
# If instantiated via Class.forName("..."), remove `allowobfuscation` and add `-keep`.
-keepclassmembers,allowobfuscation class com.my.kizzy.feature_rpc_base.services.DetectedAiApp {
    <init>(...);
}
-keepclassmembers,allowobfuscation class com.my.kizzy.feature_rpc_base.services.DetectedGameApp {
    <init>(...);
}
-keepclassmembers,allowobfuscation class com.my.kizzy.feature_rpc_base.services.DynamicActivityDetectionEngine {
    <init>(...);
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

# Audit these periodically; avoid masking real missing references:
-dontwarn dagger.hilt.internal.**
-dontwarn dagger.hilt.lifecycle.**
-dontwarn com.google.auto.service.AutoService
-dontwarn android.content.pm.ApplicationInfo
-dontwarn android.app.role.RoleManager
-dontwarn android.provider.Settings$Secure

-dontnote jakarta.annotation.processing.Processor

# ============================================================
# Release Mapping & Optional Tuning Dumps
# ============================================================
-printmapping build/outputs/mapping/release/mapping.txt

# Uncomment temporarily when tuning or auditing R8 rules:
# -printconfiguration build/outputs/mapping/release/configuration.txt
# -printseeds build/outputs/mapping/release/seeds.txt
# -printusage build/outputs/mapping/release/usage.txt