package app.netpilot.core.dns

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.netpilot.core.model.DnsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DnsProfileRepositoryTest {

    private lateinit var repo: DnsProfileRepository

    @Before
    fun setUp() {
        repo = DnsProfileRepository(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun `add stores profile and list returns it`() {
        val p = repo.add("Google", "dns.google")
        assertEquals(listOf(p), repo.list())
        assertEquals("Google", p.name)
        assertEquals("dns.google", p.hostname)
    }

    @Test
    fun `active starts empty`() {
        assertNull(repo.activeId())
        assertNull(repo.activeProfile())
    }

    @Test
    fun `setActive points to exactly one profile`() {
        val a = repo.add("A", "a.example.com")
        val b = repo.add("B", "b.example.com")
        repo.setActive(a.id)
        assertEquals(a.id, repo.activeId())
        assertEquals(a, repo.activeProfile())
        repo.setActive(b.id)
        assertEquals(b.id, repo.activeId())
        // Single active invariant: only one id is stored by construction.
        assertEquals(1, repo.list().count { it.id == repo.activeId() })
    }

    @Test
    fun `deleting active clears selection and reports it`() {
        val a = repo.add("A", "a.example.com")
        repo.setActive(a.id)
        assertTrue(repo.delete(a.id))
        assertNull(repo.activeId())
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `deleting inactive keeps selection`() {
        val a = repo.add("A", "a.example.com")
        val b = repo.add("B", "b.example.com")
        repo.setActive(a.id)
        assertFalse(repo.delete(b.id))
        assertEquals(a.id, repo.activeId())
    }

    @Test
    fun `update replaces fields`() {
        val a = repo.add("A", "a.example.com")
        repo.update(a.copy(name = "Renamed", hostname = "renamed.example.com"))
        val stored = repo.get(a.id)!!
        assertEquals("Renamed", stored.name)
        assertEquals("renamed.example.com", stored.hostname)
    }

    @Test
    fun `profiles survive repository recreation`() {
        val a = repo.add("A", "a.example.com")
        repo.setActive(a.id)
        val fresh = DnsProfileRepository(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(listOf(a), fresh.list())
        assertEquals(a.id, fresh.activeId())
    }

    @Test
    fun `export and import round-trip without duplicates`() {
        val a = repo.add("A", "a.example.com")
        repo.setActive(a.id)
        val json = repo.exportJson()

        // Simulate a fresh device: empty profile storage.
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("netpilot_profiles", 0).edit().clear().commit()
        val fresh = DnsProfileRepository(context)
        assertEquals(1, fresh.importJson(json))
        assertEquals(0, fresh.importJson(json)) // merge, never duplicate
        assertEquals(a.hostname, fresh.list().first().hostname)
    }

    @Test
    fun `import rejects invalid hostnames`() {
        val json = """
            {"type":"netpilot.dns.v1","profiles":[
              {"id":"x","name":"Good","hostname":"good.example.com"},
              {"id":"y","name":"Bad","hostname":"8.8.8.8"}
            ]}
        """.trimIndent()
        assertEquals(1, repo.importJson(json))
        assertEquals(1, repo.list().size)
    }

    @Test
    fun `corrupt storage degrades to empty list`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("netpilot_profiles", 0)
            .edit().putString("dns_profiles", "not-json").commit()
        assertTrue(DnsProfileRepository(context).list().isEmpty())
    }

    @Test
    fun `unknown active id does not produce an active profile`() {
        val a = repo.add("A", "a.example.com")
        repo.setActive(a.id)
        repo.delete(a.id)
        repo.add("B", "b.example.com")
        assertNull(repo.activeProfile())
        assertEquals(emptyList<DnsProfile>(), repo.list().filter { it.id == repo.activeId() })
    }
}
