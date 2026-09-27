package io.github.intealma.skyddsrum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.intealma.skyddsrum.ui.SkyddsrumAppUi
import io.github.intealma.skyddsrum.ui.theme.SkyddsrumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SkyddsrumTheme { SkyddsrumAppUi() }
        }
    }
}
