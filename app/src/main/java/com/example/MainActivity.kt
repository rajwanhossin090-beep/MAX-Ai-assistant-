package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.screens.MainAssistantScreen
import com.example.ui.screens.PermissionsOnboardingSheet
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MaxViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.initFallbackSessionIfNeeded(applicationContext)

        setContent {
            MyApplicationTheme {
                var showPermissionsSheet by remember { mutableStateOf(false) }
                val permissionsMap by viewModel.permissionsMap.collectAsState()

                MainAssistantScreen(
                    viewModel = viewModel,
                    onOpenPermissionsHub = { showPermissionsSheet = true }
                )

                if (showPermissionsSheet) {
                    PermissionsOnboardingSheet(
                        permissionsMap = permissionsMap,
                        onDismiss = { showPermissionsSheet = false },
                        onPermissionGranted = {
                            viewModel.checkPermissions(this)
                            showPermissionsSheet = false
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions(this)
    }
}

