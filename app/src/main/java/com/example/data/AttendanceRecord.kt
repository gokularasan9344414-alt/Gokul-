package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["memberId"]),
        Index(value = ["date", "memberId"], unique = true)
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: Int,
    val date: String, // YYYY-MM-DD
    val status: String, // "Present", "Absent", "Late", "On Leave"
    val checkInTime: String? = null, // "HH:MM AM/PM"
    val notes: String? = null,
    val isSynced: Boolean = false // Track sync state
)
