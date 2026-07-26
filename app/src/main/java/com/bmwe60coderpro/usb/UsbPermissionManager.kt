package com.bmwe60coderpro.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ACTION_USB_PERMISSION = "com.bmwe60coderpro.USB_PERMISSION"

class UsbPermissionManager(private val context: Context) {
    suspend fun ensurePermission(usbManager: UsbManager, device: UsbDevice): Boolean = withContext(Dispatchers.Main) {
        if (usbManager.hasPermission(device)) return@withContext true

        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!result.isCompleted) result.complete(granted)
                    runCatching { context.unregisterReceiver(this) }
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags,
        )
        usbManager.requestPermission(device, permissionIntent)
        result.await()
    }
}
