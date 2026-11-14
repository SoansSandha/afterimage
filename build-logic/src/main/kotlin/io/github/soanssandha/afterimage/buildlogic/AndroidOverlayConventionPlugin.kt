package io.github.soanssandha.afterimage.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `afterimage.android.overlay`: a resource overlay APK, such as `:overlay`. It holds resources only: no code, no
 * Compose, no shrinking, no unit tests. The Magisk module mounts it on the system image, where Android applies it at
 * boot (docs/plan.md §5.5).
 */
class AndroidOverlayConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = libs.versionInt("targetSdk")
                // Resources are matched by name against the target package, so none may be renamed or removed.
                buildTypes.getByName("release").isMinifyEnabled = false
            }
            // AGP's built-in Kotlin adds the standard library to every app module: about 2 MB of dex that an APK with
            // android:hasCode="false" never loads. Only the packaged classpaths drop it; lint still needs Kotlin.
            configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib"))
            }
        }
    }
}
