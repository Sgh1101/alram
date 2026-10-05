package com.alram.mail

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.data.ThemeMode
import com.alram.mail.ui.AlramNav
import com.alram.mail.ui.ProvideContainer
import com.alram.mail.ui.theme.AlramTheme
import com.alram.mail.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AlramApp).container
        container.dispatcher.poke()
        setContent {
            val prefs by container.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
            val mode = prefs?.theme ?: ThemeMode.SYSTEM
            val dark = isAppInDarkTheme(mode)
            // 앱 안에서 테마를 강제로 바꿨을 때도 상태바 아이콘 색이 배경과 맞도록 한다.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            ProvideContainer(container) {
                AlramTheme(mode) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        prefs?.let { AlramNav(it) }
                    }
                }
            }
        }
    }
}
