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

# Keep Dagger/Hilt annotations & entry points
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.lifecycle.HiltViewModel class * { *; }
