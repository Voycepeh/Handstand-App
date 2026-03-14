package com.inversioncoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inversioncoach.app.ui.navigation.AppNavHost
import com.inversioncoach.app.ui.theme.InversionCoachTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InversionCoachTheme {
                AppNavHost()
            }
        }
    }
}
