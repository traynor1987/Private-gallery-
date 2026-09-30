package uk.co.traynor.privategallery.phase0

import java.io.File
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Read-only bounded observation of synthetic evidence engine roots, never live DB copy/rewrite. */
internal object EngineMarkerScan {
  fun scan(dataDir: File, markers: List<String>): JSONObject {
    val base = dataDir.canonicalFile
    val roots = listOf(File(base, "app_webview_${Phase0EvidenceApplication.SUFFIX}"),
      File(base, "cache/WebView_${Phase0EvidenceApplication.SUFFIX}"))
    var examined = 0
    var attempted = 0
    var bytes = 0L
    var omitted = 0
    val matches = JSONArray()
    val foundRoots = JSONArray()
    for (root in roots) {
      if (!root.isDirectory || root.canonicalFile != root.absoluteFile) continue
      foundRoots.put(root.relativeTo(base).path)
      root.walkTopDown().maxDepth(12).onEnter { it.canonicalFile == it.absoluteFile }.forEach { file ->
        if (file.isFile) {
          if (file.canonicalFile != file.absoluteFile || attempted >= 1000 || file.length() > 32L * 1024 * 1024 ||
            bytes + file.length() > 128L * 1024 * 1024) { omitted++; return@forEach }
          attempted++
          try {
            val limit = minOf(32L * 1024 * 1024, 128L * 1024 * 1024 - bytes)
            val raw = file.inputStream().use { input ->
              val out = ByteArrayOutputStream()
              val buffer = ByteArray(64 * 1024)
              while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                check(out.size().toLong() + count <= limit) { "bounded synthetic scan" }
                out.write(buffer, 0, count)
              }
              out.toByteArray()
            }
            examined++; bytes += raw.size
            val ascii = raw.toString(Charsets.ISO_8859_1)
            val utf16 = raw.toString(Charsets.UTF_16LE)
            markers.forEach { marker ->
              if (ascii.contains(marker) || utf16.contains(marker)) matches.put(JSONObject()
                .put("path", file.relativeTo(base).path).put("marker", marker))
            }
          } catch (_: Exception) { omitted++ }
        }
      }
    }
    return JSONObject().put("status", if (foundRoots.length() == 0) "UNMEASURED_NO_ENGINE_ROOT" else "BOUNDED_READ_ONLY_OBSERVATION")
      .put("rootsFound", foundRoots)
      .put("filesExamined", examined).put("bytesExamined", bytes).put("filesOmitted", omitted)
      .put("matches", matches).put("absenceProvesErasure", false)
      .put("consistentDatabaseSnapshot", false)
  }
}
