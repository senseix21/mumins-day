package com.senseix21.muminsday.packs

/**
 * The one pack host (ADR-25), and the only place a host name may be written. Empty: no host is named and no pack has
 * permission, so the downloads screen says packs are not available yet and the app makes no request. The manifest has
 * no `INTERNET` permission either (INV-P1); both change together, in one commit, when the founder names the host
 * (ADR-25). `NetworkSurfaceTest` fails if this is not empty while `INTERNET` is absent.
 */
const val PACK_HOST: String = ""
