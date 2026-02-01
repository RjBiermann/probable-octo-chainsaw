package com.lagradost.common.cache

import org.junit.Test
import kotlin.test.assertNotNull

class SharedHttpPoolTest {
    @Test
    fun `SharedHttpPool is an object singleton`() {
        assertNotNull(SharedHttpPool)
    }
}
