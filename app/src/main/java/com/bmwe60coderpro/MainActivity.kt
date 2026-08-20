package com.bmwe60coderpro

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
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

    /** Forward gamepad/joystick motion events to the ViewModel so live axes update. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        viewModel.onControllerMotion(event)
        return super.dispatchGenericMotionEvent(event)
    }

    /** Forward gamepad button events (A, B, RB, LB, etc.) to the ViewModel. */
    @Suppress("RestrictedApi") // Required to preserve normal Activity event dispatch after controller handling.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.onControllerKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun handleUsbIntent(intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                Log.d(TAG, "USB attached: ${device?.deviceName}")
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d(TAG, "USB detached")
            }
        }
    }
}