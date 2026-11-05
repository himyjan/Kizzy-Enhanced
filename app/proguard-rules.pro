# --- Platform & Third Party Warning Suppressions ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.my.kizzy.resources.R$drawable

# --- Network & Ktor Infrastructure ---
-keepclassmembers class io.ktor.http.** { *; }
-keep class io.ktor.client.engine.** { *; }
-dontwarn io.ktor.client.engine.**

# --- Data Models & Reflection (Safe Constructor Retention) ---
-keep class com.my.kizzy.domain.model.** { *; }
-keep class com.my.kizzy.data.remote.** { *; }
-keep class kizzy.gateway.entities.** { *; }

# --- Runtime Type Preservation ---
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Hilt / Dagger Generated Components & Factories ---
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_HiltModules* { *; }
-keep class * extends dagger.hilt.internal.UnstableApi { *; }

# Keep Hilt generated classes
-keep class com.my.kizzy.Hilt_* { *; }
-keep class com.my.kizzy.feature_*.services.Hilt_* { *; }

# Keep Dagger/Hilt annotations & entry points
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.lifecycle.HiltViewModel class * { *; }

# Keep Dagger codegen and internal classes
-keep class dagger.internal.codegen.** { *; }
-keep class dagger.internal.** { *; }
-keepnames class dagger.internal.codegen.** { *; }

# Keep AutoValue classes (referenced by Dagger)
-keep class com.google.auto.value.AutoValue { *; }
-keep class com.google.auto.value.AutoValue$Builder { *; }
-keep class com.google.auto.value.extension.memoized.Memoized { *; }

# Keep annotation processing classes used by Dagger at runtime
-keep class javax.annotation.processing.** { *; }
-keep class javax.lang.model.** { *; }
-keep class javax.tools.Diagnostic$Kind { *; }
-keep class com.squareup.javapoet.** { *; }
-keep class com.google.common.collect.ImmutableList { *; }

# Keep Hilt-generated component classes
-keepnames class * extends dagger.hilt.android.internal.lifecycle.DefaultLifecycleService { *; }
-keepnames class * extends dagger.hilt.android.internal.managers.ViewModelFactory { *; }

# Keep Hilt Application and Activity generated classes
-keep class * extends android.app.Application { *; }
-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }
