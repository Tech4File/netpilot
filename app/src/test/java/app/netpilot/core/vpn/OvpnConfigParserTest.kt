package app.netpilot.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnConfigParserTest {

    @Test
    fun `minimal client config parses`() {
        val config = OvpnConfigParser.parse(
            """
            client
            remote vpn.example.com 1194 udp
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIB
            -----END CERTIFICATE-----
            </ca>
            dev tun
            """.trimIndent(),
        )
        assertTrue(config.isClient)
        assertEquals(1, config.remotes.size)
        assertEquals("vpn.example.com", config.remotes[0].host)
        assertEquals(1194, config.remotes[0].port)
        assertEquals("udp", config.remotes[0].proto)
        assertTrue(config.caInline)
        assertTrue(config.isUsableForProfile())
        assertEquals("vpn.example.com:1194/udp", config.primaryEndpoint())
    }

    @Test
    fun `defaults apply when port and proto omitted`() {
        val config = OvpnConfigParser.parse("client\nremote vpn.example.com")
        assertEquals(1194, config.remotes[0].port)
        assertNull(config.remotes[0].proto)
        assertEquals("vpn.example.com:1194", config.primaryEndpoint())
    }

    @Test
    fun `directives are captured`() {
        val config = OvpnConfigParser.parse(
            """
            tls-client
            remote a.com 443 tcp
            proto tcp4
            cipher AES-256-CBC
            data-ciphers AES-256-GCM AES-128-GCM
            auth SHA256
            auth-user-pass
            redirect-gateway def1
            comp-lzo
            tun-mtu 1440
            verb 3
            remote-random
            dhcp-option DNS 10.8.0.1
            tls-auth ta.key 1
            """.trimIndent(),
        )
        assertTrue(config.isClient)
        assertEquals("tcp4", config.proto)
        assertEquals("AES-256-CBC", config.cipher)
        assertEquals(listOf("AES-256-GCM", "AES-128-GCM"), config.dataCiphers)
        assertEquals("SHA256", config.auth)
        assertTrue(config.authUserPass)
        assertTrue(config.redirectGateway)
        assertTrue(config.compLzo)
        assertEquals(1440, config.mtu)
        assertEquals(3, config.verb)
        assertTrue(config.remoteRandom)
        assertEquals(listOf("10.8.0.1"), config.dhcpDns)
        assertTrue(config.tlsAuth)
        assertFalse(config.tlsCrypt)
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val config = OvpnConfigParser.parse(
            """
            # a comment
            ; another comment

            client
              remote b.com 1194   # inline comments are tolerated by OpenVPN as separate tokens
            """.trimIndent(),
        )
        assertTrue(config.isClient)
        assertEquals("b.com", config.remotes[0].host)
    }

    @Test
    fun `multiple remotes are collected in order`() {
        val config = OvpnConfigParser.parse(
            "client\nremote a.com 1194 udp\nremote b.com 53 tcp\nremote c.com",
        )
        assertEquals(3, config.remotes.size)
        assertEquals("a.com", config.remotes[0].host)
        assertEquals("b.com", config.remotes[1].host)
        assertEquals(53, config.remotes[1].port)
        assertEquals("c.com", config.remotes[2].host)
    }

    @Test
    fun `config without client directive is not usable`() {
        val config = OvpnConfigParser.parse("remote a.com 1194")
        assertFalse(config.isClient)
        assertFalse(config.isUsableForProfile())
    }

    @Test
    fun `empty config yields empty result`() {
        val config = OvpnConfigParser.parse("")
        assertFalse(config.isClient)
        assertTrue(config.remotes.isEmpty())
        assertFalse(config.isUsableForProfile())
        assertNull(config.primaryEndpoint())
    }

    @Test
    fun `block sections are detected`() {
        val config = OvpnConfigParser.parse(
            """
            client
            remote a.com 1194
            <cert>
            DATA
            </cert>
            <key>
            SECRET
            </key>
            """.trimIndent(),
        )
        assertTrue(config.certInline)
        assertTrue(config.keyInline)
        assertEquals(4, config.inlineBlockSizes["cert"])
    }

    @Test
    fun `directives inside blocks do not leak out`() {
        val config = OvpnConfigParser.parse(
            """
            client
            remote a.com 1194
            <ca>
            remote this-is-not-a-remote
            </ca>
            """.trimIndent(),
        )
        assertEquals(1, config.remotes.size)
    }
}
