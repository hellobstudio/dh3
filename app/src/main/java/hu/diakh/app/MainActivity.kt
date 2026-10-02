package hu.diakh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFE65100), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2), onPrimaryContainer = Color(0xFF3E1C00),
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF3E1C00),
    background = Color(0xFFFFFBF5), onBackground = Color(0xFF241A10),
    surface = Color(0xFFFFFBF5), onSurface = Color(0xFF241A10),
    surfaceVariant = Color(0xFFFFEFD9), onSurfaceVariant = Color(0xFF4F4538),
    outline = Color(0xFF8A7B6A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = Color(0xFF3E2000),
    primaryContainer = Color(0xFF7A4100), onPrimaryContainer = Color(0xFFFFE0B2),
    secondaryContainer = Color(0xFF5A3600), onSecondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF1A1410), onBackground = Color(0xFFF0E6DC),
    surface = Color(0xFF1A1410), onSurface = Color(0xFFF0E6DC),
    surfaceVariant = Color(0xFF2B2219), onSurfaceVariant = Color(0xFFD8C8B6),
    outline = Color(0xFF9C8B78)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db(applicationContext)
        val prefs = getSharedPreferences("diakh", MODE_PRIVATE)
        setContent {
            val system = isSystemInDarkTheme()
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", system)) }
            MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
                Surface {
                    App(db, dark) {
                        dark = it
                        prefs.edit().putBoolean("dark", it).apply()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(db: Db, dark: Boolean, setDark: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ver by remember { mutableIntStateOf(0) }
    val bump = { ver++; Unit }
    val tabs = listOf("➕" to "Új", "📋" to "Hiányzások", "⚙️" to "Beállítások")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DiákH") },
                actions = {
                    TextButton(onClick = { setDark(!dark) }) {
                        Text(if (dark) "☀️" else "🌙", fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> NewScreen(db, ver, bump)
                1 -> AbsScreen(db, ver, bump)
                else -> SettingsScreen(db, ver, bump, dark, setDark)
            }
        }
    }
}
