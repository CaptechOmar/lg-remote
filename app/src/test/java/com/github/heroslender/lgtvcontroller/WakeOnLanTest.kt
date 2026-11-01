package com.github.heroslender.lgtvcontroller

import com.github.heroslender.lgtvcontroller.utils.WakeOnLan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanTest {
    @Test
    fun normalizeMac_acceptsCommonFormats() {
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.normalizeMac("aa:bb:cc:dd:ee:ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.normalizeMac("AA-BB-CC-DD-EE-FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.normalizeMac("aabb.ccdd.eeff"))
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.normalizeMac("  AABBCCDDEEFF "))
    }

    @Test
    fun normalizeMac_rejectsInvalidInput() {
        assertNull(WakeOnLan.normalizeMac(""))
        assertNull(WakeOnLan.normalizeMac("AA:BB:CC:DD:EE"))
        assertNull(WakeOnLan.normalizeMac("AA:BB:CC:DD:EE:FF:00"))
        assertNull(WakeOnLan.normalizeMac("GG:BB:CC:DD:EE:FF"))
    }

    @Test
    fun buildMagicPacket_hasHeaderAndSixteenMacRepetitions() {
        val packet = WakeOnLan.buildMagicPacket("01:23:45:67:89:AB")
        val mac = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte())

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (i in 0 until 16) {
            assertArrayEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildMagicPacket_rejectsInvalidMac() {
        WakeOnLan.buildMagicPacket("not-a-mac")
    }
}
