package com.inversioncoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inversioncoach.app.ui.navigation.AppNavHost
import com.inversioncoach.app.ui.theme.InversionCoachTheme

class MainActivity : ComponentActivity() {
    private var launchSessionId: Long? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSessionId = intent?.extras?.getLong("upload_session_id")?.takeIf { it > 0L }
        setContent {
            InversionCoachTheme {
                AppNavHost(initialSessionId = launchSessionId)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        launchSessionId = intent.extras?.getLong("upload_session_id")?.takeIf { it > 0L }
        setIntent(intent)
    }
}
