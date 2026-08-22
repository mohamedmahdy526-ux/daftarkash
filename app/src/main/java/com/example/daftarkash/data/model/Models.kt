package com.example.daftarkash.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val creditLimit: Double = 0.0,
    val createdAt: String = "",
    val isArchived: Int = 0
)

@Immutable
@Serializable
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val type: String, // "DEBT" or "PAYMENT"
    val amount: Double,
    val description: String = "",
    val paymentMethod: String = "CASH", // "CASH", "VODAFONE_CASH", "BANK", "CARD"
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String = "",
    val price: Double = 0.0,
    val category: String = "عام"
)

@Serializable
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey
    val key: String,
    val value: String
)

data class PosCartItem(
    val barcode: String,
    val name: String,
    val price: Double,
    var qty: Int = 1
)
