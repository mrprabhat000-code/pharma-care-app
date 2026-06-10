package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MedicineFilter {
    ALL,
    ACTIVE,
    NEAR_EXPIRY,
    EXPIRED,
    LOW_STOCK
}

class MedicineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MedicineRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MedicineRepository(database.medicineDao())
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(MedicineFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    val medicines: StateFlow<List<Medicine>> = repository.allMedicines
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredMedicines: StateFlow<List<Medicine>> = combine(
        medicines,
        searchQuery,
        selectedFilter
    ) { medicineList, query, filter ->
        val q = query.trim().lowercase()
        val searched = if (q.isEmpty()) {
            medicineList
        } else {
            medicineList.filter { 
                it.name.lowercase().contains(q) || it.batchNumber.lowercase().contains(q)
            }
        }

        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + 30L * 24 * 60 * 60 * 1000

        when (filter) {
            MedicineFilter.ALL -> searched
            MedicineFilter.ACTIVE -> searched.filter { it.expiryTimestamp >= now }
            MedicineFilter.NEAR_EXPIRY -> searched.filter { 
                it.expiryTimestamp in now..thirtyDaysFromNow
            }
            MedicineFilter.EXPIRED -> searched.filter { it.expiryTimestamp < now }
            MedicineFilter.LOW_STOCK -> searched.filter { it.stockQty <= 5 }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Analytics state
    val totalCount: StateFlow<Int> = medicines.mapState { it.size }
    
    val expiredCount: StateFlow<Int> = medicines.mapState { list ->
        val now = System.currentTimeMillis()
        list.count { it.expiryTimestamp < now }
    }
    
    val nearExpiryCount: StateFlow<Int> = medicines.mapState { list ->
        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + 30L * 24 * 60 * 60 * 1000
        list.count { it.expiryTimestamp in now..thirtyDaysFromNow }
    }
    
    val lowStockCount: StateFlow<Int> = medicines.mapState { list ->
        list.count { it.stockQty <= 5 }
    }

    val totalInvestment: StateFlow<Double> = medicines.mapState { list ->
        list.sumOf { it.buyPrice * it.stockQty }
    }

    val totalPotentialRevenue: StateFlow<Double> = medicines.mapState { list ->
        list.sumOf { it.sellPrice * it.stockQty }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: MedicineFilter) {
        _selectedFilter.value = filter
    }

    fun addMedicine(
        name: String,
        mrp: Double,
        buyPrice: Double,
        sellPrice: Double,
        expiryTimestamp: Long,
        stockQty: Int,
        batchNumber: String,
        photoUri: String
    ) {
        viewModelScope.launch {
            repository.insertMedicine(
                Medicine(
                    name = name,
                    mrp = mrp,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    expiryTimestamp = expiryTimestamp,
                    stockQty = stockQty,
                    batchNumber = batchNumber,
                    photoUri = photoUri
                )
            )
        }
    }

    fun updateMedicine(
        id: Int,
        name: String,
        mrp: Double,
        buyPrice: Double,
        sellPrice: Double,
        expiryTimestamp: Long,
        stockQty: Int,
        batchNumber: String,
        photoUri: String
    ) {
        viewModelScope.launch {
            repository.updateMedicine(
                Medicine(
                    id = id,
                    name = name,
                    mrp = mrp,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    expiryTimestamp = expiryTimestamp,
                    stockQty = stockQty,
                    batchNumber = batchNumber,
                    photoUri = photoUri
                )
            )
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.deleteMedicine(medicine)
        }
    }

    fun getExportJson(context: android.content.Context): String {
        return com.example.data.MedicineBackupHelper.exportToJsonString(context, medicines.value)
    }

    fun batchImport(importedList: List<Medicine>, replaceExisting: Boolean, onComplete: (successCount: Int, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (replaceExisting) {
                    repository.deleteAllMedicines()
                }
                repository.insertMedicines(importedList)
                onComplete(importedList.size, null)
            } catch (e: Exception) {
                onComplete(0, e.message ?: "Unknown error")
            }
        }
    }

    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
        return this.map { transform(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = transform(this.value)
            )
    }
}
