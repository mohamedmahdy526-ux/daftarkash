package com.example.daftarkash.data.repository

import com.example.daftarkash.data.db.AppDatabase
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Product
import com.example.daftarkash.data.model.Setting
import com.example.daftarkash.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Immutable
import java.util.concurrent.TimeUnit

@Immutable
data class CustomerWithBalance(
    val customer: Customer,
    val balance: Double,
    val lastTransaction: Transaction? = null
)

@Immutable
data class LedgerMetrics(
    val totalMarketDebt: Double = 0.0,
    val todayCollections: Double = 0.0,
    val debtorsCount: Int = 0,
    val totalCustomersCount: Int = 0,
    val settledCount: Int = 0
)

class DaftarKashRepository(private val database: AppDatabase) {
    private val customerDao = database.customerDao()
    private val transactionDao = database.transactionDao()
    private val productDao = database.productDao()
    private val settingDao = database.settingDao()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private fun executeWithRedirects(request: Request): okhttp3.Response {
        var currentRequest = request
        var attempts = 0
        while (attempts < 5) {
            val response = httpClient.newCall(currentRequest).execute()
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    response.close()
                    val targetUrl = if (location.startsWith("http")) location else {
                        currentRequest.url.resolve(location)?.toString() ?: location
                    }
                    currentRequest = currentRequest.newBuilder()
                        .url(targetUrl)
                        .apply {
                            if (response.code == 303 || (response.code in listOf(301, 302) && currentRequest.method == "POST")) {
                                get()
                            }
                        }
                        .build()
                    attempts++
                    continue
                }
            }
            return response
        }
        return httpClient.newCall(currentRequest).execute()
    }

    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val allSettings: Flow<List<Setting>> = settingDao.getAllSettings()

    fun getCustomer(id: Long): Flow<Customer?> = customerDao.getCustomerById(id)
    fun getCustomerTransactions(customerId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsForCustomer(customerId)

    suspend fun addCustomer(name: String, phone: String, initialDebt: Double): Long = withContext(Dispatchers.IO) {
        val customer = Customer(
            name = name,
            phone = phone,
            createdAt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
        val customerId = customerDao.insertCustomer(customer)
        if (initialDebt > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))
            val now = System.currentTimeMillis()
            transactionDao.insertTransaction(
                Transaction(
                    customerId = customerId,
                    type = "DEBT",
                    amount = initialDebt,
                    description = "رصيد دين سابق عند إنشاء الحساب",
                    date = sdf.format(Date(now)),
                    timestamp = now
                )
            )
        }
        customerId
    }

    suspend fun addTransaction(
        customerId: Long,
        type: String,
        amount: Double,
        description: String,
        paymentMethod: String = "CASH"
    ): Long = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))
        val now = System.currentTimeMillis()
        val tx = Transaction(
            customerId = customerId,
            type = type,
            amount = amount,
            description = description.ifBlank { if (type == "DEBT") "سحب بضاعة" else "سداد نقدي" },
            paymentMethod = paymentMethod,
            date = sdf.format(Date(now)),
            timestamp = now
        )
        transactionDao.insertTransaction(tx)
    }

    suspend fun deleteTransaction(txId: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionById(txId)
    }

    suspend fun updateCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        customerDao.updateCustomer(customer)
    }

    suspend fun deleteCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionsForCustomer(customer.id)
        customerDao.deleteCustomer(customer)
    }

    suspend fun addProduct(name: String, barcode: String, price: Double, category: String = "عام") = withContext(Dispatchers.IO) {
        productDao.insertProduct(
            Product(
                name = name,
                barcode = barcode,
                price = price,
                category = category.ifBlank { "عام" }
            )
        )
    }

    suspend fun updateProductPrice(productId: Long, newPrice: Double) = withContext(Dispatchers.IO) {
        productDao.updatePrice(productId, newPrice)
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    suspend fun findProductByBarcode(barcode: String): Product? = withContext(Dispatchers.IO) {
        productDao.findProductByBarcode(barcode)
    }

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        settingDao.getSettingValue(key)
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.setSetting(Setting(key, value))
    }

    suspend fun performCloudSync(scriptUrlOverride: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scriptUrl = scriptUrlOverride?.ifBlank { null }
                ?: settingDao.getSettingValue("googleScriptUrl")
                ?: return@withContext Result.failure(Exception("رابط Google Apps Script غير محدد"))

            val storeName = settingDao.getSettingValue("storeName") ?: "ماركت أولاد ماهر"
            val customers = customerDao.getAllCustomers().let {
                // retrieve direct list
                database.customerDao().getCustomerCount()
            }

            val allCustomersList = customerDao.getAllCustomersDirect()
            val allTxsList = transactionDao.getAllTransactionsDirect()
            val allProductsList = productDao.getAllProductsDirect()

            val customerMap = allCustomersList.associateBy { it.id }
            val customerBalances = mutableMapOf<Long, Double>()
            for (c in allCustomersList) {
                customerBalances[c.id] = 0.0
            }
            for (t in allTxsList) {
                val current = customerBalances[t.customerId] ?: 0.0
                if (t.type == "DEBT") {
                    customerBalances[t.customerId] = current + t.amount
                } else if (t.type == "PAYMENT") {
                    customerBalances[t.customerId] = current - t.amount
                }
            }

            val jsonCustomers = JSONArray()
            for (c in allCustomersList) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("name", c.name)
                cObj.put("phone", c.phone)
                cObj.put("balance", customerBalances[c.id] ?: 0.0)
                cObj.put("createdAt", c.createdAt)
                cObj.put("address", c.address)
                cObj.put("notes", c.notes)
                cObj.put("creditLimit", c.creditLimit)
                jsonCustomers.put(cObj)
            }

            val jsonTxs = JSONArray()
            for (t in allTxsList) {
                val tObj = JSONObject()
                tObj.put("id", t.id)
                tObj.put("customerId", t.customerId)
                tObj.put("customerName", customerMap[t.customerId]?.name ?: "عميل #${t.customerId}")
                tObj.put("type", if (t.type == "DEBT") "🔴 سحب دين (+)" else "🟢 سداد دفعة (-)")
                tObj.put("amount", t.amount)
                tObj.put("description", t.description)
                tObj.put("paymentMethod", t.paymentMethod)
                tObj.put("date", t.date)
                tObj.put("timestamp", t.timestamp)
                jsonTxs.put(tObj)
            }

            val jsonProducts = JSONArray()
            for (p in allProductsList) {
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("name", p.name)
                pObj.put("barcode", p.barcode)
                pObj.put("price", p.price)
                pObj.put("category", p.category)
                jsonProducts.put(pObj)
            }

            val rootJson = JSONObject()
            rootJson.put("storeName", storeName)
            rootJson.put("syncTimestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            rootJson.put("customers", jsonCustomers)
            rootJson.put("transactions", jsonTxs)
            rootJson.put("products", jsonProducts)

            val mediaType = "text/plain;charset=utf-8".toMediaType()
            val body = rootJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(scriptUrl)
                .post(body)
                .build()

            val response = executeWithRedirects(request)
            if (response.isSuccessful || response.code in 200..399) {
                response.close()
                Result.success("تم رفع وتحديث البيانات في Google Sheets بنجاح ☁️")
            } else {
                val code = response.code
                response.close()
                Result.failure(Exception("خطأ في الاتصال بالشيت: $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun performCloudDownload(scriptUrlOverride: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scriptUrl = scriptUrlOverride?.ifBlank { null }
                ?: settingDao.getSettingValue("googleScriptUrl")
                ?: return@withContext Result.failure(Exception("رابط Google Apps Script غير محدد في الإعدادات"))

            val cleanUrl = scriptUrl.trim()
            val request = Request.Builder()
                .url(cleanUrl)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/119.0")
                .get()
                .build()

            val response = executeWithRedirects(request)
            val respBody = response.body?.string() ?: ""
            val statusCode = response.code
            response.close()

            if (statusCode !in 200..399) {
                return@withContext Result.failure(Exception("فشل قراءة الشيت (كود HTTP $statusCode): تأكد من صحة الرابط ونشره لـ Anyone"))
            }

            if (respBody.isBlank()) {
                return@withContext Result.failure(Exception("استجابة الشيت فارغة. تأكد من نشر Web App واختيار Anyone في خيارات النشر"))
            }

            // Detect if Google returned an HTML login or error page instead of JSON
            if (respBody.trim().startsWith("<") || respBody.contains("<html", ignoreCase = true)) {
                return@withContext Result.failure(Exception("جوجل يطلب تسجيل دخول! تأكد أثناء النشر (Deploy) من جعل Who has access = Anyone (أي شخص)"))
            }

            val json = try {
                JSONObject(respBody)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("رد غير متوقع من الشيت: ${respBody.take(120)}..."))
            }
            if (json.optString("status") == "error") {
                return@withContext Result.failure(Exception(json.optString("message", "خطأ في سكريبت جوجل")))
            }

            val data = json.optJSONObject("data") ?: return@withContext Result.failure(Exception("لم يتم العثور على حقل data في رد الشيت"))

            val customersArr = data.optJSONArray("customers") ?: JSONArray()
            val txsArr = data.optJSONArray("transactions") ?: JSONArray()
            val productsArr = data.optJSONArray("products") ?: JSONArray()

            val existingCustomers = customerDao.getAllCustomersDirect()
            val nameToExistingCustomer = existingCustomers.associateBy { it.name.trim() }
            val idToExistingCustomer = existingCustomers.associateBy { it.id }

            var nextCustomerId = (existingCustomers.maxOfOrNull { it.id } ?: 0L) + 1L

            val downloadedCustomers = mutableListOf<Customer>()
            val sheetIdToAppCustomerId = mutableMapOf<Long, Long>()

            for (i in 0 until customersArr.length()) {
                val obj = customersArr.getJSONObject(i)
                val rawName = obj.optString("name", "").trim()
                if (rawName.isBlank()) continue

                val rawId = obj.optLong("id", 0L)
                val existing = if (rawId > 0L && idToExistingCustomer.containsKey(rawId)) {
                    idToExistingCustomer[rawId]
                } else {
                    nameToExistingCustomer[rawName]
                }

                val finalId = existing?.id ?: if (rawId > 0L) rawId else nextCustomerId++
                if (rawId > 0L) {
                    sheetIdToAppCustomerId[rawId] = finalId
                }

                downloadedCustomers.add(
                    Customer(
                        id = finalId,
                        name = rawName,
                        phone = obj.optString("phone", "").trim().replace("'", ""),
                        address = obj.optString("address", "").trim(),
                        notes = obj.optString("notes", "").trim(),
                        creditLimit = obj.optDouble("creditLimit", 0.0),
                        createdAt = obj.optString("createdAt", "").ifBlank {
                            existing?.createdAt ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        },
                        isArchived = 0
                    )
                )
            }

            // Save customers first so transactions have valid customer references
            if (downloadedCustomers.isNotEmpty()) {
                customerDao.insertCustomers(downloadedCustomers)
            }

            // Refresh customer map after insert
            val currentCustomers = customerDao.getAllCustomersDirect()
            val currentNameMap = currentCustomers.associateBy { it.name.trim() }

            val existingTxs = transactionDao.getAllTransactionsDirect()
            var nextTxId = (existingTxs.maxOfOrNull { it.id } ?: 0L) + 1L

            val downloadedTxs = mutableListOf<Transaction>()
            for (j in 0 until txsArr.length()) {
                val obj = txsArr.getJSONObject(j)
                val rawTxId = obj.optLong("id", 0L)
                val rawCustId = obj.optLong("customerId", 0L)
                val custName = obj.optString("customerName", "").trim()

                val resolvedCustId = when {
                    rawCustId > 0L && sheetIdToAppCustomerId.containsKey(rawCustId) -> sheetIdToAppCustomerId[rawCustId]!!
                    rawCustId > 0L && currentCustomers.any { it.id == rawCustId } -> rawCustId
                    custName.isNotBlank() && currentNameMap.containsKey(custName) -> currentNameMap[custName]!!.id
                    currentCustomers.isNotEmpty() -> currentCustomers.first().id
                    else -> 1L
                }

                val rawType = obj.optString("type", "DEBT").uppercase()
                val finalType = if (rawType.contains("سداد") || rawType.contains("PAYMENT")) "PAYMENT" else "DEBT"
                val finalTxId = if (rawTxId > 0L) rawTxId else nextTxId++

                downloadedTxs.add(
                    Transaction(
                        id = finalTxId,
                        customerId = resolvedCustId,
                        type = finalType,
                        amount = obj.optDouble("amount", 0.0),
                        description = obj.optString("description", "").trim(),
                        paymentMethod = obj.optString("paymentMethod", "CASH").trim(),
                        date = obj.optString("date", "").ifBlank {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            if (downloadedTxs.isNotEmpty()) {
                transactionDao.insertTransactions(downloadedTxs)
            }

            val existingProducts = productDao.getAllProductsDirect()
            val barcodeToExistingProduct = existingProducts.filter { it.barcode.isNotBlank() }.associateBy { it.barcode.trim() }
            val nameToExistingProduct = existingProducts.associateBy { it.name.trim() }
            var nextProdId = (existingProducts.maxOfOrNull { it.id } ?: 0L) + 1L

            val downloadedProducts = mutableListOf<Product>()
            for (k in 0 until productsArr.length()) {
                val obj = productsArr.getJSONObject(k)
                val rawProdName = obj.optString("name", "").trim()
                if (rawProdName.isBlank()) continue

                val rawBarcode = obj.optString("barcode", "").trim().replace("'", "")
                val rawProdId = obj.optLong("id", 0L)

                val existingProd = when {
                    rawProdId > 0L && existingProducts.any { it.id == rawProdId } -> existingProducts.first { it.id == rawProdId }
                    rawBarcode.isNotBlank() && barcodeToExistingProduct.containsKey(rawBarcode) -> barcodeToExistingProduct[rawBarcode]
                    nameToExistingProduct.containsKey(rawProdName) -> nameToExistingProduct[rawProdName]
                    else -> null
                }

                val finalProdId = existingProd?.id ?: if (rawProdId > 0L) rawProdId else nextProdId++

                downloadedProducts.add(
                    Product(
                        id = finalProdId,
                        name = rawProdName,
                        barcode = rawBarcode,
                        price = obj.optDouble("price", 0.0),
                        category = obj.optString("category", "عام").trim().ifBlank { "عام" }
                    )
                )
            }

            if (downloadedProducts.isNotEmpty()) {
                productDao.insertProducts(downloadedProducts)
            }

            Result.success("تم بنجاح سحب وتحديث: ${downloadedCustomers.size} عميل، ${downloadedTxs.size} حركة، ${downloadedProducts.size} منتج 📥")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
