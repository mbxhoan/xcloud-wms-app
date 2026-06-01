package vn.delfi.xcloudwms.core.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {

    @Test
    fun `drops duplicate within interval, accepts after interval`() {
        val debouncer = ScanDebouncer()
        val config = ScanDebounceConfig(intervalMs = 800L)

        assertTrue(debouncer.shouldAccept("A", now = 0L, config = config))
        assertFalse(debouncer.shouldAccept("A", now = 200L, config = config))
        assertTrue(debouncer.shouldAccept("A", now = 900L, config = config))
    }

    @Test
    fun `accepts a different code immediately`() {
        val debouncer = ScanDebouncer()
        val config = ScanDebounceConfig()

        assertTrue(debouncer.shouldAccept("A", now = 0L, config = config))
        assertTrue(debouncer.shouldAccept("B", now = 10L, config = config))
    }

    @Test
    fun `continuous serial bypasses debounce`() {
        val debouncer = ScanDebouncer()
        val config = ScanDebounceConfig(continuousSerial = true)

        assertTrue(debouncer.shouldAccept("A", now = 0L, config = config))
        assertTrue(debouncer.shouldAccept("A", now = 1L, config = config))
        assertTrue(debouncer.shouldAccept("A", now = 2L, config = config))
    }

    @Test
    fun `reset clears duplicate history`() {
        val debouncer = ScanDebouncer()
        val config = ScanDebounceConfig()

        assertTrue(debouncer.shouldAccept("A", now = 0L, config = config))
        debouncer.reset()
        assertTrue(debouncer.shouldAccept("A", now = 10L, config = config))
    }
}
