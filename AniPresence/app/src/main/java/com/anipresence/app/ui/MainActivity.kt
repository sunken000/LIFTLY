package com.anipresence.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            AniPresenceTheme {
                MainScreen(
                    state = state,
                    onToggleDetection = viewModel::toggleDetection,
                    onGrantAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onConfigureWebhook = viewModel::configureWebhook,
                    onTestWebhook = viewModel::testWebhook,
                    onSaveCorrection = viewModel::saveCorrection,
                    onConfirmPublish = viewModel::confirmAndPublish,
                    onSimulate = viewModel::simulate,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setNotificationAccess(
            packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        )
    }
}
