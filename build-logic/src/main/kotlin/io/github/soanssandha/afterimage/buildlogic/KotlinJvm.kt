package io.github.soanssandha.afterimage.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** The Kotlin/JVM setup shared by pure-Kotlin modules: the catalog's bytecode level, compiled against that JDK's API. */
internal fun Project.configureKotlinJvm() {
    pluginManager.apply("org.jetbrains.kotlin.jvm")
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = libs.javaVersion
        targetCompatibility = libs.javaVersion
    }
    extensions.configure<KotlinJvmProjectExtension> {
        val jvmTarget = libs.version("jvmTarget")
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
        // Compile against that JDK's API too, not the newer JDK running the build.
        compilerOptions.freeCompilerArgs.add("-Xjdk-release=$jvmTarget")
    }
}
