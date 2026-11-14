@file:OptIn(ExperimentalMaterial3Api::class)

package com.salim.money

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home),
    Txns("Entries", AppIcons.Receipt),
    Dues("Dues", AppIcons.Swap),
    Budget("Budget", AppIcons.Target),
    Reports("Reports", AppIcons.Pie),
    Settings("Settings", AppIcons.Wallet),
}

/** What the full-screen transaction editor is showing. */
sealed interface Editor {
    data class New(val kind: Kind, val reimbursable: Boolean = false) : Editor
    data class Edit(val txn: Txn) : Editor
}

/** What the loan editor is showing. */
data class LoanEdit(val entry: LoanEntry?, val person: String, val gave: Boolean)

@Composable
fun App(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var loanEdit by remember { mutableStateOf<LoanEdit?>(null) }
    var quickAdd by remember { mutableStateOf(false) }
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }
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

    val openTxn: (Txn) -> Unit = { editor = Editor.Edit(it) }
    val addTxn: (Kind, Boolean) -> Unit = { k, r -> editor = Editor.New(k, r) }
    val openLoan: (LoanEdit) -> Unit = { loanEdit = it }

    // Root box: full-screen editors are drawn on top of the Scaffold, inside the app window.
    Box(Modifier.fillMaxSize()) {
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
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFFE0E7FF),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (current != Tab.Settings) {
                FloatingActionButton(
                    onClick = { quickAdd = true },
                    containerColor = Indigo,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(62.dp),
                ) { Icon(Icons.Filled.Add, "Add", Modifier.size(30.dp)) }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            when (current) {
                Tab.Home -> HomeScreen(state, openTxn, addTxn, openLoan, goTo = { tab = it.ordinal })
                Tab.Txns -> TransactionsScreen(state, openTxn)
                Tab.Dues -> DuesScreen(state, snackbar, scope, openTxn, openLoan) { openPerson = it }
                Tab.Budget -> BudgetScreen(state)
                Tab.Reports -> ReportsScreen(state)
                Tab.Settings -> SettingsScreen(state, snackbar)
            }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onPick = { choice ->
                quickAdd = false
                when (choice) {
                    0 -> addTxn(Kind.EXPENSE, false)
                    1 -> addTxn(Kind.INCOME, false)
                    2 -> addTxn(Kind.EXPENSE, true)
                    3 -> openLoan(LoanEdit(null, "", gave = true))
                    4 -> openLoan(LoanEdit(null, "", gave = false))
                }
            },
        )
    }

    openPerson?.let { name ->
        val p = state.person(name)
        if (p == null) LaunchedEffect(name) { openPerson = null }
        else PersonDetail(state, p, snackbar, scope, openLoan, onRenamed = { openPerson = it }) { openPerson = null }
    }

    editor?.let { e ->
        TxnEditor(state, e, snackbar, scope, onClose = { editor = null })
    }

    loanEdit?.let { l ->
        LoanEditor(state, l, snackbar, scope, onClose = { loanEdit = null })
    }
    }

    state.notice?.let { n -> BudgetNoticeDialog(state, n) { state.notice = null } }
}

@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("What do you want to add?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("💸", "Expense", "My own spending", ExpenseRed, Modifier.weight(1f)) { onPick(0) }
                QuickTile("💰", "Income", "Salary & other", IncomeGreen, Modifier.weight(1f)) { onPick(1) }
            }
            QuickTile(
                "🏢", "Company expense", "I paid for work (taxi etc.) – company pays me back later",
                Sky, Modifier.fillMaxWidth(),
            ) { onPick(2) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("🤝", "I gave / lent", "Someone owes me", Teal, Modifier.weight(1f)) { onPick(3) }
                QuickTile("🙏", "I got / borrowed", "I owe someone", WarnAmber, Modifier.weight(1f)) { onPick(4) }
            }
        }
    }
}

@Composable
private fun QuickTile(emoji: String, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Ink)
            Text(sub, fontSize = 11.sp, color = Muted, lineHeight = 14.sp)
        }
    }
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(54.dp)) {
            if (tab == Tab.Settings) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            } else {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CardBrush),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Wallet, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(state.settings.appName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(
                    if (tab == Tab.Home) "Personal finance manager" else tab.label,
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )
            }
            if (tab != Tab.Settings) {
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White) }
            }
        }
        if (tab != Tab.Settings && tab != Tab.Dues) {
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
