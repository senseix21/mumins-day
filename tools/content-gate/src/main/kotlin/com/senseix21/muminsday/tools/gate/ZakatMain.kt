package com.senseix21.muminsday.tools.gate

import java.io.File
import kotlin.system.exitProcess

/** Arguments: the path of a rules file, `true` or `false` for whether it must exist, `true` or `false` for whether a test fixture is allowed. */
object ZakatMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: <path to zakat-rules.json> <required: true|false> <allowTestFixture: true|false>" }
        val file = File(args[0])
        val text = if (file.isFile) file.readText(Charsets.UTF_8) else null
        val report = runZakatGate(text, required = args[1].toBooleanStrict(), allowTestFixture = args[2].toBooleanStrict(), name = "${file.parentFile?.name}/${file.name}")
        report.lines.forEach(::println)
        if (!report.passed) exitProcess(1)
    }
}
