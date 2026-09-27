package com.aipurchase.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmerSearchTest {
    private val fields=listOf("John Koffi","+225 07 10 20 30","F000001","Abengourou","")

    @Test fun singleCharacterMatchesFarmerValues() {
        assertTrue(matchesFarmerSearch("j",fields))
        assertTrue(matchesFarmerSearch("A",fields))
    }

    @Test fun singleCharacterDoesNotMatchJsonFieldNames() {
        assertFalse(matchesFarmerSearch("x",fields))
    }

    @Test fun blankQueryShowsAllFarmers() {
        assertTrue(matchesFarmerSearch(" ",fields))
    }
}
