package com.fisica2.campomagnetico

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fisica2.campomagnetico.ui.AppTheme
import com.fisica2.campomagnetico.ui.MainScreen
import com.fisica2.campomagnetico.ui.MainViewModel

/** Única Activity de la app (y único componente exportado: es el launcher). */
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { MainScreen(vm) }
        }
    }

    // Los sensores solo se leen con la app en primer plano.
    override fun onResume() {
        super.onResume()
        vm.startSensors()
    }

    override fun onPause() {
        vm.stopSensors()
        super.onPause()
    }
}
