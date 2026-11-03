package com.openemux.mobile.data

/** An [HttpGet] that answers from [routes] (the first whose key the URL contains) and keeps every URL asked. */
class FakeHttp(private val routes: MutableMap<String, HttpReply> = linkedMapOf()) : HttpGet {
    val asked = mutableListOf<String>()
    var fallback = HttpReply(404, null)

    fun on(part: String, reply: HttpReply) = apply { routes[part] = reply }

    override suspend fun get(url: String): HttpReply {
        asked += url
        return routes.entries.firstOrNull { url.contains(it.key) }?.value ?: fallback
    }

    companion object {
        fun ok(text: String) = HttpReply(200, text.toByteArray())

        /** A valid-looking PNG of [size] bytes; [seed] makes different ones. */
        fun png(seed: Int = 0, size: Int = 80) = HttpReply(200, ByteArray(size).also {
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(it)
            it[size - 1] = seed.toByte()
        })

        fun game(vararg medias: Triple<String, String, String>) = ok(
            """{"response":{"jeu":{"medias":[""" + medias.joinToString(",") { (type, region, url) ->
                """{"type":"$type","region":"$region","url":"$url"}"""
            } + "]}}}",
        )
    }
}
