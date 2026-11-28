package uk.co.traynor.privategallery.launcher

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject
import org.json.JSONTokener

/** Portable theme files carry only appearance values and never launcher or account data. */
internal object LauncherThemePresetCodec {
    internal const val MAX_BYTES = 4_096
    private const val TYPE = "nova-launcher-theme"
    private const val SCHEMA = 1

    fun encode(appearance: LauncherAppearance): ByteArray = JSONObject()
        .put("type", TYPE).put("schema", SCHEMA)
        .put("appearance", LauncherLayoutCodec.appearanceJson(appearance))
        .toString().encodeToByteArray().also { require(it.size <= MAX_BYTES) }

    fun decode(bytes: ByteArray): LauncherAppearance {
        require(bytes.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        LauncherLayoutCodec.checkDepth(text)
        val reader = JSONTokener(text)
        val root = reader.nextValue() as? JSONObject ?: error("Invalid theme preset")
        require(reader.nextClean() == '\u0000')
        require(root.keys().asSequence().toSet() == setOf("type", "schema", "appearance"))
        require(root.get("type") == TYPE && root.get("schema") == SCHEMA)
        return LauncherLayoutCodec.readAppearance(root.getJSONObject("appearance"))
    }
}
