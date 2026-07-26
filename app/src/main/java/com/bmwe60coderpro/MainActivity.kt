package com.bmwe60coderpro

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import com.bmwe60coderpro.ui.AppRoot
import com.bmwe60coderpro.ui.MainViewModel

class MainActivity : ComponentActivity() {

    // Hold a direct reference so we can forward input events before Compose inflates
    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this, MainViewModel.factory(application))[MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                AppRoot(vm)
            }
        }
    }

    /**
     * Forward joystick / gamepad axis events to the ViewModel.
     * Android routes MotionEvents from wired USB HID gamepads through this method
     * when the activity has focus and the source is SOURCE_JOYSTICK or SOURCE_GAMEPAD.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD  == InputDevice.SOURCE_GAMEPAD) {
            vm.onControllerMotion(event)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Forward gamepad button key events to the ViewModel.
     * Wired Xbox controllers send KEYCODE_BUTTON_* events here.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {
            if (vm.onControllerKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
