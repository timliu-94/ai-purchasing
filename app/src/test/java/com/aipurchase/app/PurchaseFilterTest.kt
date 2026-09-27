package com.aipurchase.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseFilterTest {
    private val fields=listOf("PUR-20260901-0001","CONFIRMED","clean beans","John Koffi","F000001","Cocoa")

    @Test fun searchesAcrossPurchaseFarmerAndProductFields() {
        assertTrue(match("koffi"))
        assertTrue(match("cocoa"))
        assertTrue(match("0001"))
        assertFalse(match("coffee"))
    }

    @Test fun filtersByInclusiveDateFarmerAndProduct() {
        assertTrue(match("",from="2026-09-01",to="2026-09-01",farmer=1,product=2))
        assertFalse(match("",from="2026-09-02"))
        assertFalse(match("",farmer=9))
        assertFalse(match("",product=9))
    }

    private fun match(query:String,from:String="",to:String="",farmer:Int?=null,product:Int?=null)=
        matchesPurchaseFilters(query,from,to,farmer,product,"2026-09-01",1,2,fields)
}
