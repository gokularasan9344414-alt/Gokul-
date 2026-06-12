package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Attendance statistic summary for a single member in a month
data class MemberMonthlyReport(
    val member: Member,
    val totalDays: Int,
    val daysPresent: Int,
    val daysAbsent: Int,
    val daysLate: Int,
    val daysOnLeave: Int,
    val attendanceRate: Double // e.g., 85.5%
)

data class MonthlyReportSummary(
    val monthYear: String, // "YYYY-MM"
    val totalMembers: Int,
    val overallAttendanceRate: Double,
    val averagePresentCount: Double,
    val averageAbsentCount: Double,
    val averageLateCount: Double,
    val averageOnLeaveCount: Double,
    val memberSummaries: List<MemberMonthlyReport>
)

class AttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthYearFormatter = SimpleDateFormat("yyyy-MM", Locale.US)

    // Current selected date state
    private val _selectedDate = MutableStateFlow(dateFormatter.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Sync state and Online status direct exposure
    val syncState: StateFlow<SyncStatus> = repository.syncState
    val isOnline: StateFlow<Boolean> = repository.isOnline

    // All available members/employees
    val members: StateFlow<List<Member>> = repository.allMembers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All attendance records
    val allAttendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive count of unsynced records
    val unsyncedCount: StateFlow<Int> = repository.allAttendance
        .map { records -> records.count { !it.isSynced } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Attendance records for the selected date
    val selectedDateAttendance: StateFlow<List<AttendanceRecord>> = _selectedDate
        .flatMapLatest { date -> repository.getAttendanceForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of combined member-with-attendance states for the SELECTED DATE
    val selectedDateMembersWithAttendance: StateFlow<List<MemberWithAttendance>> = combine(
        members,
        selectedDateAttendance
    ) { memberList, attendanceList ->
        memberList.map { member ->
            val record = attendanceList.find { it.memberId == member.id }
            MemberWithAttendance(member, record)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Month & Year state filter for generating monthly reports
    private val _selectedReportMonthYear = MutableStateFlow(monthYearFormatter.format(Date()))
    val selectedReportMonthYear: StateFlow<String> = _selectedReportMonthYear.asStateFlow()

    // AUTOMATIC Monthly Report generation mapping
    val monthlyReport: StateFlow<MonthlyReportSummary?> = combine(
        members,
        allAttendance,
        _selectedReportMonthYear
    ) { memberList, attendanceList, monthStr ->
        if (memberList.isEmpty()) return@combine null

        // Filter attendance records to match the selected month (YYYY-MM)
        val monthRecords = attendanceList.filter { it.date.startsWith(monthStr) }

        // Find match days present in that month
        val datesInMonth = monthRecords.map { it.date }.distinct()
        val totalRecordedDays = datesInMonth.size.coerceAtLeast(1)

        val memberSummaries = memberList.map { member ->
            val memberRecords = monthRecords.filter { it.memberId == member.id }
            
            val presentCount = memberRecords.count { it.status == "Present" }
            val lateCount = memberRecords.count { it.status == "Late" }
            val absentCount = memberRecords.count { it.status == "Absent" }
            val leaveCount = memberRecords.count { it.status == "On Leave" }
            
            // Worked/accounted days for this member is totalRecordedDays, or can be count of records
            val totalDays = memberRecords.size
            val presentOrLateValue = presentCount + lateCount
            val attendanceRate = if (totalDays > 0) {
                (presentOrLateValue.toDouble() / totalDays.toDouble()) * 100.0
            } else {
                100.0 // Default to 100% attendance if no logs logged
            }

            MemberMonthlyReport(
                member = member,
                totalDays = totalDays,
                daysPresent = presentCount,
                daysAbsent = absentCount,
                daysLate = lateCount,
                daysOnLeave = leaveCount,
                attendanceRate = attendanceRate
            )
        }

        val overallAttendanceSum = memberSummaries.map { it.attendanceRate }.sum()
        val overallRate = if (memberList.isNotEmpty()) overallAttendanceSum / memberList.size else 0.0

        val averagePresent = memberSummaries.map { it.daysPresent }.average()
        val averageAbsent = memberSummaries.map { it.daysAbsent }.average()
        val averageLate = memberSummaries.map { it.daysLate }.average()
        val averageLeave = memberSummaries.map { it.daysOnLeave }.average()

        MonthlyReportSummary(
            monthYear = monthStr,
            totalMembers = memberList.size,
            overallAttendanceRate = overallRate,
            averagePresentCount = averagePresent,
            averageAbsentCount = averageAbsent,
            averageLateCount = averageLate,
            averageOnLeaveCount = averageLeave,
            memberSummaries = memberSummaries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Run seed check when ViewModel begins
        seedDatabaseIfEmpty()
    }

    private fun seedDatabaseIfEmpty() {
        viewModelScope.launch {
            // Check if members table is empty
            repository.allMembers.first().let { currentMembers ->
                if (currentMembers.isEmpty()) {
                    // Seed some team members
                    val seedMembers = listOf(
                        Member(name = "Alice Rivera", role = "UI/UX Designer", email = "alice.r@company.co", joinDate = "2026-01-10"),
                        Member(name = "Brandon Chen", role = "Android Engineer", email = "b.chen@company.co", joinDate = "2026-02-15"),
                        Member(name = "Clara Vance", role = "Product Manager", email = "clara.v@company.co", joinDate = "2026-03-01"),
                        Member(name = "Diana Patel", role = "QA Specialist", email = "diana.p@company.co", joinDate = "2026-04-12"),
                        Member(name = "Evan Wright", role = "Backend Lead", email = "e.wright@company.co", joinDate = "2026-01-05")
                    )

                    val addedMemberIds = mutableListOf<Long>()
                    for (member in seedMembers) {
                        val id = repository.insertMember(member)
                        addedMemberIds.add(id)
                    }

                    // Pre-populate some historical logs for the current month to show beautiful dashboard stats automatically
                    val calendar = Calendar.getInstance()
                    val currentMonthPrefix = monthYearFormatter.format(calendar.time) // "YYYY-MM"
                    val todayDayNum = calendar.get(Calendar.DAY_OF_MONTH)

                    // Seed logs for the first 10 days of this month (or whatever day we are currently in)
                    val sampleDateFormatter = SimpleDateFormat("yyyy-MM-", Locale.US)
                    val datePrefixCombined = sampleDateFormatter.format(calendar.time)

                    val statuses = listOf("Present", "Present", "Late", "Present", "On Leave", "Absent")
                    val times = listOf("09:05 AM", "08:52 AM", "09:32 AM", "08:45 AM", null, null)

                    for (dayNum in 1 until todayDayNum) {
                        val paddedDay = String.format(Locale.US, "%02d", dayNum)
                        val pastDateStr = "$datePrefixCombined$paddedDay"

                        for ((index, memberId) in addedMemberIds.withIndex()) {
                            // Skip weekend days to keep it realistic
                            val testCal = Calendar.getInstance()
                            val dayInt = Integer.parseInt(paddedDay)
                            testCal.set(Calendar.DAY_OF_MONTH, dayInt)
                            val dayOfWeek = testCal.get(Calendar.DAY_OF_WEEK)
                            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                                continue
                            }

                            // Pick a slightly deterministic but organic status per worker
                            val statusIndex = (index + dayNum) % statuses.size
                            val status = statuses[statusIndex]
                            val time = if (status in listOf("Present", "Late")) times[statusIndex] else null
                            val notes = if (status == "Late") "Heavy traffic" else if (status == "On Leave") "Sabbatical" else null

                            // Save as pre-synced records
                            val record = AttendanceRecord(
                                memberId = memberId.toInt(),
                                date = pastDateStr,
                                status = status,
                                checkInTime = time,
                                notes = notes,
                                isSynced = true
                            )
                            repository.recordAttendance(
                                memberId = record.memberId,
                                date = record.date,
                                status = record.status,
                                checkInTime = record.checkInTime,
                                notes = record.notes
                            )
                        }
                    }
                }
            }
        }
    }

    fun enrollMember(name: String, role: String, email: String) {
        viewModelScope.launch {
            val dateStr = dateFormatter.format(Date())
            repository.insertMember(Member(name = name, role = role, email = email, joinDate = dateStr))
        }
    }

    fun removeMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    fun updateSelectedDate(newDate: String) {
        _selectedDate.value = newDate
    }

    fun shiftSelectedDate(days: Int) {
        try {
            val date = dateFormatter.parse(_selectedDate.value) ?: return
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DAY_OF_MONTH, days)
            _selectedDate.value = dateFormatter.format(cal.time)
        } catch (e: Exception) {
            // fallback
        }
    }

    fun updateSelectedReportMonth(offset: Int) {
        try {
            val date = monthYearFormatter.parse(_selectedReportMonthYear.value) ?: return
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.MONTH, offset)
            _selectedReportMonthYear.value = monthYearFormatter.format(cal.time)
        } catch (e: Exception) {
            // fallback
        }
    }

    fun markAttendance(memberId: Int, status: String, checkInTime: String? = null, notes: String? = null) {
        viewModelScope.launch {
            repository.recordAttendance(
                memberId = memberId,
                date = _selectedDate.value,
                status = status,
                checkInTime = checkInTime,
                notes = notes
            )
        }
    }

    fun toggleOnlineMode() {
        repository.setOnlineStatus(!repository.isOnline.value)
    }

    fun triggerForceSync() {
        viewModelScope.launch {
            repository.forceSync()
        }
    }
}

class AttendanceViewModelFactory(private val repository: AttendanceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
