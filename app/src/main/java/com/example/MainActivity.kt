package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.OpenFeedApp
import com.example.ui.OpenFeedViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: OpenFeedViewModel by viewModels {
        val container = (application as OpenFeedApplication).container
        OpenFeedViewModel.Factory(
            repository = container.repository,
            preferencesRepository = container.preferencesRepository,
            networkClient = container.networkClient
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.handleIncomingIntent(intent)
        }
        setContent {
            OpenFeedApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }
}
