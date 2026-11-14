@file:OptIn(ExperimentalLayoutApi::class)

package com.salim.money

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings
    var editCat by remember { mutableStateOf<Category?>(null) }
    var newCatKind by remember { mutableStateOf<Kind?>(null) }
    var catKind by rememberSaveable { mutableStateOf(Kind.EXPENSE.name) }
    var confirmReset by remember { mutableStateOf(false) }
    var restoreData by remember { mutableStateOf<Store?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(context, "Allow notifications in phone settings to get alerts", Toast.LENGTH_LONG).show()
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeToUri(context, uri, StoreJson.toJson(state.store).toByteArray())
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                restoreData = StoreJson.fromJson(text)
            } catch (e: Exception) {
                Toast.makeText(context, "Not a valid backup file", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("General")
        WhiteCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var name by remember { mutableStateOf(s.appName) }
                var cur by remember { mutableStateOf(s.currency) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(28) },
                    label = { Text("App name (shown at the top & on reports)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cur, onValueChange = { cur = it.take(6).uppercase() },
                        label = { Text("Currency") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Decimals", fontSize = 12.sp, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 2, 3).forEach { dd ->
                                Pill("$dd", s.decimals == dd, Indigo) { state.updateSettings(s.copy(decimals = dd)) }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OMR", "SAR", "AED", "QAR", "KWD", "BHD", "INR").forEach { c ->
                        Pill(c, cur == c, Violet) {
                            cur = c
                            val dec = if (c in listOf("OMR", "KWD", "BHD")) 3 else 2
                            state.updateSettings(state.settings.copy(currency = c, decimals = dec))
                        }
                    }
                }
                val changed = name.trim() != s.appName || cur.trim() != s.currency
                if (changed) {
                    TextButton(onClick = {
                        state.updateSettings(
                            state.settings.copy(
                                appName = name.trim().ifEmpty { "My Wallet" },
                                currency = cur.trim().ifEmpty { "OMR" },
                            ),
                        )
                        scope.launch { snackbar.showSnackbar("Saved") }
                    }) { Text("Save changes", fontWeight = FontWeight.Bold) }
                }
            }
        }

        SectionTitle("Budget alerts")
        WhiteCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ToggleRow(
                    "Message after each expense",
                    "Shows what's left in the budget and how much you can spend per day",
                    s.budgetPopup,
                ) { state.updateSettings(s.copy(budgetPopup = it)) }
                HorizontalDivider(color = Paper)
                ToggleRow(
                    "Phone notifications",
                    "Alert at 80% and when a budget is exceeded",
                    s.notifications,
                ) {
                    state.updateSettings(s.copy(notifications = it))
                    if (it && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }

        SectionTitle("Categories")
        WhiteCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Expense", catKind == Kind.EXPENSE.name, ExpenseRed) { catKind = Kind.EXPENSE.name }
                    Pill("Income", catKind == Kind.INCOME.name, IncomeGreen) { catKind = Kind.INCOME.name }
                }
                val kind = Kind.valueOf(catKind)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.categoriesOf(kind).forEach { c ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(c.col().copy(alpha = 0.14f))
                                .clickable { editCat = c }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.emoji)
                            Spacer(Modifier.width(6.dp))
                            Text(c.name, fontSize = 13.sp, color = Ink)
                        }
                    }
                    Text(
                        "+ New",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Indigo)
                            .clickable { newCatKind = kind }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text("Tap a category to rename, change icon/colour or delete it.", fontSize = 12.sp, color = Muted)
            }
        }

        SectionTitle("Payment methods")
        WhiteCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var newMethod by remember { mutableStateOf("") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.methods.forEach { mth ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF3E8FF))
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(mth, fontSize = 13.sp, color = Ink)
                            IconButton(
                                onClick = { if (s.methods.size > 1) state.updateSettings(s.copy(methods = s.methods - mth)) },
                                modifier = Modifier.size(32.dp),
                            ) { Icon(Icons.Filled.Close, "Remove", Modifier.size(14.dp), tint = Muted) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newMethod, onValueChange = { newMethod = it.take(20) },
                        placeholder = { Text("Add method, e.g. Bank Muscat card") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    )
                    TextButton(onClick = {
                        val v = newMethod.trim()
                        if (v.isNotEmpty() && s.methods.none { it.equals(v, true) }) state.updateSettings(s.copy(methods = s.methods + v))
                        newMethod = ""
                    }) { Text("Add") }
                }
            }
        }

        SectionTitle("Backup")
        WhiteCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${state.txns.size} entries saved on this phone. Make a backup file now and then " +
                        "(e.g. save to Google Drive) so nothing is lost if you change phones.",
                    fontSize = 13.sp, color = Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { backupLauncher.launch("wallet-backup-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    ) { Text("Save backup") }
                    OutlinedButton(
                        onClick = {
                            val f = writeReportFile(context, "wallet-backup-${LocalDate.now()}.json", StoreJson.toJson(state.store).toByteArray())
                            shareFile(context, f, "application/json")
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    ) { Text("Share") }
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                ) { Text("Restore from backup…") }
                TextButton(onClick = { confirmReset = true }) { Text("Delete all entries", color = ExpenseRed) }
            }
        }
        Text(
            "Version ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
            fontSize = 11.sp, color = Muted, modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
        )
    }

    editCat?.let { c -> CategoryDialog(state, c, c.kind) { editCat = null } }
    newCatKind?.let { k -> CategoryDialog(state, null, k) { newCatKind = null } }

    restoreData?.let { data ->
        AlertDialog(
            onDismissRequest = { restoreData = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces everything on this phone with the backup (${data.txns.size} entries, ${data.categories.size} categories).") },
            confirmButton = {
                TextButton(onClick = {
                    state.replaceAll(data)
                    restoreData = null
                    scope.launch { snackbar.showSnackbar("Backup restored") }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreData = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete all entries?") },
            text = { Text("All ${state.txns.size} income and expense entries and all budgets will be removed. This cannot be undone. Make a backup first if unsure.") },
            confirmButton = {
                TextButton(onClick = { state.resetAll(); confirmReset = false }) { Text("Delete all", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(sub, fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CategoryDialog(state: AppState, initial: Category?, kind: Kind, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: Defaults.emojis.first()) }
    var color by remember { mutableStateOf(initial?.color ?: Defaults.palette.first()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val preview = Category(initial?.id ?: "", name.ifBlank { "Name" }, emoji, color, kind)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New ${if (kind == Kind.INCOME) "income" else "expense"} category" else "Edit category") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(preview, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24) }, label = { Text("Name") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    )
                }
                Text("Icon", fontSize = 12.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Defaults.emojis.forEach { e ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) Color(color).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                Text("Colour", fontSize = 12.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Defaults.palette.forEach { p ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(p))
                                .then(if (p == color) Modifier.border(3.dp, Ink, CircleShape) else Modifier)
                                .clickable { color = p },
                        )
                    }
                }
                if (initial != null) {
                    val n = state.usage(initial)
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete category", color = ExpenseRed) }
                    if (confirmDelete) {
                        Text(
                            if (n > 0) "$n entries will move to “Other”. Tap again to confirm." else "Tap again to confirm.",
                            fontSize = 12.sp, color = ExpenseRed,
                        )
                        TextButton(onClick = { state.deleteCategory(initial); onDismiss() }) {
                            Text("Yes, delete", color = ExpenseRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    state.saveCategory(
                        Category(
                            id = initial?.id ?: ("c" + System.currentTimeMillis()),
                            name = name.trim(), emoji = emoji, color = color, kind = kind,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color.White,
    )
}
