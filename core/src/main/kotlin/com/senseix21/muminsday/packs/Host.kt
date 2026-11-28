package com.senseix21.muminsday.packs

/** Where packs come from (ADR-25). There is exactly one host constant in the app; empty means no packs at all. */
public sealed interface PackSource {
    /** No host is set: the downloads screen says packs are not available yet and makes no request. */
    public data object NotAvailable : PackSource

    /** A host name that is not a plain DNS name. Treated as not available; a test keeps the shipped constant valid. */
    public data class Invalid(val text: String) : PackSource

    /** Made only by [packSource], so a name that is not a plain DNS name cannot become a host. */
    public class Host internal constructor(public val name: String) : PackSource {
        override fun equals(other: Any?): Boolean = other is Host && other.name == name
        override fun hashCode(): Int = name.hashCode()
        override fun toString(): String = "Host($name)"
    }
}

/**
 * Lowercase DNS labels of letters, digits and hyphens joined by dots, at least two labels: no scheme, no port, no path,
 * no user, so the address of every request is `https://` + this name + `/` + a [PackPath], and nothing else.
 */
public fun packSource(host: String): PackSource {
    if (host.isEmpty()) return PackSource.NotAvailable
    val labels = host.split('.')
    val valid = host.length <= MAX_HOST_LENGTH && labels.size >= 2 && labels.all { LABEL.matches(it) }
    return if (valid) PackSource.Host(host) else PackSource.Invalid(host)
}

/** The one URL a pack file is fetched from. Always https (ADR-25); the path cannot climb out of the host's root. */
public fun packUrl(host: PackSource.Host, path: PackPath): String = "https://${host.name}/${path.value}"

/**
 * The one User-Agent every pack request sends, replacing the platform's, which names the phone's model and Android build
 * (ASSUMED from Android's `http.agent` default, "Dalvik/... (Linux; U; Android ...; MODEL Build/...)"). It names
 * nothing about the phone, the user or the app's version.
 */
public const val PACK_USER_AGENT: String = "packs"

private val LABEL = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
private const val MAX_HOST_LENGTH = 253
