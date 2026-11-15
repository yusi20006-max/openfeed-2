package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.OpenFeedApp
import com.example.ui.OpenFeedViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenFeedApplication).container
        setContent {
            val viewModel: OpenFeedViewModel = viewModel(
                factory = OpenFeedViewModel.Factory(
                    repository = container.repository,
                    preferencesRepository = container.preferencesRepository,
                    networkClient = container.networkClient
                )
            )
            OpenFeedApp(viewModel = viewModel)
        }
    }
}
