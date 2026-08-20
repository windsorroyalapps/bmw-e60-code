package com.bmwe60coderpro.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "UsbPermissionManager"
private const val ACTION_USB_PERMISSION = "com.bmwe60coderpro.USB_PERMISSION"

class UsbPermissionManager(private val context: Context) {

    suspend fun ensurePermission(usbManager: UsbManager, device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Already has permission for ${device.deviceName}")
            return true
        }
        Log.d(TAG, "Requesting permission for ${device.deviceName}")
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.d(TAG, "Permission result for ${device.deviceName}: $granted")
                deferred.complete(granted)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context, device.deviceId, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            usbManager.requestPermission(device, pendingIntent)
            return withTimeoutOrNull(30_000) { deferred.await() } ?: run {
                Log.w(TAG, "Permission timeout for ${device.deviceName}")
                false
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
