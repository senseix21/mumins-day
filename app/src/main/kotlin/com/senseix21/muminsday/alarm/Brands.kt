package com.senseix21.muminsday.alarm

import com.senseix21.muminsday.R

/**
 * Which makers get "keep the app running" steps (F1 failure mode 2, story 3). The steps are from dontkillmyapp.com (G32),
 * fetched 2026-11-28, not checked on a phone. The one map below is the single source of truth: the activity shows the
 * array, the health check asks for confirmation only for a key of this map, and a test walks it.
 *
 * Keys are `Build.MANUFACTURER` lowercased. ASSUMED: these are the strings these makers report; Redmi and Poco phones
 * report "xiaomi", and Infinix and itel are Transsion brands like Tecno, so they show Tecno's steps with a note
 * ([showsTecnoStepsAsNote]). The site has no page for Infinix, itel or Honor (404), scores Nokia 0 (no extra killing),
 * and Google/Pixel is stock Android: none of those is listed.
 */
private val STEPS_BY_MANUFACTURER: Map<String, Int> = mapOf(
    "xiaomi" to R.array.brand_xiaomi_steps,
    "realme" to R.array.brand_realme_steps,
    "tecno" to R.array.brand_tecno_steps,
    "infinix" to R.array.brand_tecno_steps,
    "itel" to R.array.brand_tecno_steps,
    "samsung" to R.array.brand_samsung_steps,
    "oppo" to R.array.brand_oppo_steps,
    "vivo" to R.array.brand_vivo_steps,
    "oneplus" to R.array.brand_oneplus_steps,
    "huawei" to R.array.brand_huawei_steps,
    "motorola" to R.array.brand_motorola_steps,
)

/** Every maker that has steps, derived from the map so the two cannot disagree. */
val BRANDS_THAT_NEED_STEPS: Set<String> get() = STEPS_BY_MANUFACTURER.keys

fun brandStepsArray(manufacturer: String): Int? = STEPS_BY_MANUFACTURER[manufacturer.trim().lowercase()]

fun brandNeedsSteps(manufacturer: String): Boolean = brandStepsArray(manufacturer) != null

/** Infinix and itel have no page of their own: the screen says plainly that Tecno's steps are shown. */
fun showsTecnoStepsAsNote(manufacturer: String): Boolean = manufacturer.trim().lowercase() in setOf("infinix", "itel")
