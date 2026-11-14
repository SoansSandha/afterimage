plugins {
    `kotlin-dsl`
}

dependencies {
    // Provided by the root build's plugin classpath; compileOnly keeps a single copy of each plugin.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "afterimage.android.application"
            implementationClass = "io.github.soanssandha.afterimage.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "afterimage.android.library"
            implementationClass = "io.github.soanssandha.afterimage.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidOverlay") {
            id = "afterimage.android.overlay"
            implementationClass = "io.github.soanssandha.afterimage.buildlogic.AndroidOverlayConventionPlugin"
        }
        register("jvmLibrary") {
            id = "afterimage.jvm.library"
            implementationClass = "io.github.soanssandha.afterimage.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
