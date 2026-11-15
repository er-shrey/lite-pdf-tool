package com.shrey.litepdf

import com.google.common.truth.Truth.assertThat
import com.shrey.litepdf.core.pdfgen.PageLayout
import com.shrey.litepdf.core.pdfgen.PageSize
import org.junit.Test

class PageLayoutTest {
    private val tol = 0.01

    @Test fun originalAt96Dpi() {
        // PRD example: 1200 x 1800 px at 96 DPI -> 900 x 1350 pt.
        val s = PageLayout.layout(1200, 1800, 96.0, 96.0, PageSize.ORIGINAL)
        assertThat(s.width).isWithin(tol).of(900.0)
        assertThat(s.height).isWithin(tol).of(1350.0)
        assertThat(s.image.x).isEqualTo(0.0)
        assertThat(s.image.width).isWithin(tol).of(900.0)
    }

    @Test fun usesMetadataDpi() {
        val s = PageLayout.layout(2480, 3508, 300.0, 300.0, PageSize.ORIGINAL)
        assertThat(s.width).isWithin(0.1).of(595.2)
        assertThat(s.height).isWithin(0.1).of(841.9)
    }

    @Test fun implausibleDpiFallsBack() {
        assertThat(PageLayout.effectiveDpi(null)).isEqualTo(96.0)
        assertThat(PageLayout.effectiveDpi(1.0)).isEqualTo(96.0)
        assertThat(PageLayout.effectiveDpi(72.0)).isEqualTo(72.0)
    }

    @Test fun a4PortraitFitsAndCentres() {
        val s = PageLayout.layout(3000, 4000, 96.0, 96.0, PageSize.A4)
        assertThat(s.width).isEqualTo(PageLayout.A4_WIDTH)
        assertThat(s.height).isEqualTo(PageLayout.A4_HEIGHT)
        val r = s.image
        // Aspect ratio kept.
        assertThat(r.width / r.height).isWithin(1e-9).of(3000.0 / 4000.0)
        // Inside the margins, never cropped.
        assertThat(r.x).isAtLeast(PageLayout.A4_MARGIN - tol)
        assertThat(r.y).isAtLeast(PageLayout.A4_MARGIN - tol)
        assertThat(r.x + r.width).isAtMost(s.width - PageLayout.A4_MARGIN + tol)
        assertThat(r.y + r.height).isAtMost(s.height - PageLayout.A4_MARGIN + tol)
        // Centred.
        assertThat(r.x).isWithin(tol).of(s.width - r.x - r.width)
        assertThat(r.y).isWithin(tol).of(s.height - r.y - r.height)
    }

    @Test fun a4LandscapeForWideImages() {
        val s = PageLayout.layout(4000, 3000, 96.0, 96.0, PageSize.A4)
        assertThat(s.width).isEqualTo(PageLayout.A4_HEIGHT)
        assertThat(s.height).isEqualTo(PageLayout.A4_WIDTH)
    }

    @Test fun a4DoesNotUpscaleSmallImages() {
        val s = PageLayout.layout(200, 100, 96.0, 96.0, PageSize.A4)
        assertThat(s.image.width).isWithin(tol).of(150.0)
        assertThat(s.image.height).isWithin(tol).of(75.0)
    }
}
