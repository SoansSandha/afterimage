package io.github.soanssandha.afterimage.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `afterimage.jvm.tool`: a developer tool run from Gradle on the PC (`./gradlew :<module>:run --args="…"`), such as
 * `:lab`. Never packaged into the app; no coverage gate.
 */
class JvmToolConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureKotlinJvm()
            pluginManager.apply("application")
        }
    }
}
