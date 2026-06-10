package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mrp: Double,
    val buyPrice: Double,
    val sellPrice: Double,
    val expiryTimestamp: Long,
    val stockQty: Int = 0,
    val batchNumber: String = "",
    val photoUri: String = ""
)
