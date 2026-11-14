plugins {
    id("afterimage.jvm.tool")
}

application {
    mainClass = "io.github.soanssandha.afterimage.lab.OutlineLabKt"
}

dependencies {
    implementation(project(":core"))
}
