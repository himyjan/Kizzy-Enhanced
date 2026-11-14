# ============================================================
# Platform & Third Party Warning Suppressions
# ============================================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.my.kizzy.resources.R$drawable

# Suppress warnings for Hilt/KSP framework types that appear unresolved
-dontwarn com.my.kizzy.Hilt_*
-dontwarn xyz.dead8309.**
-dontwarn dagger.hilt.internal.**
-dontwarn dagger.hilt.lifecycle.**
-dontwarn com.google.auto.service.AutoService
-dontwarn dagger.spi.internal.shaded.androidx.room3.compiler.processing.XProcessingEnvConfig$Builder
-dontwarn dagger.spi.internal.shaded.androidx.room3.compiler.processing.XProcessingEnvConfig
-dontwarn dagger.spi.internal.shaded.androidx.room3.compiler.processing.javac.JavacBasicAnnotationProcessor
# Suppress javax/jakarta annotation processor service warnings (migrated to jakarta-inject)
-dontnote javax.annotation.processing.Processor
-dontnote jakarta.annotation.processing.Processor

# Allow optimization to reshape broad rules without throwing warnings
-allowaccessmodification

# ============================================================
# Network & Ktor Infrastructure
# ============================================================
-keep class io.ktor.client.** { *; }
-dontwarn io.ktor.client.**

-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.client.engine.cio.**

-keep class io.ktor.client.plugins.** { *; }
-dontwarn io.ktor.client.plugins.**

-keep class io.ktor.websocket.** { *; }
-dontwarn io.ktor.websocket.**

-keep class io.ktor.http.** { *; }
-dontwarn io.ktor.http.**

# kotlinx.serialization
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# kotlinx.coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-dontnote io.ktor.**
-dontnote kotlinx.serialization.**

# ============================================================
# Data Models & Reflection
# ============================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.my.kizzy.domain.model.** { *; }
-keep class com.my.kizzy.data.remote.** { *; }
-keep class kizzy.gateway.entities.** { *; }

# ============================================================
# Hilt / Dagger Components & Factories
# ============================================================
-keep class **_Factory { *; }
-keep class **_HiltFactory { *; }
-keep class **_MembersInjector { *; }
-keep class **_HiltModules* { *; }
-keep class * extends dagger.hilt.internal.** { *; }

# Keep Hilt entry points and generated service subclasses
-keep class com.my.kizzy.Hilt_* { *; }
-keep class com.my.kizzy.feature_**.Hilt_* { *; }
-keep class com.my.kizzy.feature_**.services.Hilt_* { *; }
-keep class xyz.dead8309.**.Hilt_* { *; }

# Keep Dagger/Hilt annotation markers
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepnames class * { @dagger.hilt.android.lifecycle.HiltViewModel *; }
-keepnames class * extends androidx.lifecycle.ViewModel { *; }

# Keep Hilt-generated ViewModel factories (prevents runtime DI crashes)
-keep class com.my.kizzy.feature_media_rpc.Hilt_MediaScreenViewModel { *; }
-keep class **Hilt*ViewModel { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.DefaultViewModelFactory { *; }

-keep class dagger.internal.** { *; }
-keep class dagger.hilt.internal.** { *; }

# ============================================================
# Dynamic Activity Detection Engine
# ============================================================
# Suppress warnings for system framework class (R8 cannot match members in platform stubs)
-dontwarn android.content.pm.ApplicationInfo
-keep class com.my.kizzy.feature_rpc_base.services.DetectedAiApp { *; }
-keep class com.my.kizzy.feature_rpc_base.services.DetectedGameApp { *; }
-keep class com.my.kizzy.feature_rpc_base.services.DynamicActivityDetectionEngine { *; }

# Assistant / Secure settings rules (Fixed Inner Class Syntax)
-keep class android.app.role.RoleManager { *; }
-keep class android.provider.Settings$Secure { *; }

# ============================================================
# R8 Optimization & Reporting Flags
# ============================================================
-dontskipnonpubliclibraryclassmembers

# Full R8 analysis reports for build size/performance tuning
-printconfiguration build/outputs/mapping/release/configuration.txt
-printmapping build/outputs/mapping/release/mapping.txt
-printseeds build/outputs/mapping/release/seeds.txt
-printusage build/outputs/mapping/release/usage.txt

# Keep classes referenced via reflection by Kotlin serialization
-keepclassmembers class kotlinx.serialization.** {
    <fields>;
}

# Keep descriptor classes needed for reflection-based serialization
-keepnames class kotlinx.serialization.internal.** { *; }

# Preserve parameter names for better stack trace readability
-keepattributes SourceFile,LineNumberTable

# Keep annotations used by Hilt for reliable DI
-keep @interface dagger.hilt.android.EntryPoint
-keep @interface dagger.hilt.android.HiltAndroidApp
-keep @interface dagger.hilt.android.AndroidEntryPoint