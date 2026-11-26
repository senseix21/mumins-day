package com.senseix21.muminsday.tools.gate

import java.io.File
import kotlin.system.exitProcess

/** Arguments: the path of `content/deeds.json`, then `true` or `false` for whether the file must exist. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <path to deeds.json> <required: true|false>" }
    val file = File(args[0])
    val text = if (file.isFile) file.readText(Charsets.UTF_8) else null
    val report = runGate(text, required = args[1].toBooleanStrict())
    report.lines.forEach(::println)
    if (!report.passed) exitProcess(1)
}
