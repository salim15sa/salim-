package com.salim.taxilog

import android.content.Context
import android.content.Intent
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
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(TripRepo(applicationContext))
        setContent {
            TaxiTheme { App(state) }
        }
    }
}

val Navy = Color(0xFF1F3A5F)
val Amber = Color(0xFFF59E0B)
val Paper = Color(0xFFF4F6FA)

@Composable
fun TaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE6F5),
            onPrimaryContainer = Navy,
            secondary = Amber,
            onSecondary = Color(0xFF1F2937),
            secondaryContainer = Color(0xFFFFEDC7),
            onSecondaryContainer = Color(0xFF5B3A00),
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFE8EDF5),
        ),
        content = content,
    )
}

/** App-wide state; every change is written straight to disk. */
class AppState(val repo: TripRepo) {
    var trips by mutableStateOf(repo.load())
        private set
    var people by mutableStateOf(repo.people)
        private set
    var home by mutableStateOf(repo.home)
        private set
    var title by mutableStateOf(repo.reportTitle)
        private set

    fun upsert(t: Trip) {
        trips = TripRepo.sort(trips.filter { it.id != t.id } + t)
        repo.save(trips)
    }

    fun delete(t: Trip) {
        trips = trips.filter { it.id != t.id }
        repo.save(trips)
    }

    fun replaceAll(list: List<Trip>) {
        trips = TripRepo.sort(list)
        repo.save(trips)
    }

    fun nextId(): Long = maxOf(System.currentTimeMillis(), (trips.maxOfOrNull { it.id } ?: 0L) + 1)

    fun addPerson(name: String) {
        val n = name.trim()
        if (n.isEmpty() || people.any { it.equals(n, true) }) return
        people = people + n
        repo.people = people
    }

    fun removePerson(name: String) {
        people = people - name
        repo.people = people
    }

    fun updateHome(v: String) {
        home = v.trim().ifEmpty { "Ghubrah" }
        repo.home = home
    }

    fun updateTitle(v: String) {
        title = v.trim().ifEmpty { "Site Travel & Expense Tracker" }
        repo.reportTitle = title
    }

    /** Most used first. */
    val sites: List<String>
        get() = trips.map { it.site }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    val places: List<String>
        get() = (trips.flatMap { listOf(it.from, it.to) } + home).filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun tripsOn(date: LocalDate) = trips.filter { it.date == date }
}

// ---------------------------------------------------------------- file helpers

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
    context.startActivity(Intent.createChooser(send, "Share report"))
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

const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val MIME_PDF = "application/pdf"
