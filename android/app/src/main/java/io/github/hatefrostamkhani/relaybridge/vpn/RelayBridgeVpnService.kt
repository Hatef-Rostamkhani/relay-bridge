package io.github.hatefrostamkhani.relaybridge.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.hatefrostamkhani.relaybridge.MainActivity
import io.github.hatefrostamkhani.relaybridge.R
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class RelayBridgeVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRelayBridgeVpn()
                return START_NOT_STICKY
            }
            else -> startRelayBridgeVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }

    private fun startRelayBridgeVpn() {
        if (running.get()) return
        createNotificationChannel()
        startForegroundCompat(buildNotification())

        val builder = Builder()
            .setSession("RelayBridge MVP")
            .setMtu(1500)
            .addAddress("10.111.0.2", 32)

        if (EXPERIMENTAL_CAPTURE_ALL_TRAFFIC) {
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("1.1.1.1")
        } else {
            builder.addRoute("203.0.113.0", 24)
        }

        vpnInterface = builder.establish()
        running.set(true)
        packetThread = Thread(::packetLoop, "RelayBridgeVpnPacketLoop").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopRelayBridgeVpn() {
        closeTunnel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun closeTunnel() {
        running.set(false)
        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
        packetThread = null
    }

    private fun packetLoop() {
        val descriptor = vpnInterface?.fileDescriptor ?: return
        val buffer = ByteArray(32767)
        try {
            FileInputStream(descriptor).use { input ->
                while (running.get()) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    // MVP behavior: the tunnel shell is established, but packets
                    // are not forwarded to Apps Script until the data-plane phase.
                }
            }
        } catch (_: IOException) {
            running.set(false)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("RelayBridge VPN")
            .setContentText("Android MVP tunnel shell is running")
            .setSmallIcon(R.drawable.ic_stat_relaybridge)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "io.github.hatefrostamkhani.relaybridge.START_VPN"
        const val ACTION_STOP = "io.github.hatefrostamkhani.relaybridge.STOP_VPN"

        private const val CHANNEL_ID = "relaybridge_vpn"
        private const val NOTIFICATION_ID = 4101

        // Keep false for MVP so installing the debug APK cannot capture all
        // device traffic before the packet forwarding data-plane exists.
        private const val EXPERIMENTAL_CAPTURE_ALL_TRAFFIC = false
    }
}
