package com.cocode.claudeemailapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cocode.claudeemailapp.app.ClaudeEmailApp
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeEmailAppTheme {
                ClaudeEmailApp()
            }
        }
    }
}
