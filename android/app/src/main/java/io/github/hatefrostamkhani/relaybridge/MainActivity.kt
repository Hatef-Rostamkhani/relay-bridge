package io.github.hatefrostamkhani.relaybridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.hatefrostamkhani.relaybridge.cert.CertificateAuthorityManager
import io.github.hatefrostamkhani.relaybridge.vpn.RelayBridgeVpnService

class MainActivity : Activity() {
    private lateinit var configStore: SecureConfigStore
    private lateinit var caManager: CertificateAuthorityManager
    private lateinit var scriptIdInput: EditText
    private lateinit var authKeyInput: EditText
    private lateinit var safeModeRadio: RadioButton
    private lateinit var mitmModeRadio: RadioButton
    private lateinit var vpnButton: Button
    private lateinit var statusText: TextView

    private var vpnStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = SecureConfigStore(this)
        caManager = CertificateAuthorityManager(this)
        requestNotificationPermission()
        buildUi(configStore.load())
    }

    override fun onPause() {
        super.onPause()
        saveConfig()
    }

    @Deprecated("Deprecated by Android, but adequate for this minimal Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_PREPARE && resultCode == RESULT_OK) {
            startVpnService()
        } else if (requestCode == REQUEST_VPN_PREPARE) {
            setStatus("VPN permission was not granted.")
        }
    }

    private fun buildUi(config: AppConfig) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(getColor(R.color.relaybridge_bg))
        }

        root.addView(TextView(this).apply {
            text = "RelayBridge Android MVP"
            textSize = 24f
            setTextColor(getColor(R.color.relaybridge_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "This build prepares the Android VPN shell, stores RelayBridge settings, and exports a local CA. Full packet relay is planned for the next phase."
            textSize = 14f
            setTextColor(getColor(R.color.relaybridge_accent))
            setPadding(0, dp(8), 0, dp(18))
        })

        scriptIdInput = editText("Apps Script deployment ID", config.scriptId, false)
        authKeyInput = editText("Auth key", config.authKey, true)
        root.addView(scriptIdInput)
        root.addView(authKeyInput)

        root.addView(TextView(this).apply {
            text = "Mode"
            textSize = 16f
            setTextColor(getColor(R.color.relaybridge_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })

        val modes = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        safeModeRadio = RadioButton(this).apply {
            text = "Safe Proxy"
            id = ViewId.SAFE_MODE
        }
        mitmModeRadio = RadioButton(this).apply {
            text = "MITM Preview"
            id = ViewId.MITM_MODE
        }
        modes.addView(safeModeRadio)
        modes.addView(mitmModeRadio)
        modes.check(if (config.mode == RelayMode.MITM_PREVIEW) ViewId.MITM_MODE else ViewId.SAFE_MODE)
        root.addView(modes)

        root.addView(button("Prepare VPN") { prepareVpn() })

        vpnButton = button("Start VPN") {
            if (vpnStarted) stopVpnService() else startVpnWithPermissionCheck()
        }
        root.addView(vpnButton)

        root.addView(button("Generate CA") {
            caManager.generateIfMissing()
            setStatus("CA is ready. Android requires manual trust-store installation.")
        })

        root.addView(button("Export CA") {
            exportCertificate()
        })

        root.addView(button("Open Certificate Settings") {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        })

        statusText = TextView(this).apply {
            text = "Ready. Install exported CA manually only on devices you control."
            textSize = 14f
            setTextColor(getColor(R.color.relaybridge_accent))
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(statusText)

        setContentView(ScrollView(this).apply {
            addView(root)
        })
    }

    private fun editText(hintValue: String, textValue: String, secret: Boolean): EditText =
        EditText(this).apply {
            hint = hintValue
            setText(textValue)
            setSingleLine(true)
            textSize = 16f
            setTextColor(getColor(R.color.relaybridge_text))
            setHintTextColor(getColor(R.color.relaybridge_accent))
            inputType = if (secret) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
        }

    private fun button(textValue: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = textValue
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        }

    private fun prepareVpn() {
        saveConfig()
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQUEST_VPN_PREPARE)
        } else {
            setStatus("VPN permission is already granted.")
        }
    }

    private fun startVpnWithPermissionCheck() {
        saveConfig()
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            setStatus("Grant Android VPN permission to start RelayBridge.")
            startActivityForResult(prepareIntent, REQUEST_VPN_PREPARE)
            return
        }
        startVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, RelayBridgeVpnService::class.java)
            .setAction(RelayBridgeVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        vpnStarted = true
        vpnButton.text = "Stop VPN"
        setStatus("VPN shell started. Packet relay is not enabled in this MVP build.")
    }

    private fun stopVpnService() {
        startService(
            Intent(this, RelayBridgeVpnService::class.java)
                .setAction(RelayBridgeVpnService.ACTION_STOP),
        )
        vpnStarted = false
        vpnButton.text = "Start VPN"
        setStatus("VPN stopped.")
    }

    private fun saveConfig() {
        if (!::scriptIdInput.isInitialized || !::authKeyInput.isInitialized) return
        val mode = if (mitmModeRadio.isChecked) RelayMode.MITM_PREVIEW else RelayMode.SAFE_PROXY
        configStore.save(
            AppConfig(
                scriptId = scriptIdInput.text?.toString().orEmpty(),
                authKey = authKeyInput.text?.toString().orEmpty(),
                mode = mode,
            ),
        )
    }

    private fun exportCertificate() {
        val cert = caManager.generateIfMissing()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-x509-ca-cert"
            putExtra(Intent.EXTRA_STREAM, caManager.certificateUri())
            putExtra(Intent.EXTRA_SUBJECT, cert.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, "Export RelayBridge CA"))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun setStatus(value: String) {
        if (::statusText.isInitialized) {
            statusText.text = value
        } else {
            Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object ViewId {
        const val SAFE_MODE = 1001
        const val MITM_MODE = 1002
    }

    companion object {
        private const val REQUEST_VPN_PREPARE = 2101
        private const val REQUEST_NOTIFICATIONS = 2102
    }
}
