package com.senseix21.muminsday.store

import android.content.Context
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.hisnul.CHAPTERS_FILE
import com.senseix21.muminsday.hisnul.DUAS_FOLDER
import com.senseix21.muminsday.hisnul.HisnulCheck
import com.senseix21.muminsday.hisnul.HisnulMuslim
import com.senseix21.muminsday.hisnul.checkHisnulMuslim
import java.io.FileNotFoundException

/** What opening Hisnul Muslim gave (ADR-27). Only [Loaded] shows any of it. */
sealed interface HisnulLoad {
    data class Loaded(val collection: HisnulMuslim) : HisnulLoad

    /** The files are there but core refused them; the build's gate normally stops this reaching a phone. */
    data class Refused(val reasons: List<String>) : HisnulLoad

    /** A release, or a debug build made where `content/local/hisnul-muslim/` does not exist. */
    data object NotInThisBuild : HisnulLoad
}

/** The asset folder the debug build stages `content/local/hisnul-muslim/` into. */
private const val HISNUL_ASSETS: String = "hisnul-muslim"

/** One reading per process: 313 files, read once off the main thread, then shared by both screens. */
private var opened: HisnulLoad? = null

/**
 * Reads the debug assets into the typed collection; core judges them with the check the build's gate ran. A release
 * build never packages the files (ADR-27, T-BUILD-7) and, should one ever carry them, shows nothing: unreviewed
 * religious text does not reach a store user (INV-C1, INV-C6). Call off the main thread.
 */
fun openHisnulMuslim(context: Context): HisnulLoad = synchronized(HisnulLoad::class) {
    opened ?: read(context).also { opened = it }
}

private fun read(context: Context): HisnulLoad {
    if (!BuildConfig.DEBUG) return HisnulLoad.NotInThisBuild
    val assets = context.assets
    val chapters = try {
        assets.open("$HISNUL_ASSETS/$CHAPTERS_FILE").use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        return HisnulLoad.NotInThisBuild
    }
    val names = assets.list("$HISNUL_ASSETS/$DUAS_FOLDER").orEmpty()
    val duas = names.associateWith { name -> assets.open("$HISNUL_ASSETS/$DUAS_FOLDER/$name").use { it.readBytes().toString(Charsets.UTF_8) } }
    return when (val check = checkHisnulMuslim(chapters, duas)) {
        is HisnulCheck.Accepted -> HisnulLoad.Loaded(check.collection)
        is HisnulCheck.Refused -> HisnulLoad.Refused(check.reasons)
    }
}
