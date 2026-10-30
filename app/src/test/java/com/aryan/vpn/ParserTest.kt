package com.aryan.vpn

import com.aryan.vpn.core.*
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    @Test fun shipsNoCredentialsOrDefaultProfiles() {
        assertTrue(DefaultProfiles.load().isEmpty())
    }
    @Test fun prefersTlsHostnameForIpv6EndpointFallback() {
        val p = VlessParser.parse(
            "vless://123e4567-e89b-12d3-a456-426614174000@[2001:db8::1]:443?type=ws&security=tls&host=edge.example.com&sni=edge.example.com"
        )
        assertEquals("2001:db8::1", p.host)
        assertEquals("edge.example.com", p.connectionHost)
        assertFalse(p.toString().contains(p.uuid))
    }
    @Test fun keepsIpv6EndpointWhenNoDnsHostnameIsPresent() {
        val p = VlessParser.parse(
            "vless://123e4567-e89b-12d3-a456-426614174000@[2001:db8::1]:443?type=ws&security=tls"
        )
        assertEquals(p.host, p.connectionHost)
    }
    @Test(expected = VlessParser.ParseException::class) fun rejectsGarbage() { VlessParser.parse("vmess://nope") }
    @Test fun dnsValidation() {
        assertTrue(DnsValidator.isValid("1.1.1.1")); assertTrue(DnsValidator.isValid("2620:fe::fe"))
        assertFalse(DnsValidator.isValid("999.1.1.1")); assertFalse(DnsValidator.isValid("example.com"))
    }
}
