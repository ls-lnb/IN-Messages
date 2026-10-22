package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {

    @Test
    fun extractsCodesFromCommonFormats() {
        assertEquals("12345", "Your OTP id 12345".extractOtpCode())
        assertEquals("1234", "1234 is your XYZ verification code".extractOtpCode())
        assertEquals("123456", "123456 is the One Time Password".extractOtpCode())
        assertEquals("123456", "123456 is (the) One Time Password".extractOtpCode())
        assertEquals("123456", "Your OTP is 123456".extractOtpCode())
        assertEquals("123456", "Testing. Your OTP is 123456".extractOtpCode())
        assertEquals("987654", "OTP: 987654".extractOtpCode())
        assertEquals("456789", "Use 456789 as OTP".extractOtpCode())
        assertEquals("4321", "Your verification code is 4321".extractOtpCode())
        assertEquals("111222", "Do not share this OTP with anyone. OTP is 111222".extractOtpCode())
        assertEquals("778899", "Your code is 778899".extractOtpCode())
        assertEquals("112233", "one-time password: 112233".extractOtpCode())
        assertEquals("1234", "OTP is 1234".extractOtpCode())
        assertEquals("12345678", "OTP 12345678".extractOtpCode())
    }

    @Test
    fun prefersTheCodeOverAmountsInTheSameMessage() {
        assertEquals("123456", "123456 is your OTP for txn of Rs.5000".extractOtpCode())
        assertEquals("123456", "Your OTP is 123456. Amount Rs.5000 will be debited.".extractOtpCode())
        assertEquals("123456", "Rs.5000 debited. OTP 123456".extractOtpCode())
        assertEquals("998877", "Use OTP 998877 to complete your payment of Rs 25000".extractOtpCode())
    }

    @Test
    fun handlesLongerSentences() {
        assertEquals(
            "246810",
            "Dear Customer, OTP for your transaction is 246810. Do not share.".extractOtpCode()
        )
        assertEquals("135790", "Your banking OTP is 135790, valid 3 mins".extractOtpCode())
        assertEquals("654321", "Your one time password (OTP) is 654321".extractOtpCode())
    }

    @Test
    fun handlesBankTransactionMessages() {
        val icici = "099514 is One-Time Password for INR 262.07 transaction towards AMAZON " +
            "using ICICI Bank Credit Card4. OTPs are SECRET. DO NOT disclose"
        assertEquals("099514", icici.extractOtpCode())

        val hdfc = "Your OTP for txn of Rs.5000 is 123456. Valid for 10 mins."
        assertEquals("123456", hdfc.extractOtpCode())
    }

    @Test
    fun keepsLeadingZerosInCodes() {
        assertEquals("012345", "Your OTP is 012345".extractOtpCode())
        assertEquals("0099", "0099 is your verification code".extractOtpCode())
    }

    @Test
    fun handlesMasksAndBankSpecificPhrases() {
        val icici = "099524 is One-Time Password for INR 2693.07 transaction towards AMAZON using " +
            "ICICI Bank Credit Card XX1001. OTPs are SECRET. DO NOT disclose"
        assertEquals("099524", icici.extractOtpCode())

        assertEquals("44821877", "Citi Identification Code Service\n44821877".extractOtpCode())
        assertEquals("234567", "CDSL-Your Generated TPIN code is 234567".extractOtpCode())

        // masked card digits must never win over the code, even when they sit closer to "OTP"
        assertEquals("123456", "Your OTP is 123456 for card XX1001".extractOtpCode())
        assertEquals("445566", "OTP for your ICICI Bank Credit Card XX1001 txn of Rs.500 is 445566".extractOtpCode())
        assertEquals("778899", "OTP is 778899. Card ending *4321 was used.".extractOtpCode())
    }

    @Test
    fun detectsAlphanumericCodes() {
        assertEquals("2MB8G", "Use code 2MB8G to change credentials for your Steam account".extractOtpCode())
        assertEquals("2mb8g", "Your code is 2mb8g".extractOtpCode())
        assertEquals("4F7K2", "Your Uber verification code is 4F7K2".extractOtpCode())
        assertEquals("AB12CD", "OTP: AB12CD".extractOtpCode())
        assertEquals("8KJ2M4", "OTP for your Netflix account is 8KJ2M4".extractOtpCode())
        assertEquals("AB12", "Your OTP is AB12".extractOtpCode())
    }

    @Test
    fun alphanumericCodesNeedTwoDigitsTwoLettersAndFourToTenChars() {
        // too short, no digits, one digit, or too long - none of these are codes
        assertNull("Your OTP is A1B".extractOtpCode())
        assertNull("Your OTP is ABCDEF".extractOtpCode())
        assertNull("Your OTP is ABC1".extractOtpCode())
        assertNull("Your OTP is AB1CD".extractOtpCode())
        assertNull("Your OTP is 12AB34CD567".extractOtpCode())

        // the boundaries themselves are accepted
        assertEquals("AB12", "Your OTP is AB12".extractOtpCode())
        assertEquals("AB12C", "Your OTP is AB12C".extractOtpCode())
        assertEquals("12AB34CD56", "Your OTP is 12AB34CD56".extractOtpCode())
    }

    @Test
    fun wordsAndAmountsAreNotCodes() {
        assertNull("OTP for your AMAZON transaction of Rs.500".extractOtpCode())
        assertNull("Your OTP for ZOMATO is VALID for 10 mins".extractOtpCode())
        assertNull("OTP 2FA required for your ICICI account".extractOtpCode())
        assertNull("Rs5000 debited. Never share the OTP.".extractOtpCode())
        assertNull("INR2500 debited from A/c XX1234. Do not share your OTP.".extractOtpCode())
        assertNull("Your OTP was sent to card XX1234. Do not share.".extractOtpCode())
    }

    @Test
    fun digitsWinWhenAMixedReferenceSitsCloser() {
        assertEquals("998877", "Your OTP for order 12AB34CD is 998877".extractOtpCode())
        assertEquals("445566", "OTP for txn AB12CD34 of Rs.500 is 445566".extractOtpCode())
    }

    @Test
    fun skipsNumbersThatAreMoneyOrDates() {
        val credit = "Your SB A/c *0000 Credited for Rs:1500.00 on 11-09-2026 12:04:11 by IMPS ref no " +
            "000000000000 Avl Bal Rs:4364.46.Never Share OTP/PIN/CVV-Union Bank of India"
        assertNull(credit.extractOtpCode())

        val mobBk = "A/c *0000 Credited for Rs:60.00 on 28-08-2026 11:09:48 by Mob Bk ref no 000000000000 " +
            "Avl Bal Rs:4364.46.Never Share OTP/PIN/CVV-Union Bank of India"
        assertNull(mobBk.extractOtpCode())

        assertNull("Never share this OTP. Amount Rs.5000 debited from your account.".extractOtpCode())
        assertNull("Your OTP was sent on 11-09-2026 to your registered number.".extractOtpCode())
        assertEquals("123456", "Your OTP is 123456 on 11-09-2026. Amount Rs.5000 debited.".extractOtpCode())
    }

    @Test
    fun findsCodesFarFromTheKeyword() {
        val amex = "Your Amex SafeKey One-Time Password for INR 5,000.00, at Jewellers India Priva " +
            "is 873074. Valid for 10 mins. Do not disclose it to anyone"
        assertEquals("873074", amex.extractOtpCode())
        assertEquals("445566", "Your OTP for INR 5000 is 445566".extractOtpCode())
        assertEquals("778899", "OTP for your transaction of Rs 12500 is 778899".extractOtpCode())
    }

    @Test
    fun handlesDashAndSpaceVariants() {
        assertEquals("123456", "123456 is the One\u2013Time Password".extractOtpCode())
        assertEquals("123456", "One\u2014Time Password: 123456".extractOtpCode())
        assertEquals("123456", "Your OTP\u00A0is 123456".extractOtpCode())
        assertEquals("099514", "099514 is One\u2013Time Password for INR 262.07 transaction".extractOtpCode())
    }

    @Test
    fun usesUserFormats() {
        val custom = listOf("secret word")
        assertEquals("998877", "Your secret word is 998877".extractOtpCode(custom))
        assertNull("Your secret word is 998877".extractOtpCode())
        assertEquals("123456", "Your secret-word is 123456".extractOtpCode(custom))
        assertEquals("445566", "Your SECRET word: 445566".extractOtpCode(custom))
        assertEquals("665544", "665544 is your secret word".extractOtpCode(custom))
    }

    @Test
    fun userFormatsAreLiteralNotRegex() {
        assertNull("Your a+b is 123456".extractOtpCode(listOf("a b")))
        assertEquals("123456", "Your a b is 123456".extractOtpCode(listOf("a b")))
        assertNull("Your anything is 123456".extractOtpCode(listOf("a.*b")))
    }

    @Test
    fun ignoresMessagesWithoutCodes() {
        assertNull("Your account 1234567890 is debited".extractOtpCode())
        assertNull("Rs.5000 debited from A/c XX1234 on 12-10-26".extractOtpCode())
        assertNull("Your order 12345 has shipped".extractOtpCode())
        assertNull("PIN code 560001".extractOtpCode())
        assertNull("Coupon code 123456".extractOtpCode())
        assertNull("Referral code 12345 for discount".extractOtpCode())
        assertNull("Your OTP verification failed".extractOtpCode())
        assertNull("Your package will arrive in 12345 minutes".extractOtpCode())
        assertNull("Call us on 1800123456 for support".extractOtpCode())
    }

    @Test
    fun requiresDigitBoundaries() {
        assertNull("OTP 12345678901".extractOtpCode())
        assertNull("Your OTP is 123".extractOtpCode())
    }

    @Test
    fun `detects a bare PIN phrase`() {
        assertEquals("6543", "PIN is 6543 . Test.".extractOtpCode())
        assertEquals("65432", "PIN is 65432. Test.".extractOtpCode())
        assertEquals("654321", "PIN is 654321. Test.".extractOtpCode())
        assertEquals("654321", "PIN is 654321 . Test.".extractOtpCode())
        assertEquals("4821", "Your PIN: 4821".extractOtpCode())
    }

    @Test
    fun `does not treat a postal PIN code as a password`() {
        assertNull("PIN code 400001".extractOtpCode())
        assertNull("Pincode: 400001".extractOtpCode())
        assertNull("Postal PIN code 400001".extractOtpCode())
    }
}
