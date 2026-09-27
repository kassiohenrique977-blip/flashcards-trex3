package com.flashcards.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
class PairingStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `code has six digits and survives a new instance`() {
        val code = PairingStore(context).code.value

        assertTrue(code.matches(Regex("\\d{6}")))
        assertEquals(code, PairingStore(context).code.value)
    }

    @Test
    fun `regenerate replaces and publishes the code`() {
        val store = PairingStore(context)
        val old = store.code.value

        val fresh = store.regenerate()

        assertNotEquals(old, fresh)
        assertEquals(fresh, store.code.value)
        assertEquals(fresh, PairingStore(context).code.value)
    }

    @Test
    fun `server id is stable`() {
        val id = PairingStore(context).serverId

        assertTrue(id.startsWith("phone-"))
        assertEquals(id, PairingStore(context).serverId)
    }

    @Test
    fun `small numbers are padded with zeros`() {
        val lowRandom = object : SecureRandom() {
            override fun nextInt(bound: Int): Int = 42
        }

        assertEquals("000042", PairingStore.newCode(lowRandom))
    }
}
