package com.senseix21.muminsday.entitlements

/** What v1 contains. A later paid tier would gate some of these; v1 gates none. */
public enum class Feature { Adhan, PrayerTimes, Qibla, Deed }

/**
 * The one interface the architecture allows with a single implementation (ADR in 03-architecture, "Paid tier"): a paid
 * tier attaches here later. No billing code exists.
 */
public interface Entitlements {
    public fun allows(feature: Feature): Boolean
}

/** v1: everything is included, always. */
public data object Everything : Entitlements {
    override fun allows(feature: Feature): Boolean = true
}
