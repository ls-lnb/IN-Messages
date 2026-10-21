package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderSimilarityTest {
    /**
     * A realistic corpus: the senders that matter, plus enough unrelated short codes that common
     * fragments like ATM, BNK and CASH appear often. Without those neighbours every trigram looks
     * rare and the weighting would not be exercised at all.
     */
    private val senders = listOf(
        "TX-SBIBNK",
        "AD-CBSSBI",
        "VD-SBIPG",
        "MX-ATMSBI",
        "VM-SBICGV-S",
        "AD-HDFCBK",
        "JM-HDFCBK-S",
        "VA-HDFCBK",
        "TX-ICICIB",
        "AD-ICICIB",
        "VM-ATMBNK",
        "AX-ATMCASH",
        "AD-CASHBNK",
        "TM-BNKATM",
        "AD-AXISBNK",
        "AX-PAYTMBNK",
        "AD-PAYTMMNY",
        "VM-AMZNIN",
        "AD-FLPKTBNK",
        "AX-MYNTRA",
        "AD-ZOMATO",
        "VM-SWIGGY",
        "AX-UBERIND",
        "AD-OLAONE",
        "TM-IRCTCIN",
        "AD-NETFLIX",
        "VM-SPOTIFY",
        "AX-GOOGLE",
        "AD-FACEBOOK",
        "TM-INSTGRM",
        "AX-WHATSAPP",
        "AD-TELEGRAM",
        "VM-LINKEDIN",
        "AX-TWITTERX",
        "AD-SNAPCHAT",
        "TM-REDDITIN",
        "AX-DISCORD",
        "AD-ZOOMVID",
        "VM-WEBEXME",
        "AX-SLACKHQ",
        "AD-GITHUBIN",
        "TM-GITLABIN",
        "AX-DOCKERIO",
        "AD-AWSINDIA",
        "VM-AZUREIN",
        "AX-GCPCLOUD",
        "AD-DIGIOCEAN",
        "TM-LINODEIN",
        "AX-CLOUDFLR",
        "AD-CPANELWH",
        "VM-NAMECHEAP",
        "AX-GODADDYI",
        "AD-BIGROCKET",
        "TM-HOSTGATOR",
        "AX-BLUEHOST",
        "AD-SITEGROUND",
        "VM-DREAMHOST",
        "AX-A2HOSTING",
        "AD-INMOTIONH",
        "TM-LIQUIDWEB",
        "AX-KINSTA",
        "AD-WPENGINE",
        "VM-FLYWHEEL",
        "AX-PRESSABLE",
        "AD-KINSTAWP",
        "TM-VERCELHQ",
        "AX-NETLIFYIN",
        "AD-HEROKUAPP",
        "VM-RENDERCOM",
        "AX-RAILWAYAPR",
        "AD-SUPABASE",
        "TM-FIREBASE",
        "AX-MONGODB",
        "AD-REDISLAB",
        "VM-ELASTICCO",
        "AX-SNOWFLAKE",
        "AD-DATABRICKS",
        "TM-POSTGRES",
        "AX-MYSQLINE",
        "AD-SQLITEHQ",
        "VM-MARIADBIN",
        "AX-HDFCBNK",
        "AD-ICICBNK",
        "VM-KOTAKBNK",
        "TM-AXISBNK",
        "AX-IDFCBNK",
        "AD-BOBNK",
        "VM-PNBSBNK",
        "TM-CANARBNK",
        "AX-UNIONBNK",
        "AD-YESBNK",
        "VM-INDUSBNK",
        "TM-FEDERALBNK",
        "AX-RBLBNK",
        "AD-SBIBANKATM",
        "VM-ATMCASHIN",
        "TM-CASHWITHD",
        "AX-ATMREVERS",
        "AD-ATMDECLINE",
        "VM-CASHDEPOT",
        "TM-CASHBACKOF",
    )

    private val index = SenderIndex(senders)

    private fun related(query: String): List<SenderMatch> = index.rank(query, senders)

    @Test
    fun `finds senders sharing a distinctive inner run, wherever it sits`() {
        val results = related("TX-SBIBNK")
        val addresses = results.map { it.address }

        listOf("AD-CBSSBI", "VD-SBIPG", "MX-ATMSBI", "VM-SBICGV-S").forEach { address ->
            assertTrue("$address should be related to TX-SBIBNK, got $addresses", address in addresses)
        }
        // and it must not have needed to be told what the shared run is
        assertEquals("SBI", results.first { it.address == "AD-CBSSBI" }.evidence)
    }

    @Test
    fun `ignores case, whitespace and which separator is used`() {
        val baseline = related("TX-SBIBNK").map { it.address }
        listOf("tx-sbibnk", "TX SBIBNK", "  tx_sbibnk  ").forEach { variant ->
            assertEquals("variant `$variant` should rank the same", baseline, related(variant).map { it.address })
        }
    }

    @Test
    fun `separators are boundaries, so no gram straddles one`() {
        // the reason VM-CAMS used to match on 'VMC': that gram was read across the hyphen
        assertEquals(setOf("CAM", "AMS"), SenderIndex.grams("VM-CAMS"))
        assertEquals(listOf("VM", "CAMS"), SenderIndex.segments("VM-CAMS"))
        assertFalse("VMC" in SenderIndex.grams("VM-CAMS"))

        val cams = SenderIndex(listOf("VM-CAMS", "AD-VMCPAY", "AX-CAMSIN", "TM-CASHWITHD"))
        val results = cams.rank("VM-CAMS", listOf("VM-CAMS", "AD-VMCPAY", "AX-CAMSIN", "TM-CASHWITHD"))
        // AD-VMCPAY shares VMC, but only by way of its own letters - it must not be matched here
        assertFalse(
            "AD-VMCPAY must not be related through a straddling gram",
            results.any { it.address == "AD-VMCPAY" }
        )
        assertTrue("AX-CAMSIN shares CAMS", results.any { it.address == "AX-CAMSIN" })
    }

    @Test
    fun `a query typed without separators still finds the family`() {
        val addresses = related("TXSBIBNK").map { it.address }
        listOf("AD-CBSSBI", "VD-SBIPG", "MX-ATMSBI", "VM-SBICGV-S").forEach {
            assertTrue("$it should still be found, got $addresses", it in addresses)
        }
    }

    @Test
    fun `still finds the family when the query has a typo`() {
        val addresses = related("TX-SBINBK").map { it.address }
        assertTrue("a transposed query should still reach SBIPG, got $addresses", "VD-SBIPG" in addresses)
    }

    @Test
    fun `common fragments do not outrank the distinctive one`() {
        val results = related("TX-SBIBNK")
        val sbiBest = results.first { it.address == "AD-CBSSBI" }.score
        val bankOnly = results.firstOrNull { it.address == "AD-AXISBNK" }

        val family = setOf("AD-CBSSBI", "VD-SBIPG", "MX-ATMSBI", "VM-SBICGV-S")
        val worstFamily = results.withIndex().filter { it.value.address in family }.maxOf { it.index }
        val bestNoise = results.withIndex().filter { it.value.evidence == "BNK" }.minOfOrNull { it.index }
        assertTrue(
            "every SBI relative must outrank every BNK-only match, got ${results.map { it.address }}",
            bestNoise == null || worstFamily < bestNoise
        )
        if (bankOnly != null) {
            assertTrue("BNK-only match should score below SBI", bankOnly.score < sbiBest)
        }
        // a sender sharing nothing worthwhile is dropped rather than padded in
        val bankAtm = results.firstOrNull { it.address == "VM-ATMBNK" }
        assertTrue("ATM/BNK noise should be weak or absent", bankAtm == null || bankAtm.score < sbiBest / 2)
    }

    @Test
    fun `more shared evidence ranks higher than less`() {
        val results = related("TX-SBIBNK")
        val withBankCore = results.firstOrNull { it.address == "AD-CBSSBI" }
        val runOnly = results.firstOrNull { it.address == "VD-SBIPG" }
        assertTrue(withBankCore != null && runOnly != null)
        // both share SBI; nothing pathological should put one far above the other
        assertTrue(withBankCore!!.score - runOnly!!.score < 0.5)
    }

    @Test
    fun `exact match comes first`() {
        assertEquals("TX-SBIBNK", related("TX-SBIBNK").first().address)
    }

    @Test
    fun `short queries do not go fuzzy`() {
        val results = related("SB")
        assertTrue("two characters should still find direct substrings, got ${results.size}", results.isNotEmpty())
        assertTrue("but nothing may be inferred from two characters", results.all { it.direct })
    }

    @Test
    fun `blank input is safe`() {
        assertTrue(related("").isEmpty())
        assertTrue(related("   ").isEmpty())
        assertTrue(index.rank("", senders).isEmpty())
    }

    @Test
    fun `unicode and odd separators do not crash`() {
        val results = index.rank("टीएक्स-एसबीआई", senders)
        assertTrue(results.none { it.address.isEmpty() })
    }

    @Test
    fun `unrelated senders are not invented`() {
        val results = related("AD-ZOMATO")
        assertFalse("ZOMATO shares nothing with the SBI family", results.any { it.address == "AD-CBSSBI" })
    }

    @Test
    fun `ranking stays fast on a realistic dataset`() {
        val many = (1..3000).map { "AX-${(it * 37 % 99991).toString().padStart(5, '0')}BNK" } + senders
        val bigIndex = SenderIndex(many)
        val started = System.nanoTime()
        repeat(20) { bigIndex.rank("TX-SBIBNK", many) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("20 rankings over ${many.size} senders took ${elapsedMs}ms", elapsedMs < 2000)
    }


    @Test
    fun `works for an unrelated domain with no bank involved`() {
        // same algorithm, different vocabulary: nothing here knows about banks or HDFC
        val delivery = listOf(
            "AD-SWIGGYIN", "VM-SWIGGYOFF", "AX-SWIGGYHYD", "TM-SWIGGYDEL",
            "AD-ZOMATOIN", "VM-ZOMATOGLD", "AX-ZOMATOPRO",
            "AD-PAYTMBNK", "AX-PAYTMUPI", "VM-PAYTMWLT",
            "AD-AMZNIND", "VM-AMZNPRIM", "AX-AMZNFRSH",
            "AD-FLPKTBNK", "VM-FLPKTBIG",
            "AX-GOOGLEIN", "AD-FACEBOOK", "VM-WHATSAPP", "AX-INSTGRM",
            "AD-NETFLIX", "VM-SPOTIFY", "AX-HOTSTAR", "AD-JIOSAAVN",
            "TM-UBERIND", "AX-OLACABS", "VM-RAPIDOIN", "AD-PORTERIN",
        )
        val deliveryIndex = SenderIndex(delivery)

        val swiggy = deliveryIndex.rank("AX-SWIGGYHYD", delivery).map { it.address }
        listOf("AD-SWIGGYIN", "VM-SWIGGYOFF", "TM-SWIGGYDEL").forEach {
            assertTrue("$it should be related to AX-SWIGGYHYD, got $swiggy", it in swiggy)
        }
        assertFalse("Zomato must not be pulled in", swiggy.any { it.contains("ZOMATO") })

        val paytm = deliveryIndex.rank("AX-PAYTMUPI", delivery).map { it.address }
        assertTrue("AD-PAYTMBNK should be related to AX-PAYTMUPI, got $paytm", "AD-PAYTMBNK" in paytm)
        assertFalse("Amazon must not be pulled in", paytm.any { it.contains("AMZN") })
    }

    @Test
    fun `normalization keeps the comparison form stable`() {
        assertEquals("TXSBIBNK", SenderIndex.normalize("tx-sbi bnk"))
        assertEquals("", SenderIndex.normalize("   "))
        assertEquals(setOf("TXS", "XSB", "SBI", "BIB", "IBN", "BNK"), SenderIndex.grams("TXSBIBNK"))
        assertTrue(SenderIndex.grams("TX").isEmpty())
    }

    @Test
    fun `longest common substring is contiguous`() {
        assertEquals("SBI", SenderIndex.longestCommonSubstring("TXSBIBNK", "ADCBSSBI"))
        assertEquals("", SenderIndex.longestCommonSubstring("ABC", "XYZ"))
    }
}
