package com.flashcards.app.data

import com.flashcards.app.data.files.IncomingFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingFilesTest {

    @Test
    fun `a received file is consumed only once`() {
        val files = IncomingFiles()

        files.offer("content://downloads/1")

        assertEquals("content://downloads/1", files.pending.value)
        assertEquals("content://downloads/1", files.consume())
        assertNull(files.consume())
        assertNull(files.pending.value)
    }
}
