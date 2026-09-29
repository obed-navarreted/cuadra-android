package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonationOfferTest {
    private val url = "https://www.paypal.com/donate/?hosted_button_id=ABC"

    @Test fun directWithExternalLinkShows() = assertEquals(url, DonationOffer.from("external_link", url, "direct"))
    @Test fun trimsUrl() = assertEquals(url, DonationOffer.from("external_link", "  $url ", "direct"))
    @Test fun hiddenModeHides() = assertNull(DonationOffer.from("hidden", url, "direct"))
    @Test fun nullOrUnknownModeHides() {
        assertNull(DonationOffer.from(null, url, "direct"))
        assertNull(DonationOffer.from("in_app", url, "direct"))
    }
    @Test fun blankOrNullUrlHides() {
        assertNull(DonationOffer.from("external_link", "", "direct"))
        assertNull(DonationOffer.from("external_link", "   ", "direct"))
        assertNull(DonationOffer.from("external_link", null, "direct"))
    }
    @Test fun httpHides() = assertNull(DonationOffer.from("external_link", "http://www.paypal.com/donate", "direct"))
    @Test fun otherSchemesHide() {
        assertNull(DonationOffer.from("external_link", "javascript:alert(1)", "direct"))
        assertNull(DonationOffer.from("external_link", "paypal.com/donate", "direct"))
        assertNull(DonationOffer.from("external_link", "https://", "direct"))
        assertNull(DonationOffer.from("external_link", "https://a b.com", "direct"))
    }
    @Test fun playBuildHidesEvenWithValidConfig() {
        assertNull(DonationOffer.from("external_link", url, "play"))
        assertNull(DonationOffer.from("external_link", url, "PLAY"))
    }
}
