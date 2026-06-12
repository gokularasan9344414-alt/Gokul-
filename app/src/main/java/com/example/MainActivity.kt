package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local database and core dependencies
        val database = AppDatabase.getDatabase(this)
        val repository = AttendanceRepository(database.memberDao(), database.attendanceDao())

        setContent {
            MyApplicationTheme {
                // Instantiate the ViewModel using our custom factory
                val viewModel: AttendanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = AttendanceViewModelFactory(repository)
                )
                AttendanceApp(viewModel = viewModel)
            }
        }
    }
}

// Simple standard Material Theme alias compatibility check
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    com.example.ui.theme.MyApplicationTheme {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceApp(viewModel: AttendanceViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0: Tracker, 1: Reports, 2: Directory
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isOnline by viewModel.isOnline.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()

    // Observe sync success to raise beautiful floating snacks
    LaunchedEffect(syncState) {
        if (syncState is SyncStatus.Success) {
            val count = (syncState as SyncStatus.Success).syncedCount
            if (count > 0) {
                snackbarHostState.showSnackbar("Real-time Sync: successfully uploaded $count updates to cloud!")
            }
        } else if (syncState is SyncStatus.Error) {
            snackbarHostState.showSnackbar("Sync Error: ${(syncState as SyncStatus.Error).message}")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Attendance",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF1B1B1F)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        viewModel.toggleOnlineMode()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (!isOnline) {
                                                    "Switched to Offline Mode. Local queue activated."
                                                } else {
                                                    "Switched Online. Real-time auto-sync activated."
                                                }
                                            )
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFB3261E))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isOnline) "LIVE SYNCING" else "OFFLINE QUEUE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF44464F)
                                )
                            }
                        }

                        // Toolbar profile button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isSyncing = syncState is SyncStatus.Syncing
                            val rotationTransition = rememberInfiniteTransition(label = "rotation")
                            val angle by rotationTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "spin"
                            )

                            if (unsyncedCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text("$unsyncedCount", fontSize = 9.sp, color = Color.White)
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEADDFF))
                                    .clickable {
                                        if (isOnline) {
                                            viewModel.triggerForceSync()
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Offline: Reconnect to force sync database logs.")
                                            }
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isSyncing) Icons.Default.Refresh else Icons.Default.Person,
                                    contentDescription = "Profile and Sync Status Indicator",
                                    tint = Color(0xFF21005D),
                                    modifier = if (isSyncing) Modifier.rotate(angle) else Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Queue status bar banner
                    if (unsyncedCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFDADA))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sync Queue Warning",
                                tint = Color(0xFF410002),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$unsyncedCount updates pending locally. Connect online to auto-sync.",
                                fontSize = 11.sp,
                                color = Color(0xFF410002),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = Color(0xFFF2F3F9),
                modifier = Modifier.border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001453),
                        unselectedIconColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        selectedTextColor = Color(0xFF001453),
                        unselectedTextColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        indicatorColor = Color(0xFFDDE1FF)
                    ),
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Daily Attendance Home Page") },
                    label = { Text("Home", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001453),
                        unselectedIconColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        selectedTextColor = Color(0xFF001453),
                        unselectedTextColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        indicatorColor = Color(0xFFDDE1FF)
                    ),
                    icon = { Icon(Icons.Default.List, contentDescription = "Monthly Reports Overview") },
                    label = { Text("History", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001453),
                        unselectedIconColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        selectedTextColor = Color(0xFF001453),
                        unselectedTextColor = Color(0xFF44464F).copy(alpha = 0.6f),
                        indicatorColor = Color(0xFFDDE1FF)
                    ),
                    icon = { Icon(Icons.Default.Person, contentDescription = "Team Directory Setup") },
                    label = { Text("People", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentTab) {
                0 -> TrackerTabScreen(viewModel = viewModel)
                1 -> ReportsTabScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
                2 -> DirectoryTabScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: DAILY TRACKER INDEX SCREEN
// -------------------------------------------------------------
@Composable
fun TrackerTabScreen(viewModel: AttendanceViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val rosterList by viewModel.selectedDateMembersWithAttendance.collectAsState()

    var showDetailDialogForMember by remember { mutableStateOf<MemberWithAttendance?>(null) }
    var showsScannerDialog by remember { mutableStateOf(false) }

    val totalCount = rosterList.size
    val presentCount = rosterList.count { it.attendanceRecord?.status == "Present" }
    val lateCount = rosterList.count { it.attendanceRecord?.status == "Late" }
    val absentCount = rosterList.count { it.attendanceRecord?.status == "Absent" }
    val leaveCount = rosterList.count { it.attendanceRecord?.status == "On Leave" }

    val checkedInCount = presentCount + lateCount

    Column(modifier = Modifier.fillMaxSize()) {
        // Date Selector Row
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.shiftSelectedDate(-1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Day")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // Reset to today
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        viewModel.updateSelectedDate(today)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Date picker button",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatHumanDate(selectedDate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    if (selectedDate == todayStr) {
                        Spacer(modifier = Modifier.width(4.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Today", fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                IconButton(onClick = { viewModel.shiftSelectedDate(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Day")
                }
            }
        }

        if (rosterList.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Empty member roster list visual",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Roster is Empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No staff members enrolled yet. Navigate to the Directory tab to enroll your team members.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Summary Hero Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDDE1FF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "Today's Summary",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF001453)
                                    )
                                    Text(
                                        text = "$checkedInCount / $totalCount",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Light,
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = Color(0xFF001453)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.4f))
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Metrics summary icon visual",
                                        tint = Color(0xFF001453)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "PRESENT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF001453).copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%02d", presentCount),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF001453)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "ABSENT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF001453).copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%02d", absentCount),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB3261E)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "LATE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF001453).copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%02d", lateCount),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                                if (leaveCount > 0) {
                                    Column {
                                        Text(
                                            text = "LEAVE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = Color(0xFF001453).copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%02d", leaveCount),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF44464F)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Quick Action / Scan Badge simulator trigger card
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showsScannerDialog = true
                            }
                            .border(
                                width = 1.dp,
                                color = Color(0xFFCAC4D0),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE8DEF8))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Scan badge proxy scanner icon",
                                        tint = Color(0xFF6750A4),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Scan Badge",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Text(
                                        text = "Touchless check-in active",
                                        fontSize = 12.sp,
                                        color = Color(0xFF44464F)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Right Arrow Icon",
                                tint = Color(0xFF44464F)
                            )
                        }
                    }
                }

                // 3. Recent Activity title header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF44464F)
                        )
                        Text(
                            text = "View All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF005AC1),
                            modifier = Modifier.clickable { }
                        )
                    }
                }

                // 4. Staff roster item rows
                items(rosterList, key = { it.member.id }) { item ->
                    AttendanceMemberCard(
                        item = item,
                        onStatusSelected = { status ->
                            val calendar = Calendar.getInstance()
                            val timeStr = if (status in listOf("Present", "Late")) {
                                SimpleDateFormat("hh:mm a", Locale.US).format(calendar.time)
                            } else null
                            viewModel.markAttendance(
                                memberId = item.member.id,
                                status = status,
                                checkInTime = timeStr,
                                notes = item.attendanceRecord?.notes
                            )
                        },
                        onRowClicked = {
                            showDetailDialogForMember = item
                        }
                    )
                }
            }
        }
    }

    // Badge Scanner Overlay
    if (showsScannerDialog) {
        AlertDialog(
            onDismissRequest = { showsScannerDialog = false },
            title = { Text("Simulate Badge QR/NFC Scan", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text("Select a team member's virtual badge to scan instantly:", fontSize = 13.sp, color = Color(0xFF44464F))
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rosterList) { item ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.markAttendance(
                                            memberId = item.member.id,
                                            status = "Present",
                                            checkInTime = SimpleDateFormat("hh:mm a", Locale.US).format(Date()),
                                            notes = "Scanned via Touchless Badge Reader"
                                        )
                                        showsScannerDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF3F3FA)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEADDFF))
                                    ) {
                                        Text(getInitials(item.member.name), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.member.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1B1F))
                                        Text(item.member.role, fontSize = 11.sp, color = Color(0xFF44464F))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showsScannerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal dialog to update custom timing and additional notes
    val activeDialogMember = showDetailDialogForMember
    if (activeDialogMember != null) {
        val dialogItem = activeDialogMember
        var checkInTime by remember { mutableStateOf(dialogItem.attendanceRecord?.checkInTime ?: "09:00 AM") }
        var notes by remember { mutableStateOf(dialogItem.attendanceRecord?.notes ?: "") }
        var currentStatus by remember { mutableStateOf(dialogItem.attendanceRecord?.status ?: "Present") }

        AlertDialog(
            onDismissRequest = { showDetailDialogForMember = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markAttendance(
                            memberId = dialogItem.member.id,
                            status = currentStatus,
                            checkInTime = if (currentStatus in listOf("Present", "Late")) checkInTime else null,
                            notes = if (notes.isNotBlank()) notes else null
                        )
                        showDetailDialogForMember = null
                    }
                ) {
                    Text("Save Details")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailDialogForMember = null }) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Edit Record: ${dialogItem.member.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Roster Role: ${dialogItem.member.role}", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)

                    // Quick status selector inside the options
                    Text(text = "Attendance Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Present", "Late", "Absent", "On Leave").forEach { stat ->
                            val isSelected = currentStatus == stat
                            FilterChip(
                                selected = isSelected,
                                onClick = { currentStatus = stat },
                                label = { Text(stat, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (currentStatus in listOf("Present", "Late")) {
                        OutlinedTextField(
                            value = checkInTime,
                            onValueChange = { checkInTime = it },
                            label = { Text("Log Checked-in Time") },
                            placeholder = { Text("e.g. 09:15 AM") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = "Time") }, // using standard Refresh icon as proxy clocks
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Additional Log Notes") },
                        placeholder = { Text("e.g. Medical doctor leave / Delayed by train") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

@Composable
fun AttendanceMemberCard(
    item: MemberWithAttendance,
    onStatusSelected: (String) -> Unit,
    onRowClicked: () -> Unit
) {
    val record = item.attendanceRecord
    val status = record?.status

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3F3FA)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClicked() }
            .border(
                width = 1.dp,
                color = Color(0xFFCAC4D0).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Member Information Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar bubble
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                when (status) {
                                    "Present" -> Color(0xFFE2F3E3)
                                    "Late" -> Color(0xFFFFF4DF)
                                    "Absent" -> Color(0xFFFFDADA)
                                    "On Leave" -> Color(0xFFEADDFF)
                                    else -> Color(0xFFE6E1E5)
                                }
                            )
                    ) {
                        Text(
                            text = getInitials(item.member.name),
                            color = when (status) {
                                "Present" -> Color(0xFF2E7D32)
                                "Late" -> Color(0xFFE65100)
                                "Absent" -> Color(0xFFB3261E)
                                "On Leave" -> Color(0xFF381E72)
                                else -> Color(0xFF1D1B20)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = item.member.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == "Absent") Color(0xFFB3261E) else Color(0xFF1B1B1F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (status != null) "$status • ${item.member.role}" else item.member.role,
                            fontSize = 12.sp,
                            color = Color(0xFF44464F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Checked-in / Logged Indicator on right side
                if (record?.checkInTime != null) {
                    Text(
                        text = record.checkInTime ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (status == "Absent") Color(0xFFB3261E) else Color(0xFF44464F),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                } else if (status != null) {
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (status) {
                            "Present" -> Color(0xFF2E7D32)
                            "Late" -> Color(0xFFE65100)
                            "Absent" -> Color(0xFFB3261E)
                            else -> Color(0xFF44464F)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Attendance Action Status Selector Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PRESENT BUTTON
                StatusSelectorButton(
                    label = "Present",
                    isSelected = status == "Present",
                    onColor = Color(0xFF2E7D32),
                    offColor = Color(0xFF2E7D32).copy(alpha = 0.05f),
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusSelected("Present") }
                )

                // LATE BUTTON
                StatusSelectorButton(
                    label = "Late",
                    isSelected = status == "Late",
                    onColor = Color(0xFFE65100),
                    offColor = Color(0xFFE65100).copy(alpha = 0.05f),
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusSelected("Late") }
                )

                // ABSENT BUTTON
                StatusSelectorButton(
                    label = "Absent",
                    isSelected = status == "Absent",
                    onColor = Color(0xFFB3261E),
                    offColor = Color(0xFFB3261E).copy(alpha = 0.05f),
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusSelected("Absent") }
                )

                // LEAVE BUTTON
                StatusSelectorButton(
                    label = "Leave",
                    isSelected = status == "On Leave",
                    onColor = Color(0xFF44464F),
                    offColor = Color(0xFF44464F).copy(alpha = 0.05f),
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusSelected("On Leave") }
                )
            }

            // Sync status badge inside card
            if (record != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (record.isSynced) Color(0xFFE2F2E3) else Color(0xFFFFF2D0)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (record.isSynced) Icons.Default.Check else Icons.Default.Refresh,
                            contentDescription = if (record.isSynced) "Synced" else "Cached locally",
                            modifier = Modifier.size(10.dp),
                            tint = if (record.isSynced) Color(0xFF2E7D32) else Color(0xFFC67C00)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (record.isSynced) "Cloud synced" else "Pending queue",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (record.isSynced) Color(0xFF2E7D32) else Color(0xFFC67C00)
                        )
                    }
                }
            }

            // Expand metadata indicator if recorded
            if (record != null && (!record.notes.isNullOrBlank())) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Notes: ${record.notes}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF44464F)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusSelectorButton(
    label: String,
    isSelected: Boolean,
    onColor: Color,
    offColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isSelected) onColor else onColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(18.dp)
            ),
        color = if (isSelected) onColor else offColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) Color.White else onColor
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 2: AUTOMATIC REPORTS SCREEN
// -------------------------------------------------------------
@Composable
fun ReportsTabScreen(viewModel: AttendanceViewModel, snackbarHostState: SnackbarHostState) {
    val reportMonthYear by viewModel.selectedReportMonthYear.collectAsState()
    val reportSummary by viewModel.monthlyReport.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Report Date Controller Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Reports",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F)
                )
                Text(
                    text = "On-the-fly from local logs",
                    fontSize = 11.sp,
                    color = Color(0xFF44464F)
                )
            }

            // Month Shift Selector Panel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF2F3F9))
                    .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.updateSelectedReportMonth(-1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev Month", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = formatHumanMonth(reportMonthYear).uppercase(Locale.US),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = Color(0xFF1B1B1F)
                )
                IconButton(
                    onClick = { viewModel.updateSelectedReportMonth(1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month", modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reportSummary == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records found to compile report stats.",
                    color = Color(0xFF44464F),
                    fontSize = 14.sp
                )
            }
        } else {
            val monthName = formatHumanMonth(reportMonthYear)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Notice: Report Generated Banner
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFDDE1FF),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF001453))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$monthName REPORT READY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF001453)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.6f))
                                        .clickable {
                                            val reportString = compileReportCSV(reportSummary!!)
                                            clipboardManager.setText(AnnotatedString(reportString))
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Success: copied $monthName spreadsheet CSV to clipboard.")
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "DOWNLOAD",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF001453)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Monthly summary logs compile automatically as attendance status changes occur.",
                                fontSize = 12.sp,
                                color = Color(0xFF001453).copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // KPI DASHBOARD HEADER ROW
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // KPI 1: Overall Percentage with custom Circular Arc Gauger
                        Card(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(125.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3FA)),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini Circular progress indicator
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    val rateFloat = (reportSummary!!.overallAttendanceRate / 100.0).toFloat().coerceIn(0f, 1f)
                                    val animateSweep = remember { Animatable(0f) }
                                    LaunchedEffect(rateFloat) {
                                        animateSweep.animateTo(rateFloat, animationSpec = tween(1000))
                                    }

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = Color(0xFFCAC4D0).copy(alpha = 0.3f),
                                            style = Stroke(width = 5.dp.toPx())
                                        )
                                        drawArc(
                                            color = Color(0xFF2E7D32),
                                            startAngle = -90f,
                                            sweepAngle = animateSweep.value * 360f,
                                            useCenter = false,
                                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Text(
                                        text = "${reportSummary!!.overallAttendanceRate.toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Attendance",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF44464F)
                                    )
                                    Text(
                                        text = "Month Rate",
                                        fontSize = 12.sp,
                                        color = Color(0xFF44464F)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", reportSummary!!.overallAttendanceRate),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B1B1F)
                                    )
                                }
                            }
                        }

                        // KPI 2: Averages list
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(125.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3FA)),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceAround
                            ) {
                                Text(
                                    text = "Monthly Averages",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF44464F)
                                )
                                MetricRow(label = "Present", count = reportSummary!!.averagePresentCount, color = Color(0xFF2E7D32))
                                MetricRow(label = "Late", count = reportSummary!!.averageLateCount, color = Color(0xFFE65100))
                                MetricRow(label = "Absent", count = reportSummary!!.averageAbsentCount, color = Color(0xFFB3261E))
                            }
                        }
                    }
                }

                // DETAILED ROSTER LIST HEADER
                item {
                    Text(
                        text = "Individual Staff Summaries",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1B1B1F),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // INDIVIDUAL LIST CARDS
                items(reportSummary!!.memberSummaries) { element ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp)),
                        color = Color.White
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = element.member.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Text(
                                        text = element.member.role,
                                        fontSize = 12.sp,
                                        color = Color(0xFF44464F)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", element.attendanceRate),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = when {
                                            element.attendanceRate >= 90.0 -> Color(0xFF2E7D32)
                                            element.attendanceRate >= 75.0 -> Color(0xFFE65100)
                                            else -> Color(0xFFB3261E)
                                        }
                                    )
                                    Text(
                                        text = "Attendance Rate",
                                        fontSize = 9.sp,
                                        color = Color(0xFF44464F)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom Linear Bar showing safety/lateness ratio
                            LinearProgressIndicator(
                                progress = { (element.attendanceRate / 100f).toFloat().coerceIn(0f, 1f) },
                                color = when {
                                    element.attendanceRate >= 90.0 -> Color(0xFF2E7D32)
                                    element.attendanceRate >= 75.0 -> Color(0xFFE65100)
                                    else -> Color(0xFFB3261E)
                                },
                                trackColor = Color(0xFFCAC4D0).copy(alpha = 0.25f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Days details logs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DetailStatItem(label = "Present", count = element.daysPresent, dotColor = Color(0xFF2E7D32))
                                DetailStatItem(label = "Late", count = element.daysLate, dotColor = Color(0xFFE65100))
                                DetailStatItem(label = "Absent", count = element.daysAbsent, dotColor = Color(0xFFB3261E))
                                DetailStatItem(label = "Leave", count = element.daysOnLeave, dotColor = Color(0xFF44464F))
                                DetailStatItem(label = "Logged", count = element.totalDays, dotColor = Color(0xFFCAC4D0))
                            }
                        }
                    }
                }

                // EXPORT REPORT ACTION BUTTON
                item {
                    Button(
                        onClick = {
                            val reportString = compileReportCSV(reportSummary!!)
                            clipboardManager.setText(AnnotatedString(reportString))
                            scope.launch {
                                snackbarHostState.showSnackbar("Monthly report successfully copied to clipboard in CSV format!")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001453)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export clipboard option logo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export to Spreadsheet (CSV)")
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, count: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = String.format(Locale.US, "%.1f/d", count),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DetailStatItem(label: String, count: Int, dotColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count d",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// -------------------------------------------------------------
// TAB 3: TEAM DIRECTORY / ENROLLMENT SCREEN
// -------------------------------------------------------------
@Composable
fun DirectoryTabScreen(viewModel: AttendanceViewModel, snackbarHostState: SnackbarHostState) {
    val memberList by viewModel.members.collectAsState()
    val scope = rememberCoroutineScope()

    var showEnrollForm by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var inputRole by remember { mutableStateOf("UI/UX Designer") }
    var inputEmail by remember { mutableStateOf("") }

    val rolesAvailable = listOf("UI/UX Designer", "Android Engineer", "Product Manager", "QA Specialist", "Backend Lead", "HR Consultant")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Directory",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F)
                )
                Text(
                    text = "Manage team rosters",
                    fontSize = 11.sp,
                    color = Color(0xFF44464F)
                )
            }

            Button(
                onClick = { showEnrollForm = !showEnrollForm },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showEnrollForm) Color(0xFFB3261E) else Color(0xFF001453)
                )
            ) {
                Icon(
                    imageVector = if (showEnrollForm) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "New staff registration trigger",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showEnrollForm) "Dismiss" else "Add Staff", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ENROLLMENT FORM SLIDE INPUT
        AnimatedVisibility(
            visible = showEnrollForm,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F3F9)),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enroll New Staff Member",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1B1B1F)
                    )

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Frank Ocean") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Work Email") },
                        placeholder = { Text("e.g. frank.o@company.co") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Role Chip Selector
                    Text(text = "Roster Role", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF44464F))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rolesAvailable.forEach { role ->
                                        val isSelected = inputRole == role
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { inputRole = role },
                                            label = { Text(role, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (inputName.isBlank() || inputEmail.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error: Name and Email cannot be empty.")
                                }
                                return@Button
                            }
                            viewModel.enrollMember(inputName, inputRole, inputEmail)
                            inputName = ""
                            inputEmail = ""
                            showEnrollForm = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Staff member enrolled successfully!")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001453)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add to Roster", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // STAFF DIRECTORY ROSTER LIST
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memberList) { member ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp)),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEADDFF))
                            ) {
                                Text(
                                    getInitials(member.name),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D),
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = member.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B1B1F)
                                )
                                Text(
                                    text = member.role,
                                    fontSize = 12.sp,
                                    color = Color(0xFF44464F)
                                )
                                Text(
                                    text = member.email,
                                    fontSize = 10.sp,
                                    color = Color(0xFF44464F).copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.removeMember(member)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Staff: ${member.name} removed from roster.")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Staff Member",
                                tint = Color(0xFFB3261E)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ANALYTICS & FORMATTING STATIC HELPERS
// -------------------------------------------------------------
fun getInitials(name: String): String {
    if (name.isBlank()) return "ST"
    val parts = name.split(" ")
    return if (parts.size >= 2) {
        "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
    } else {
        name.take(2).uppercase()
    }
}

fun formatHumanDate(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        dateStr
    }
}

fun formatHumanMonth(monthStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthStr) ?: return monthStr
        SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        monthStr
    }
}

fun compileReportCSV(report: MonthlyReportSummary): String {
    val builder = StringBuilder()
    builder.append("=== ATTENDANCE TRACKER MONTHLY REPORT ===\n")
    builder.append("Month: ,${formatHumanMonth(report.monthYear)}\n")
    builder.append("Total Team Size: ,${report.totalMembers}\n")
    builder.append("Overall Attendance Rate: ,${String.format(Locale.US, "%.2f%%", report.overallAttendanceRate)}\n\n")
    builder.append("STAFF ROSTER INDIVIDUAL BREAKDOWNS:\n")
    builder.append("Name,Role,Present Days,Late Days,Absent Days,Leave Days,Logged Days,Attendance Rate\n")

    for (item in report.memberSummaries) {
        builder.append("${item.member.name},")
        builder.append("${item.member.role},")
        builder.append("${item.daysPresent},")
        builder.append("${item.daysLate},")
        builder.append("${item.daysAbsent},")
        builder.append("${item.daysOnLeave},")
        builder.append("${item.totalDays},")
        builder.append(String.format(Locale.US, "%.1f%%\n", item.attendanceRate))
    }
    return builder.toString()
}
