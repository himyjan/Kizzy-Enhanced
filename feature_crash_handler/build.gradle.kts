plugins {
    id ("kizzy.android.library")
    id ("kizzy.android.library.compose")
    id ("kizzy.android.feature")
}

android {
    namespace = "com.my.kizzy.feature_crash_handler"
}

dependencies {
    implementation (libs.activity.compose)
    implementation (projects.theme)
    implementation (projects.data)
    implementation (projects.common.resources)
}