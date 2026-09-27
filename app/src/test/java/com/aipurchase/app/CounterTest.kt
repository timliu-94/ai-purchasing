package com.aipurchase.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterTest {
    @Test fun incrementsFromZero() {
        val counter = Counter()
        assertEquals(0, counter.value)
        assertEquals(1, counter.increment())
        assertEquals(2, counter.increment())
    }
    @Test fun continuesFromRestoredValue() {
        assertEquals(8, Counter(7).increment())
    }
}
