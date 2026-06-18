package dev.bleu.locallink.data.model

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val type: DeviceType = DeviceType.PHONE
)

enum class DeviceType { PHONE, LAPTOP, TABLET, DESKTOP }
