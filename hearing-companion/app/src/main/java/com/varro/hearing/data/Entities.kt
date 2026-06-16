package com.varro.hearing.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An action the automation engine can apply to the aids. */
enum class HaAction { VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, SET_VOLUME, SET_PROGRAM, NEXT_PROGRAM }

/** A row in the usage diary — every setting change, manual or automated. */
@Entity(tableName = "diary")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val source: String,          // "manual", "schedule", "geofence", "ab-test"
    val action: String,          // human-readable, e.g. "Volume up" / "Program 2"
    val detail: String = "",     // optional context (place name, hex, etc.)
)

/** A time-of-day automation rule. */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,           // bit 0=Sun … bit 6=Sat; 0 = every day
    val action: HaAction,
    val param: Int = 0,          // volume level or program index
    val enabled: Boolean = true,
)

/** A saved location that switches settings when entered. */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 120f,
    val onEnter: HaAction,
    val param: Int = 0,
    val enabled: Boolean = true,
)
