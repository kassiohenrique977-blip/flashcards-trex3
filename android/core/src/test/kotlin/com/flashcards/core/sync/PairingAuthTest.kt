package com.flashcards.core.sync

import com.flashcards.core.util.Clock
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingAuthTest {

    private var now = 0L
    private val clock = Clock { now }
    private var code = "123456"
    private val auth = PairingAuth({ code }, clock, maxFailures = 3, windowMs = 60_000)

    @Test
    fun `right code passes and surrounding spaces are ignored`() {
        assertEquals(AuthResult.OK, auth.check("123456"))
        assertEquals(AuthResult.OK, auth.check(" 123456 "))
    }

    @Test
    fun `wrong or missing code is denied`() {
        assertEquals(AuthResult.DENIED, auth.check("654321"))
        assertEquals(AuthResult.DENIED, auth.check(null))
        assertEquals(AuthResult.DENIED, auth.check(""))
    }

    @Test
    fun `empty expected code never matches`() {
        code = ""
        assertEquals(AuthResult.DENIED, auth.check(""))
    }

    @Test
    fun `too many failures lock even the right code until the window passes`() {
        repeat(3) { auth.check("000000") }

        assertEquals(AuthResult.LOCKED, auth.check("123456"))

        now += 60_000
        assertEquals(AuthResult.OK, auth.check("123456"))
    }

    @Test
    fun `successful checks do not count as failures`() {
        repeat(10) { auth.check("123456") }
        auth.check("000000")
        auth.check("000000")

        assertEquals(AuthResult.OK, auth.check("123456"))
    }
}
