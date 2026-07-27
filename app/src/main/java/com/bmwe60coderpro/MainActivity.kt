package com.bmwe60coderpro

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bmwe60coderpro.ui.AppRoot
import com.bmwe60coderpro.ui.MainViewModel

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel(application)
        handleUsbIntent(intent)
        setContent {
            AppRoot(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Log.d(TAG, "USB attached: ${device?.deviceName}")
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d(TAG, "USB detached")
            }
        }
    }
}
