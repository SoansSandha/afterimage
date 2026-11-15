import io.github.soanssandha.afterimage.buildlogic.releaseVersionCode

plugins {
    id("afterimage.android.application")
}

android {
    namespace = "io.github.soanssandha.afterimage"

    defaultConfig {
        applicationId = "io.github.soanssandha.afterimage"
        // Releases derive versionCode from the version (0.1.0 is 100). Dev installs pass a fresh, larger one through
        // scripts/dev-install.sh, so `adb install` can update the copy the Magisk module put on the system image.
        versionCode = providers.gradleProperty("afterimage.versionCode")
            .map { requireNotNull(it.toIntOrNull()) { "afterimage.versionCode must be an integer, was '$it'" } }
            .getOrElse(releaseVersionCode(libs.versions.afterimage.get()))
        versionName = libs.versions.afterimage.get()
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
