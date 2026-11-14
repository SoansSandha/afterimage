package io.github.soanssandha.afterimage.buildlogic

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * `afterimage.jvm.library`: pure Kotlin modules such as `:core`. Tests run on JUnit 5 through kotlin.test,
 * and `check` fails if line or branch coverage drops below [MIN_COVERAGE_PERCENT].
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureKotlinJvm()
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            extensions.configure<KoverProjectExtension> {
                reports.verify.rule {
                    minBound(MIN_COVERAGE_PERCENT, CoverageUnit.LINE)
                    minBound(MIN_COVERAGE_PERCENT, CoverageUnit.BRANCH)
                }
            }

            dependencies.add("testImplementation", libs.library("kotlin-test"))
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
            tasks.named("check") {
                dependsOn("koverVerify")
            }
        }
    }

    private companion object {
        const val MIN_COVERAGE_PERCENT = 80
    }
}
