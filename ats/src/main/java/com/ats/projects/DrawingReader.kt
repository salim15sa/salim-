package com.ats.projects

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import org.json.JSONObject
import java.time.Duration
import java.util.Base64

/** One device found on a drawing, before the user confirms it. */
data class FoundDevice(
    val floor: String,
    val system: String,
    val type: String,
    val location: String,
    val note: String = "",
)

data class DrawingResult(
    val floors: List<String>,
    val rooms: List<String>,
    val devices: List<FoundDevice>,
)

/**
 * Sends an ELV / automation drawing (PDF or photo) to Claude and gets back every
 * device symbol, with its floor and room, mapped onto the app's catalog.
 */
object DrawingReader {

    const val MODEL = "claude-opus-5-5"

    class ReadException(message: String) : Exception(message)

    /** [mime] is application/pdf, image/jpeg, image/png or image/webp. Blocking – call off the UI thread. */
    fun read(apiKey: String, bytes: ByteArray, mime: String, fileName: String, floorHint: String): DrawingResult {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey.trim())
            .timeout(Duration.ofMinutes(10))
            .build()

        val b64 = Base64.getEncoder().encodeToString(bytes)
        val fileBlock: ContentBlockParam = if (mime == "application/pdf") {
            ContentBlockParam.ofDocument(
                DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(b64).build())
                    .title(fileName)
                    .build(),
            )
        } else {
            val media = when (mime) {
                "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
                "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
                "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
                else -> Base64ImageSource.MediaType.IMAGE_JPEG
            }
            ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(Base64ImageSource.builder().data(b64).mediaType(media).build())
                    .build(),
            )
        }

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(32000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.HIGH)
                    .format(JsonOutputFormat.builder().schema(schema()).build())
                    .build(),
            )
            // If the main model declines, the API retries on a fallback model instead of failing.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessageOfContentBlockParams(
                listOf(fileBlock, ContentBlockParam.ofText(TextBlockParam.builder().text(userPrompt(floorHint)).build())),
            )
            .build()

        val response = client.messages().create(params)
        val stop = response.stopReason().map { it.toString() }.orElse("")
        if (stop == "refusal") {
            throw ReadException("The drawing could not be read (request declined). Add the devices manually.")
        }
        val text = response.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("")
        if (text.isBlank()) throw ReadException("No answer from the drawing reader ($stop). Try again.")
        if (stop == "max_tokens") throw ReadException("The drawing is too big for one read. Split it per floor and try again.")
        return parse(text)
    }

    fun parse(text: String): DrawingResult {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw ReadException("Could not understand the reader's answer.")
        val o = JSONObject(text.substring(start, end + 1))
        fun list(key: String): List<String> = o.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }
        } ?: emptyList()
        val arr = o.optJSONArray("devices")
        val devices = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val system = Catalog.matchSystem(d.optString("system"))
            val rawType = d.optString("type").trim()
            val type = Catalog.types(system).firstOrNull { it.name.equals(rawType, true) }?.name ?: rawType.ifEmpty { "Device" }
            val count = d.optInt("count", 1).coerceIn(1, 200)
            val one = FoundDevice(
                floor = d.optString("floor").trim().ifEmpty { "Ground Floor" },
                system = system,
                type = type,
                location = d.optString("location").trim(),
                note = d.optString("note").trim(),
            )
            List(count) { one }
        }.flatten()
        return DrawingResult(list("floors"), list("rooms"), devices)
    }

    private fun schema(): JsonOutputFormat.Schema {
        fun str() = mapOf("type" to "string")
        val device = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "floor" to str(),
                "system" to str(),
                "type" to str(),
                "location" to str(),
                "count" to mapOf("type" to "integer"),
                "note" to str(),
            ),
            "required" to listOf("floor", "system", "type", "location", "count", "note"),
            "additionalProperties" to false,
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "floors" to mapOf("type" to "array", "items" to str()),
                        "rooms" to mapOf("type" to "array", "items" to str()),
                        "devices" to mapOf("type" to "array", "items" to device),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("floors", "rooms", "devices")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }

    private val CATALOG_TEXT: String = Catalog.systems.joinToString("\n") { s ->
        "- ${s.name}: " + s.types.joinToString(", ") { it.name }
    }

    private val SYSTEM_PROMPT = """
        You read ELV, smart-home automation and low-current electrical shop drawings for a systems
        integrator in Oman. Your job is to make a complete device take-off: every device symbol drawn
        on the plans, each with the floor and the room it sits in. The result becomes the site
        progress checklist, so a missed or double-counted device costs the team real work.

        How to read the drawing:
        - Use the drawing's own LEGEND to identify symbols (cameras, intercom units, Wi-Fi access
          points, touch screens, motion sensors, glass-break sensors, keypads, sirens, data sockets,
          TV points, smart DBs, AC gateways, switches, electric locks, card readers and so on).
        - Each sheet or plan title (GROUND FLOOR, FIRST FLOOR, PENTHOUSE …) is the floor.
        - Use the room name written inside the room. If a room has no label, give the best plain
          description (e.g. "Parking", "Outdoor Front", "Corridor near stairs").
        - Ignore cable runs, cable notes ("1 CAT6 cable from the rack"), dimensions, title block,
          legend entries themselves and items marked "provision only" unless a device is drawn.

        Output rules:
        - "system" and "type" must use exactly these names when one fits (system: types):
        $CATALOG_TEXT
          Use system "Other" with a short type name only when nothing fits.
        - One entry per device. If several identical devices are in the same room you may give one
          entry with "count" set to how many; otherwise count is 1.
        - Floor names like "Ground Floor", "First Floor", "Penthouse Floor".
        - "floors": all floors in the drawing, bottom to top. "rooms": every room name you used.
        - "note": empty, or a short remark (e.g. "ceiling mounted", "symbol unclear").
    """.trimIndent()

    private fun userPrompt(floorHint: String) = buildString {
        append("Make the complete device take-off for this drawing.")
        if (floorHint.isNotBlank()) {
            append(" All plans in this file belong to: ").append(floorHint.trim())
            append(" (use it as the floor unless the sheet clearly says otherwise).")
        }
    }
}
