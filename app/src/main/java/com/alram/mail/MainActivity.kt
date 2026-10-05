package com.alram.mail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.data.ThemeMode
import com.alram.mail.ui.AlramNav
import com.alram.mail.ui.ProvideContainer
import com.alram.mail.ui.theme.AlramTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AlramApp).container
        container.dispatcher.poke()
        setContent {
            val prefs by container.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
            ProvideContainer(container) {
                AlramTheme(prefs?.theme ?: ThemeMode.SYSTEM) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        prefs?.let { AlramNav(it) }
                    }
                }
            }
        }
    }
}
