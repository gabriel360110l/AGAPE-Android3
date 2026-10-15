package agape.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import agape.app.store.LocalStore
import agape.app.theme.AgapeRoot
import agape.app.ui.navigation.RootView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = AgapeApplication.instance.store
        setContent {
            // store.night leest store.state (mutableStateOf) → Compose hercomposeert
            // automatisch als nightMode verandert.
            CompositionLocalProvider(LocalStore provides store) {
                AgapeRoot(night = store.night) {
                    RootView()
                }
            }
        }
    }
}
