@file:OptIn(ExperimentalMaterial3Api::class)

package com.salim.money

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home),
    Txns("Transactions", AppIcons.Receipt),
    Budget("Budget", AppIcons.Target),
    Reports("Reports", AppIcons.Pie),
    Settings("Settings", AppIcons.Wallet),
}

/** What the full-screen editor is showing. */
sealed interface Editor {
    data class New(val kind: Kind) : Editor
    data class Edit(val txn: Txn) : Editor
}

@Composable
fun App(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = Tab.entries[tab]
    val context = LocalContext.current

    // Ask once for permission to show budget alerts (Android 13+).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && state.settings.notifications && !Notifier.canNotify(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = current != Tab.Home) { tab = 0 }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Header(
                state = state,
                tab = current,
                onSettings = { tab = Tab.Settings.ordinal },
                onBack = { tab = 0 },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.entries.filter { it != Tab.Settings }.forEach { t ->
                    NavigationBarItem(
                        selected = current == t,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Indigo,
                            selectedTextColor = Indigo,
                            indicatorColor = Color(0xFFE0E7FF),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (current != Tab.Settings) {
                ExtendedFloatingActionButton(
                    onClick = { editor = Editor.New(Kind.EXPENSE) },
                    containerColor = Indigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add", fontWeight = FontWeight.Bold) },
                )
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            val open: (Txn) -> Unit = { editor = Editor.Edit(it) }
            val add: (Kind) -> Unit = { editor = Editor.New(it) }
            when (current) {
                Tab.Home -> HomeScreen(state, open, add, goTo = { tab = it.ordinal })
                Tab.Txns -> TransactionsScreen(state, open)
                Tab.Budget -> BudgetScreen(state)
                Tab.Reports -> ReportsScreen(state)
                Tab.Settings -> SettingsScreen(state, snackbar)
            }
        }
    }

    editor?.let { e ->
        TxnEditor(state, e, snackbar, scope, onClose = { editor = null })
    }

    state.notice?.let { n -> BudgetNoticeDialog(state, n) { state.notice = null } }
}

@Composable
private fun Header(state: AppState, tab: Tab, onSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HeaderBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = if (tab == Tab.Home) 0.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
            if (tab == Tab.Settings) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            } else {
                Spacer(Modifier.padding(start = 8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(state.settings.appName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    if (tab == Tab.Home) "Income • Expense • Budget" else tab.label,
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                )
            }
            if (tab != Tab.Settings) {
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White) }
            }
        }
        if (tab != Tab.Settings) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MonthSwitcher(state.month, { state.month = it })
            }
        }
    }
}

@Composable
private fun BudgetNoticeDialog(state: AppState, n: BudgetNotice, onDismiss: () -> Unit) {
    val s = n.status
    val name = n.category?.let { "${it.emoji} ${it.name}" } ?: "💰 Monthly budget"
    val (emoji, headline, color) = when (s.level) {
        Level.OVER -> Triple("🚨", "Budget exceeded!", ExpenseRed)
        Level.WARN -> Triple("⚠️", "Careful – budget almost used", WarnAmber)
        Level.OK -> Triple("✅", "You're on track", IncomeGreen)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK, got it", fontWeight = FontWeight.Bold) } },
        icon = { Text(emoji, fontSize = 36.sp) },
        title = { Text(headline, color = color, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                BudgetBar(s.ratio, marker = s.expectedByToday / s.budget)
                Row {
                    Text("Used ${state.money(s.spent)}", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("of ${state.money(s.budget)}", color = Muted, fontSize = 13.sp)
                }
                if (s.remaining >= 0) {
                    Text(
                        "Only ${state.money(s.remaining)} left for this month.",
                        color = Ink, fontWeight = FontWeight.SemiBold,
                    )
                    if (s.daysLeft > 0) {
                        Text(
                            "${s.daysLeft} day${if (s.daysLeft == 1) "" else "s"} left → you can spend about " +
                                "${state.money(s.perDay)} per day.",
                            color = Ink,
                        )
                    }
                } else {
                    Text(
                        "You are over by ${state.money(-s.remaining)}. Try to avoid more spending here this month.",
                        color = ExpenseRed, fontWeight = FontWeight.SemiBold,
                    )
                }
                n.overall?.let { o ->
                    Spacer(Modifier.height(4.dp))
                    Text("Whole month budget", fontWeight = FontWeight.Bold, color = Ink, fontSize = 13.sp)
                    BudgetBar(o.ratio, height = 8.dp)
                    Text(
                        if (o.remaining >= 0) "${state.money(o.remaining)} left • ${state.money(o.perDay)}/day"
                        else "Over by ${state.money(-o.remaining)}",
                        color = Muted, fontSize = 12.sp,
                    )
                }
            }
        },
        containerColor = Color.White,
    )
}
