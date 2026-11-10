package com.github.heroslender.lgtvcontroller.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Checks whether a webOS TV is reachable at a known address by opening a TCP connection to its
 * control ports. A TV in standby doesn't accept connections, so this is a reliable "is it on?".
 */
object TvProbe {
    /** webOS secure (3001) and plain (3000) control ports. */
    private val PORTS = intArrayOf(3001, 3000)

    suspend fun isOnline(ip: String, timeoutMs: Int = 700): Boolean = withContext(Dispatchers.IO) {
        PORTS.any { port ->
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")

    fun isValidIpv4(input: String) = IPV4.matches(input.trim())
}
