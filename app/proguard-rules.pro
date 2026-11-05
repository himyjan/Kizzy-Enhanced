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
-keep class **_HiltFactory { *; }
-keep class **_MembersInjector { *; }
-keep class **_HiltModules* { *; }
-keep class * extends dagger.hilt.internal.UnstableApi { *; }

# Keep Hilt generated classes (explicit patterns)
-keep class com.my.kizzy.Hilt_App { *; }
-keep class com.my.kizzy.Hilt_MainActivity { *; }
-keep class com.my.kizzy.feature_rpc_base.services.Hilt_AppDetectionService { *; }
-keep class com.my.kizzy.feature_rpc_base.services.Hilt_CustomRpcService { *; }
-keep class com.my.kizzy.feature_rpc_base.services.Hilt_ExperimentalRpc { *; }
-keep class com.my.kizzy.feature_rpc_base.services.Hilt_KizzyRpcService { *; }
-keep class com.my.kizzy.feature_rpc_base.services.Hilt_MediaRpcService { *; }
-keep class com.my.kizzy.Hilt_* { *; }
-keep class com.my.kizzy.feature_**.Hilt_* { *; }
-keep class com.my.kizzy.feature_**.services.Hilt_* { *; }
-keep class xyz.dead8309.**.Hilt_* { *; }

# Keep Dagger/Hilt annotations & entry points
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.lifecycle.HiltViewModel class * { *; }
-keepnames class * extends androidx.lifecycle.ViewModel { *; }

# Keep Dagger runtime internals needed by generated code
-keep class dagger.internal.Factory { *; }
-keep class dagger.internal.MembersInjector { *; }
-keep class dagger.internal.DoubleCheck { *; }
-keep class dagger.internal.Preconditions { *; }
-keep class dagger.internal.InstanceFactory { *; }
-keep class dagger.internal.ProviderFactory { *; }
-keep class dagger.internal.FieldSet { *; }
-keep class dagger.internal.MapMultibindings { *; }
-keep class dagger.internal.SetMultibindings { *; }
-keep class dagger.internal.StaticInjection { *; }
-keep class dagger.hilt.internal.** { *; }

# Keep AutoValue annotation (not the builder/codegen)
-keep class com.google.auto.value.AutoValue { *; }

# Keep annotation processing classes referenced by Dagger at runtime
# These are compile-time APIs but referenced from kept Dagger classes
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

# Allow shrinking of unused codegen classes
-dontskipnonpubliclibraryclassmembers
