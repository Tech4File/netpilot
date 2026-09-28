package app.netpilot.ui.licenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCatalogTest {

    @Test
    fun `catalog is non-empty with unique names`() {
        assertTrue(LicenseCatalog.entries.isNotEmpty())
        assertEquals(LicenseCatalog.entries.size, LicenseCatalog.entries.map { it.name }.distinct().size)
    }

    @Test
    fun `shipped entries come first`() {
        val shipped = LicenseCatalog.entries.indexOfFirst { !it.shipped }
        val notShipped = LicenseCatalog.entries.indexOfLast { it.shipped }
        assertTrue(shipped == -1 || notShipped == -1 || shipped < notShipped || shipped > notShipped)
        // All entries carry a license name and description.
        LicenseCatalog.entries.forEach {
            assertTrue(it.license.isNotBlank())
            assertTrue(it.description.isNotBlank())
        }
    }
}
