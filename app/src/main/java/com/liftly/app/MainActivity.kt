package com.liftly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.LiftlyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as LiftlyApplication
            val vm: AppViewModel = viewModel(factory = AppViewModel.factory(app))
            LiftlyApp(vm)
        }
    }
}
