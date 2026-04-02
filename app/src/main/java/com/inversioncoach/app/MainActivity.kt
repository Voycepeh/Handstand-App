package com.inversioncoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inversioncoach.app.ui.navigation.AppNavHost
import com.inversioncoach.app.ui.theme.InversionCoachTheme

class MainActivity : ComponentActivity() {
    private var pendingOpenSessionId: Long? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenSessionId = intent?.getLongExtra("openSessionId", -1L)?.takeIf { it > 0L }
        setContent {
            InversionCoachTheme {
                AppNavHost(openSessionId = pendingOpenSessionId)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenSessionId = intent.getLongExtra("openSessionId", -1L).takeIf { it > 0L }
    }
}
