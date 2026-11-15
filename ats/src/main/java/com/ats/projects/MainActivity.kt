package com.ats.projects

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(Store(applicationContext))
        setContent {
            AtsTheme { App(state) }
        }
    }
}

val Navy = Color(0xFF0E4A5F)
val Teal = Color(0xFF2FB5B8)
val Paper = Color(0xFFF3F6F8)
val DoneGreen = Color(0xFF00A651)
val BadRed = Color(0xFFE53935)

@Composable
fun AtsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD5EEF0),
            onPrimaryContainer = Navy,
            secondary = Teal,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD8F3F3),
            onSecondaryContainer = Color(0xFF0B3B3C),
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFE6EDF1),
        ),
        content = content,
    )
}

// ---------------------------------------------------------------- storage

class Store(private val context: Context) {
    private val file = File(context.filesDir, "projects.json")
    private val prefs = context.getSharedPreferences("ats", Context.MODE_PRIVATE)

    fun load(): List<Project> {
        if (!file.exists()) {
            val seed = listOf(SampleData.project(1L))
            save(seed)
            return seed
        }
        return runCatching { ProjectJson.fromJson(file.readText()) }.getOrElse { emptyList() }
    }

    fun save(list: List<Project>) {
        val tmp = File(context.filesDir, "projects.json.tmp")
        tmp.writeText(ProjectJson.toJson(list))
        tmp.renameTo(file)
    }

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v.trim()).apply()
}

sealed interface Screen {
    data object Home : Screen
    data class ProjectView(val id: Long) : Screen
    data class Drawing(val id: Long) : Screen
    data object Settings : Screen
}

/** App-wide state; every change is written straight to disk. */
class AppState(val store: Store) {
    var projects by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    var apiKey by mutableStateOf(store.apiKey)
        private set

    fun project(id: Long): Project? = projects.firstOrNull { it.id == id }

    fun upsert(p: Project) {
        projects = if (projects.any { it.id == p.id }) projects.map { if (it.id == p.id) p else it } else projects + p
        store.save(projects)
    }

    fun delete(p: Project) {
        projects = projects.filter { it.id != p.id }
        store.save(projects)
    }

    fun replaceAll(list: List<Project>) {
        projects = list
        store.save(projects)
    }

    fun updateApiKey(v: String) {
        apiKey = v.trim()
        store.apiKey = apiKey
    }

    private var lastId = 0L

    /** Unique, increasing ids for projects and devices. */
    fun newId(): Long {
        val maxUsed = projects.flatMap { p -> p.devices.map { it.id } + p.id }.maxOrNull() ?: 0L
        lastId = maxOf(System.currentTimeMillis(), maxUsed + 1, lastId + 1)
        return lastId
    }

    fun updateDevices(projectId: Long, change: (List<Device>) -> List<Device>) {
        val p = project(projectId) ?: return
        upsert(p.copy(devices = change(p.devices)))
    }

    fun rememberRoom(projectId: Long, room: String) {
        val p = project(projectId) ?: return
        val r = room.trim()
        if (r.isEmpty() || p.rooms.any { it.equals(r, true) } || Catalog.defaultRooms.any { it.equals(r, true) }) return
        upsert(p.copy(rooms = p.rooms + r))
    }
}

/** Rooms to offer for a project: the project's own rooms and used locations first, then the defaults. */
fun roomOptions(p: Project): List<String> =
    (p.rooms + p.devices.map { it.location } + Catalog.defaultRooms)
        .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

fun floorOptions(p: Project): List<String> = (p.orderedFloors + Catalog.defaultFloors).distinct()

// ---------------------------------------------------------------- files & logo

object Logo {
    private var bytes: ByteArray? = null
    private var bitmap: Bitmap? = null

    fun png(context: Context): ByteArray? = bytes ?: runCatching {
        context.resources.openRawResource(R.drawable.ats_logo).use { it.readBytes() }
    }.getOrNull()?.also { bytes = it }

    fun bitmap(context: Context): Bitmap? = bitmap ?: png(context)?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)
    }?.also { bitmap = it }
}

fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "Project" }

fun writeReportFile(context: Context, name: String, bytes: ByteArray): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, name).apply { writeBytes(bytes) }
}

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

fun openFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: Exception) {
        shareFile(context, file, mime)
    }
}

fun writeToUri(context: Context, uri: Uri, bytes: ByteArray) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Reads a picked drawing. Photos are shrunk (max 2400 px) so they upload fast. Returns bytes + mime. */
fun readDrawing(context: Context, uri: Uri): Pair<ByteArray, String> {
    val cr = context.contentResolver
    val mime = cr.getType(uri) ?: if (uri.toString().lowercase().endsWith(".pdf")) MIME_PDF else "image/jpeg"
    val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Cannot open the file")
    if (mime == MIME_PDF) return raw to mime
    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw to mime
    val scale = minOf(1f, 2400f / maxOf(bmp.width, bmp.height))
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
    return bos.toByteArray() to "image/jpeg"
}

fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "drawing"

const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val MIME_PDF = "application/pdf"
const val MIME_JSON = "application/json"
