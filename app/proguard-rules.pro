# The hidden WallpaperService.Engine callback for AOD (@SystemApi, missing from the SDK stubs). Nothing in the app
# calls it, so R8 would remove it; the framework calls it by name and signature (docs/plan.md §5.2).
-keepclassmembers class io.github.soanssandha.afterimage.wallpaper.OutlineWallpaperService$OutlineEngine {
    public void onAmbientModeChanged(boolean, long);
}
