package io.github.soanssandha.afterimage

import android.app.Application
import io.github.soanssandha.afterimage.wallpaper.AmbientComponent

/** Turns the wallpaper service on or off to match whether this copy may draw on AOD, whenever the process starts. */
class AfterimageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AmbientComponent.sync(this)
    }
}
