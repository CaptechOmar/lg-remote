package com.github.heroslender.lgtvcontroller

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.heroslender.lgtvcontroller.ui.theme.LGTVControllerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLocalNetworkAccess()
        setContent {
            LGTVControllerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControllerApp()
                }
            }
        }
    }

    // Only exists (and only asks) on Android versions that guard the local network; elsewhere the
    // request is simply refused without showing anything.
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < 36) return
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            runCatching { localNetworkPermission.launch(permission) }
        }
    }

    override fun onResume() {
        super.onResume()

        deviceManager.resume()
    }

    override fun onPause() {
        super.onPause()

        deviceManager.pause()
    }
}

