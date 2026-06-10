package com.example.data

import kotlinx.coroutines.flow.Flow

class MedicineRepository(private val medicineDao: MedicineDao) {
    val allMedicines: Flow<List<Medicine>> = medicineDao.getAllMedicines()

    suspend fun insertMedicine(medicine: Medicine): Long {
        return medicineDao.insertMedicine(medicine)
    }

    suspend fun updateMedicine(medicine: Medicine) {
        medicineDao.updateMedicine(medicine)
    }

    suspend fun deleteMedicine(medicine: Medicine) {
        medicineDao.deleteMedicine(medicine)
    }

    suspend fun deleteMedicineById(id: Int) {
        medicineDao.deleteMedicineById(id)
    }

    suspend fun deleteAllMedicines() {
        medicineDao.deleteAllMedicines()
    }

    suspend fun insertMedicines(medicines: List<Medicine>) {
        medicineDao.insertMedicines(medicines)
    }
}
