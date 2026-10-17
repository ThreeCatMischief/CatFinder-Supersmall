package com.catfinder.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catfinder.app.ui.AppViewModel
import com.catfinder.app.ui.DriveScreen
import com.catfinder.app.ui.Screen
import com.catfinder.app.ui.SettingsScreen
import com.catfinder.app.ui.theme.CatFinderTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { CatFinderTheme { CatFinderApp(vm) } }
    }

    // Leaving the app stops the robot and the stream; see AppViewModel.updateDriving.
    override fun onStart() {
        super.onStart()
        vm.onStart()
    }

    override fun onStop() {
        vm.onStop()
        super.onStop()
    }
}

@Composable
private fun CatFinderApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    when (screen) {
        Screen.DRIVE -> DriveScreen(vm)
        Screen.SETTINGS -> {
            BackHandler { vm.show(Screen.DRIVE) }
            SettingsScreen(vm)
        }
    }
}
