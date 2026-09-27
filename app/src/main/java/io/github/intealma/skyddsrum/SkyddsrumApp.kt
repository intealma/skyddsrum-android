package io.github.intealma.skyddsrum

import android.app.Application
import android.content.Context
import io.github.intealma.skyddsrum.premium.Premium
import org.osmdroid.config.Configuration
import java.io.File

class SkyddsrumApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(this@SkyddsrumApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            // The OSM tile usage policy requires a user agent that identifies the app.
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (+$SOURCE_URL)"
            // Keep osmdroid's tile cache in app-private storage (no storage permission needed).
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        Premium.init(this)
    }

    companion object {
        const val SOURCE_URL = "https://github.com/intealma/skyddsrum-android"
    }
}
