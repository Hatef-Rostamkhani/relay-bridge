package io.github.hatefrostamkhani.relaybridge

data class AppConfig(
    val scriptId: String = "",
    val authKey: String = "",
    val mode: RelayMode = RelayMode.SAFE_PROXY,
)

enum class RelayMode {
    SAFE_PROXY,
    MITM_PREVIEW;

    companion object {
        fun fromStored(value: String?): RelayMode =
            entries.firstOrNull { it.name == value } ?: SAFE_PROXY
    }
}
