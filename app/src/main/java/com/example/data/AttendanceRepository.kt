package com.example.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val syncedCount: Int) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class AttendanceRepository(
    private val memberDao: MemberDao,
    private val attendanceDao: AttendanceDao
) {
    val allMembers: Flow<List<Member>> = memberDao.getAllMembers()
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()

    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    // Flag to control fake network status (Online/Offline)
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun setOnlineStatus(online: Boolean) {
        _isOnline.value = online
    }

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(date)
    }

    suspend fun insertMember(member: Member): Long {
        return memberDao.insertMember(member)
    }

    suspend fun updateMember(member: Member) {
        memberDao.updateMember(member)
    }

    suspend fun deleteMember(member: Member) {
        // Cascade delete attendance records
        attendanceDao.deleteAttendanceForMember(member.id)
        memberDao.deleteMember(member)
    }

    suspend fun recordAttendance(
        memberId: Int,
        date: String,
        status: String,
        checkInTime: String?,
        notes: String?
    ) {
        // Under Online mode, mark as synced immediately, else mark as unsynced
        val isSynced = _isOnline.value
        val record = AttendanceRecord(
            memberId = memberId,
            date = date,
            status = status,
            checkInTime = checkInTime,
            notes = notes,
            isSynced = isSynced
        )
        attendanceDao.insertOrUpdateAttendance(record)

        // If online and dynamic sync is on, we can trigger auto-sync of any leftovers too
        if (isSynced) {
            triggerSyncBackground()
        }
    }

    suspend fun triggerSyncBackground() {
        val unsynced = attendanceDao.getUnsyncedRecords()
        if (unsynced.isEmpty()) return
        
        _syncState.value = SyncStatus.Syncing
        try {
            // Simulate network delay
            delay(1000)
            val idsToMark = unsynced.map { it.id }
            attendanceDao.markAsSynced(idsToMark)
            _syncState.value = SyncStatus.Success(unsynced.size)
        } catch (e: Exception) {
            _syncState.value = SyncStatus.Error(e.message ?: "Sync failed")
        } finally {
            delay(1500)
            _syncState.value = SyncStatus.Idle
        }
    }

    suspend fun forceSync(): Int {
        if (!_isOnline.value) {
            _syncState.value = SyncStatus.Error("Cannot sync while Offline")
            delay(1500)
            _syncState.value = SyncStatus.Idle
            return 0
        }
        val unsynced = attendanceDao.getUnsyncedRecords()
        if (unsynced.isEmpty()) {
            _syncState.value = SyncStatus.Success(0)
            delay(1500)
            _syncState.value = SyncStatus.Idle
            return 0
        }

        _syncState.value = SyncStatus.Syncing
        delay(1200) // fake upload time
        val idsToMark = unsynced.map { it.id }
        attendanceDao.markAsSynced(idsToMark)
        _syncState.value = SyncStatus.Success(unsynced.size)
        delay(1500)
        _syncState.value = SyncStatus.Idle
        return unsynced.size
    }
}
