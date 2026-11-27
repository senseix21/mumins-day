package com.senseix21.muminsday.tools.gate

import java.io.File
import kotlin.system.exitProcess

/** Arguments: the path of a districts file, then `true` or `false` for whether the file must exist. */
object DistrictsMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <path to districts.json> <required: true|false>" }
        val file = File(args[0])
        val text = if (file.isFile) file.readText(Charsets.UTF_8) else null
        val report = runDistrictGate(text, required = args[1].toBooleanStrict(), name = file.name.let { "${file.parentFile?.name}/$it" })
        report.lines.forEach(::println)
        if (!report.passed) exitProcess(1)
    }
}
