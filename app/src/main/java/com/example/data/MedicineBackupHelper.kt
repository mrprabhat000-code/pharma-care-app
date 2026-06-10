package com.example.data

import android.content.Context
import android.util.Base64
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MedicineBackupHelper {

    fun exportToJsonString(medicines: List<Medicine>): String {
        val jsonArray = JSONArray()
        for (med in medicines) {
            val jsonObject = JSONObject().apply {
                put("name", med.name)
                put("mrp", med.mrp)
                put("buyPrice", med.buyPrice)
                put("sellPrice", med.sellPrice)
                put("expiryTimestamp", med.expiryTimestamp)
                put("stockQty", med.stockQty)
                put("batchNumber", med.batchNumber)
                put("photoUri", med.photoUri)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(4) // Beautifully formatted/indented JSON
    }

    fun exportToJsonString(context: Context, medicines: List<Medicine>): String {
        val jsonArray = JSONArray()
        for (med in medicines) {
            val jsonObject = JSONObject().apply {
                put("name", med.name)
                put("mrp", med.mrp)
                put("buyPrice", med.buyPrice)
                put("sellPrice", med.sellPrice)
                put("expiryTimestamp", med.expiryTimestamp)
                put("stockQty", med.stockQty)
                put("batchNumber", med.batchNumber)
                put("photoUri", med.photoUri)

                try {
                    if (med.photoUri.isNotEmpty()) {
                        if (med.photoUri.startsWith("/")) {
                            val file = File(med.photoUri)
                            if (file.exists()) {
                                val bytes = file.readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                put("photoBase64", base64)
                            }
                        } else if (med.photoUri.startsWith("content://")) {
                            val uri = Uri.parse(med.photoUri)
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                put("photoBase64", base64)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(4)
    }

    fun importFromJsonString(jsonString: String): List<Medicine> {
        val list = mutableListOf<Medicine>()
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jsonArray = if (trimmed.startsWith("{")) {
            // Support single wrapped objects just in case
            val obj = JSONObject(trimmed)
            JSONArray().apply { put(obj) }
        } else {
            JSONArray(trimmed)
        }

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(
                Medicine(
                    name = jsonObject.getString("name"),
                    mrp = jsonObject.optDouble("mrp", 0.0),
                    buyPrice = jsonObject.optDouble("buyPrice", 0.0),
                    sellPrice = jsonObject.optDouble("sellPrice", 0.0),
                    expiryTimestamp = jsonObject.getLong("expiryTimestamp"),
                    stockQty = jsonObject.optInt("stockQty", 0),
                    batchNumber = jsonObject.optString("batchNumber", ""),
                    photoUri = jsonObject.optString("photoUri", "")
                )
            )
        }
        return list
    }

    fun importFromJsonString(context: Context, jsonString: String): List<Medicine> {
        val list = mutableListOf<Medicine>()
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jsonArray = if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            JSONArray().apply { put(obj) }
        } else {
            JSONArray(trimmed)
        }

        val directory = File(context.filesDir, "medicine_images")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            
            var photoUri = jsonObject.optString("photoUri", "")
            val photoBase64 = jsonObject.optString("photoBase64", "")
            
            if (photoBase64.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(photoBase64, Base64.NO_WRAP)
                    val newFile = File(directory, "imported_img_${System.currentTimeMillis()}_$i.png")
                    newFile.writeBytes(bytes)
                    photoUri = newFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            list.add(
                Medicine(
                    name = jsonObject.getString("name"),
                    mrp = jsonObject.optDouble("mrp", 0.0),
                    buyPrice = jsonObject.optDouble("buyPrice", 0.0),
                    sellPrice = jsonObject.optDouble("sellPrice", 0.0),
                    expiryTimestamp = jsonObject.getLong("expiryTimestamp"),
                    stockQty = jsonObject.optInt("stockQty", 0),
                    batchNumber = jsonObject.optString("batchNumber", ""),
                    photoUri = photoUri
                )
            )
        }
        return list
    }
}
