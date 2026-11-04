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
