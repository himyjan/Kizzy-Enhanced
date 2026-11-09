import com.android.build.api.dsl.LibraryExtension
import kizzy.gradle.plugin.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target){
            with(pluginManager) {
                apply("com.android.library")
                // org.jetbrains.kotlin.android is applied natively by AGP 9.x
            }
            extensions.configure<LibraryExtension>{
                configureKotlinAndroid(this)
            }
        }
    }
}