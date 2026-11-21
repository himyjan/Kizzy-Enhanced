# ============================================================
# Stack Traces, Attributes & Coroutines (R8 Full Mode)
# ============================================================
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature
-keep class kotlin.coroutines.Continuation

# ============================================================
# kotlinx.serialization (Named Companions Only, if used)
# ============================================================
# Standard @Serializable classes & companions are covered by kotlinx-serialization's
# consumer rules. Only add -if rules for specific classes with NAMED companion objects:
# -if @kotlinx.serialization.Serializable class com.my.kizzy.path.ToNamedCompanionClass {
#     public static ** NamedCompanion;
# }
# -keep class com.my.kizzy.path.ToNamedCompanionClass$NamedCompanion {
#     public kotlinx.serialization.KSerializer serializer(...);
# }

# ============================================================
# Dynamic Activity Detection (Reflection / Dynamic Lookup)
# ============================================================
# Note: Only keep classes/members here that are instantiated or invoked dynamically
# (e.g., via reflection or external/dynamic callers) rather than normal direct calls.
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
# Release Mapping & Optional Tuning Dumps
# ============================================================
-printmapping build/outputs/mapping/release/mapping.txt

# Uncomment temporarily when tuning or auditing R8 rules:
# -printconfiguration build/outputs/mapping/release/configuration.txt
# -printseeds build/outputs/mapping/release/seeds.txt
# -printusage build/outputs/mapping/release/usage.txt