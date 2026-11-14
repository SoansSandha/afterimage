import io.github.soanssandha.afterimage.buildlogic.releaseVersionCode

plugins {
    id("afterimage.android.overlay")
}

android {
    namespace = "io.github.soanssandha.afterimage.overlay"

    defaultConfig {
        applicationId = "io.github.soanssandha.afterimage.overlay.aodgate"
        versionCode = releaseVersionCode(libs.versions.afterimage.get())
        versionName = libs.versions.afterimage.get()
    }
}
