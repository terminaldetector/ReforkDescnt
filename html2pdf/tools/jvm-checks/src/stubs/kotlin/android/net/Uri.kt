package android.net

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Test-harness stand-in for android.net.Uri — only the surface Facebook.kt
 * uses, with the same semantics (null path/host when absent, decoded path and
 * query parameter values, a builder that percent-encodes).
 */
class Uri private constructor(
    private val scheme: String?,
    private val authority: String?,
    private val rawPath: String?,
    private val rawQuery: String?,
    private val rawFragment: String?
) {
    val host: String? get() = authority?.substringAfterLast('@')?.substringBefore(':')

    val path: String? get() = rawPath?.let { decode(it) }

    private fun pairs(): List<Pair<String, String>> =
        rawQuery?.split('&')?.mapNotNull { p ->
            if (p.isEmpty()) null
            else {
                val k = p.substringBefore('=')
                val v = if (p.contains('=')) p.substringAfter('=') else ""
                decode(k) to decode(v)
            }
        } ?: emptyList()

    fun getQueryParameter(name: String): String? =
        pairs().firstOrNull { it.first == name }?.second

    val queryParameterNames: Set<String>
        get() = pairs().map { it.first }.toCollection(LinkedHashSet())

    fun buildUpon(): Builder = Builder()
        .scheme(scheme).authority(authority).encodedPath(rawPath)
        .encodedQuery(rawQuery).fragment(rawFragment)

    override fun toString(): String = buildString {
        if (scheme != null) append(scheme).append("://")
        if (authority != null) append(authority)
        if (rawPath != null) append(rawPath)
        if (!rawQuery.isNullOrEmpty()) append('?').append(rawQuery)
        if (!rawFragment.isNullOrEmpty()) append('#').append(rawFragment)
    }

    class Builder {
        private var scheme: String? = null
        private var authority: String? = null
        private var path: String? = null
        private var query: StringBuilder? = null
        private var fragment: String? = null

        fun scheme(v: String?) = apply { scheme = v }
        fun authority(v: String?) = apply { authority = v }
        fun fragment(v: String?) = apply { fragment = v }
        fun encodedPath(v: String?) = apply { path = v }
        fun encodedQuery(v: String?) = apply { query = v?.let { StringBuilder(it) } }

        fun path(v: String?) = apply {
            path = v?.split('/')?.joinToString("/") { enc(it) }
        }

        fun appendQueryParameter(name: String, value: String) = apply {
            val q = query ?: StringBuilder().also { query = it }
            if (q.isNotEmpty()) q.append('&')
            q.append(enc(name)).append('=').append(enc(value))
        }

        fun build(): Uri = Uri(scheme, authority, path, query?.toString(), fragment)
    }

    companion object {
        @JvmStatic
        fun parse(s: String): Uri {
            val hash = s.indexOf('#')
            val body = if (hash >= 0) s.substring(0, hash) else s
            val frag = if (hash >= 0) s.substring(hash + 1) else null
            val q = body.indexOf('?')
            val beforeQ = if (q >= 0) body.substring(0, q) else body
            val query = if (q >= 0) body.substring(q + 1) else null
            val sep = beforeQ.indexOf("://")
            return if (sep < 0) {
                Uri(null, null, beforeQ.ifEmpty { null }, query, frag)
            } else {
                val scheme = beforeQ.substring(0, sep)
                val rest = beforeQ.substring(sep + 3)
                val slash = rest.indexOf('/')
                val authority = if (slash < 0) rest else rest.substring(0, slash)
                val path = if (slash < 0) null else rest.substring(slash)
                Uri(scheme, authority.ifEmpty { null }, path, query, frag)
            }
        }

        @JvmStatic
        fun decode(s: String): String = try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Throwable) { s }

        private fun enc(s: String): String =
            URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
