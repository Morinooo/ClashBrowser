package com.jomo.clashbrowser

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 配置导入校验：基础端口归一、回环代理节点拒绝、合法配置放行。 */
class ProxyConfigTest {

    private val good = """
        mixed-port: 1080
        port: 1081
        proxies:
          - name: n1
            type: ss
            server: 1.2.3.4
            port: 8388
    """.trimIndent()

    @Test
    fun basicsForcePortsAndController() {
        val out = ProxyManager.ensureBasics(good)
        assertTrue(out.contains("mixed-port: 7890"))
        assertTrue(out.contains("port: 0"))
        assertTrue(out.contains("socks-port: 0"))
        assertTrue(out.contains("allow-lan: false"))
        assertTrue(out.contains("external-controller: 127.0.0.1:9090"))
        // 节点自身的 port 不应被顶层归一误伤
        assertTrue(out.contains("port: 8388"))
    }

    @Test
    fun rejectsLoopbackNodeOnMixedPort() {
        val bad = good
            .replace("server: 1.2.3.4", "server: 127.0.0.1")
            .replace("port: 8388", "port: 7890")
        assertNotNull(ProxyManager.validateYaml(bad))
    }

    @Test
    fun acceptsGoodConfig() {
        assertNull(ProxyManager.validateYaml(good))
    }

    @Test
    fun rejectsMissingProxies() {
        assertNotNull(ProxyManager.validateYaml("mixed-port: 7890\n"))
    }

    @Test
    fun rejectsNonMapping() {
        assertNotNull(ProxyManager.validateYaml("- a\n- b\n"))
    }
}