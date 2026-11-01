package com.github.heroslender.lgtvcontroller.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Wake-on-LAN helpers. A "magic packet" is 6 bytes of 0xFF followed by the target MAC address
 * repeated 16 times, sent as a UDP broadcast.
 */
object WakeOnLan {
    private val WOL_PORTS = intArrayOf(9, 7)

    /**
     * Normalizes a MAC address to the `AA:BB:CC:DD:EE:FF` form.
     * Accepts `:`, `-` or `.` separators, or none at all.
     *
     * @return the normalized address, or null if [input] is not a valid MAC address.
     */
    fun normalizeMac(input: String): String? {
        val hex = input.trim().replace(Regex("[:\\-.]"), "")
        if (!Regex("^[0-9A-Fa-f]{12}$").matches(hex)) {
            return null
        }

        return hex.uppercase().chunked(2).joinToString(":")
    }

    /**
     * @throws IllegalArgumentException if [mac] is not a valid MAC address.
     */
    fun buildMagicPacket(mac: String): ByteArray {
        val normalized = requireNotNull(normalizeMac(mac)) { "Invalid MAC address: $mac" }
        val macBytes = normalized.split(":").map { it.toInt(16).toByte() }.toByteArray()

        val packet = ByteArray(6 + 16 * macBytes.size)
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        for (i in 0 until 16) {
            macBytes.copyInto(packet, destinationOffset = 6 + i * macBytes.size)
        }
        return packet
    }

    /**
     * Sends a magic packet for [mac] to the limited broadcast address and to the broadcast
     * address of every active IPv4 network interface, so it still reaches the TV when the phone
     * is also connected to other networks (mobile data, VPN...).
     *
     * @throws IllegalArgumentException if [mac] is not a valid MAC address.
     * @throws IOException if the packet could not be sent on any address.
     */
    suspend fun send(mac: String) = withContext(Dispatchers.IO) {
        val packet = buildMagicPacket(mac)

        var sent = 0
        var lastError: IOException? = null
        DatagramSocket().use { socket ->
            socket.broadcast = true

            for (address in broadcastAddresses()) {
                for (port in WOL_PORTS) {
                    try {
                        socket.send(DatagramPacket(packet, packet.size, address, port))
                        sent++
                    } catch (e: IOException) {
                        lastError = e
                    }
                }
            }
        }

        if (sent == 0) {
            throw lastError ?: IOException("No network available to send the Wake-on-LAN packet")
        }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))

        try {
            for (iface in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!iface.isUp || iface.isLoopback) continue

                for (ifaceAddress in iface.interfaceAddresses) {
                    // Only IPv4 interfaces have a broadcast address
                    ifaceAddress.broadcast?.let { addresses.add(it) }
                }
            }
        } catch (_: Exception) {
            // Fall back to only the limited broadcast address
        }

        return addresses
    }
}
