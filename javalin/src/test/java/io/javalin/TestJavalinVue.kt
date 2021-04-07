/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin

import io.javalin.http.Context
import io.javalin.http.staticfiles.Location
import io.javalin.plugin.rendering.vue.JavalinVue
import io.javalin.plugin.rendering.vue.VueComponent
import io.javalin.testing.TestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

class TestJavalinVue {

    @Before
    fun setup() {
        before()
    }

    companion object {
        fun before() {
            JavalinVue.isDev = null // reset
            JavalinVue.stateFunction = { ctx -> mapOf<String, String>() } // reset
            JavalinVue.rootDirectory("src/test/resources/vue", Location.EXTERNAL) // src/main -> src/test
            JavalinVue.optimizeDependencies = false
        }
    }

    data class User(val name: String, val email: String)
    data class Role(val name: String)
    data class State(val user: User, val role: Role)

    private val state = State(User("tipsy", "tipsy@tipsy.tipsy"), Role("Maintainer"))

    @Test
    fun `vue component with state`() = TestUtil.test { app, http ->
        // This is just encodeURIComponent('{"pathParams":{"my-param":"test-path-param"},"queryParams":{"qp":["test-query-param"]},"state":{"user":{"name":"tipsy","email":"tipsy@tipsy.tipsy"},"role":{"name":"Maintainer"}}}')
        val encodedState = "%7B%22pathParams%22%3A%7B%22my-param%22%3A%22test-path-param%22%7D%2C%22queryParams%22%3A%7B%22qp%22%3A%5B%22test-query-param%22%5D%7D%2C%22state%22%3A%7B%22user%22%3A%7B%22name%22%3A%22tipsy%22%2C%22email%22%3A%22tipsy%40tipsy.tipsy%22%7D%2C%22role%22%3A%7B%22name%22%3A%22Maintainer%22%7D%7D%7D"
        JavalinVue.stateFunction = { ctx -> state }
        app.get("/vue/:my-param", VueComponent("<test-component></test-component>"))
        val res = http.getBody("/vue/test-path-param?qp=test-query-param")
        assertThat(res).contains(encodedState)
        assertThat(res).contains("""Vue.component("test-component", {template: "#test-component"});""")
        assertThat(res).contains("<body><test-component></test-component></body>")
    }

    @Test
    fun `vue component without state`() = TestUtil.test { app, http ->
        // This is just encodeURIComponent('{"pathParams":{},"queryParams":{},"state":{}}')
        val encodedEmptyState = "%7B%22pathParams%22%3A%7B%7D%2C%22queryParams%22%3A%7B%7D%2C%22state%22%3A%7B%7D%7D"

        app.get("/no-state", VueComponent("<test-component></test-component>"))
        val res = http.getBody("/no-state")
        assertThat(res).contains(encodedEmptyState)
        assertThat(res).contains("<body><test-component></test-component></body>")
    }

    @Test
    fun `vue component with component-specific state`() = TestUtil.test { app, http ->
        // This is just encodeURIComponent('{"pathParams":{},"queryParams":{},"state":{}}')
        val encodedEmptyState = "%7B%22pathParams%22%3A%7B%7D%2C%22queryParams%22%3A%7B%7D%2C%22state%22%3A%7B%7D%7D"

        // This is just encodeURIComponent('{"pathParams":{},"queryParams":{},"state":{"test":"tast"}}')
        val encodedTestState = "%7B%22pathParams%22%3A%7B%7D%2C%22queryParams%22%3A%7B%7D%2C%22state%22%3A%7B%22test%22%3A%22tast%22%7D%7D"

        app.get("/no-state", VueComponent("<test-component></test-component>"))
        val noStateRes = http.getBody("/no-state")
        app.get("/specific-state", VueComponent("<test-component></test-component>", mapOf("test" to "tast")))
        val specificStateRes = http.getBody("/specific-state")
        assertThat(noStateRes).contains(encodedEmptyState)
        assertThat(specificStateRes).contains(encodedTestState)
    }

    @Test
    fun `vue component works Javalin#error`() = TestUtil.test { app, http ->
        app.get("/") { it.status(404) }
        app.error(404, "html", VueComponent("<test-component></test-component>"))
        assertThat(http.htmlGet("/").body).contains("<body><test-component></test-component></body>")
    }

    @Test
    fun `unicode in template works`() = TestUtil.test { app, http ->
        app.get("/unicode", VueComponent("<test-component></test-component>"))
        assertThat(http.getBody("/unicode")).contains("<div>Test ÆØÅ</div>")
    }

    @Test
    fun `default params are escaped`() = TestUtil.test { app, http ->
        val encodedXSS = "%3Cscript%3Ealert%281%29%3Cscript%3E"
        app.get("/escaped", VueComponent("<test-component></test-component>"))
        // keys
        assertThat(http.getBody("/escaped?${encodedXSS}=value")).doesNotContain("<script>alert(1)<script>")
        assertThat(http.getBody("/escaped?${encodedXSS}=value")).contains(encodedXSS)
        // values
        assertThat(http.getBody("/escaped?key=${encodedXSS}")).doesNotContain("<script>alert(1)<script>")
        assertThat(http.getBody("/escaped?key=${encodedXSS}")).contains(encodedXSS)
    }

    @Test
    fun `quotes are handled correctly`() = TestUtil.test { app, http ->
        // This is just encodeURIComponent('"test":["\\"cool\\""]')
        val encodedTestObject = "%22test%22%3A%5B%22%5C%22cool%5C%22%22%5D"
        app.get("/escaped", VueComponent("<test-component></test-component>"))

        assertThat(http.getBody("""/escaped?test=%22cool%22""")).contains(encodedTestObject)
    }

    @Test
    fun `component shorthand works`() = TestUtil.test { app, http ->
        app.get("/shorthand", VueComponent("test-component"))
        assertThat(http.getBody("/shorthand")).contains("<test-component></test-component>")
    }

    @Test
    fun `non-existent component fails`() = TestUtil.test { app, http ->
        app.get("/fail", VueComponent("unknown-component"))
        assertThat(http.getBody("/fail")).contains("Route component not found: <unknown-component></unknown-component>")
    }

    @Test
    fun `component can have attributes`() = TestUtil.test { app, http ->
        app.get("/attr", VueComponent("<test-component attr='1'></test-component>"))
        assertThat(http.getBody("/attr")).contains("<test-component attr='1'>")
    }

    @Test
    fun `classpath rootDirectory works`() = TestUtil.test { app, http ->
        JavalinVue.rootDirectory("/vue", Location.CLASSPATH)
        app.get("/classpath", VueComponent("test-component"))
        assertThat(http.getBody("/classpath")).contains("<test-component></test-component>")
    }

    @Test
    fun `setting rootDirectory with Path works`() = TestUtil.test { app, http ->
        JavalinVue.rootDirectory(Paths.get("src/test/resources/vue"))
        app.get("/path", VueComponent("test-component"))
        assertThat(http.getBody("/path")).contains("<test-component></test-component>")
    }

    @Test
    fun `non-existent folder fails`() = TestUtil.test { app, http ->
        JavalinVue.isDev = true // reset
        JavalinVue.rootDirectory("/vue", Location.EXTERNAL)
        app.get("/fail", VueComponent("test-component"))
        assertThat(http.get("/fail").status).isEqualTo(500)
    }

    @Test
    fun `@cdnWebjar resolves to webjar on localhost`() = TestUtil.test { app, http ->
        val ctx = mockk<Context>(relaxed = true)
        JavalinVue.isDev = true // reset
        every { ctx.url() } returns "http://localhost:1234/"
        VueComponent("<test-component></test-component>").handle(ctx)
        val slot = slot<String>().also { verify { ctx.html(html = capture(it)) } }
        assertThat(slot.captured).contains("""src="/webjars/""")
    }

    @Test
    fun `@cdnWebjar resolves to cdn on non-localhost`() = TestUtil.test { app, http ->
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.url() } returns "https://example.com"
        VueComponent("<test-component></test-component>").handle(ctx)
        val slot = slot<String>().also { verify { ctx.html(html = capture(it)) } }
        assertThat(slot.captured).contains("""src="https://cdn.jsdelivr.net/webjars/""")
    }

    @Test
    fun `@cdnWebjar resolves to https even on non https hosts`() = TestUtil.test { app, http ->
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.url() } returns "http://123.123.123.123:1234/"
        VueComponent("<test-component></test-component>").handle(ctx)
        val slot = slot<String>().also { verify { ctx.html(html = capture(it)) } }
        assertThat(slot.captured).contains("""src="https://cdn.jsdelivr.net/webjars/""")
    }

    @Test
    fun `@inlineFile functionality works as expected if not-dev`() = TestUtil.test { app, http ->
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.url() } returns "http://123.123.123.123:1234/"
        VueComponent("<test-component></test-component>").handle(ctx)
        val slot = slot<String>().also { verify { ctx.html(html = capture(it)) } }
        assertThat(slot.captured).contains("""<script>let a = "Always included";let ${"\$"}a = "Dollar works"</script>""")
        assertThat(slot.captured).contains("""<script>let b = "Included if not dev"</script>""")
        assertThat(slot.captured).doesNotContain("""<script>let b = "Included if dev"</script>""")
        assertThat(slot.captured).doesNotContain("""<script>@inlineFileDev("/vue/scripts-dev.js")</script>""")
        assertThat(slot.captured).doesNotContain("""<script>@inlineFile""")
    }

    @Test
    fun `@inlineFile functionality works as expected if dev`() = TestUtil.test { app, http ->
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.url() } returns "http://localhost:1234/"
        VueComponent("<test-component></test-component>").handle(ctx)
        val slot = slot<String>().also { verify { ctx.html(html = capture(it)) } }
        assertThat(slot.captured).contains("""<script>let a = "Always included";let ${"\$"}a = "Dollar works"</script>""")
        assertThat(slot.captured).contains("""<script>let b = "Included if dev"</script>""")
        assertThat(slot.captured).doesNotContain("""<script>let b = "Included if not dev"</script>""")
        assertThat(slot.captured).doesNotContain("""<script>@inlineFileNotDev("/vue/scripts-not-dev.js")</script>""")
        assertThat(slot.captured).doesNotContain("""<script>@inlineFile""")
    }

}
