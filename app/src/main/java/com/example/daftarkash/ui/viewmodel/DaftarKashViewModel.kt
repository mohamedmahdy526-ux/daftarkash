package com.example.daftarkash.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.PosCartItem
import com.example.daftarkash.data.model.Product
import com.example.daftarkash.data.model.Setting
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.data.repository.CustomerWithBalance
import com.example.daftarkash.data.repository.DaftarKashRepository
import com.example.daftarkash.data.repository.LedgerMetrics
import com.example.daftarkash.util.SoundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DaftarKashViewModel(private val repository: DaftarKashRepository) : ViewModel() {

    // Filter & Search states
    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchQuery: StateFlow<String> = _customerSearchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow("all") // "all", "has_debt", "settled", "top_debt"
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _productSearchQuery = MutableStateFlow("")
    val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

    // Selected customer for Statement detail
    private val _selectedCustomerId = MutableStateFlow<Long?>(null)
    val selectedCustomerId: StateFlow<Long?> = _selectedCustomerId.asStateFlow()

    // Cloud Sync state
    private val _cloudSyncStatus = MutableStateFlow("synced") // "synced", "syncing", "offline", "error"
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private var isSyncInProgress = false

    init {
        // تحديث الاسم الرسمي للمحل تلقائياً إن وجد الاسم القديم
        viewModelScope.launch(Dispatchers.IO) {
            val currentName = repository.getSetting("storeName")
            if (currentName.isNullOrBlank() || currentName == "بقالة البركة" || currentName == "دفتر كاش" || currentName == "My Application") {
                repository.saveSetting("storeName", "ماركت أولاد ماهر")
            }
        }

        // حلقة المزامنة التلقائية الدورية في الخلفية (Background Polling Loop)
        viewModelScope.launch(Dispatchers.IO) {
            // انتظار 6 ثوانٍ عند بدء تشغيل التطبيق لضمان سرعة واستجابة الواجهة فوراً
            kotlinx.coroutines.delay(6000)
            while (true) {
                try {
                    val url = repository.getSetting("googleScriptUrl") ?: ""
                    if (url.isNotBlank()) {
                        silentBackgroundSync()
                    }
                } catch (e: Exception) {
                    // تفادي أي توقف مفاجئ عند انقطاع الإنترنت المؤقت
                }
                kotlinx.coroutines.delay(30000) // التكرار التلقائي كل 30 ثانية
            }
        }
    }

    private suspend fun silentBackgroundSync() {
        if (isSyncInProgress) return
        val url = googleScriptUrl.value
        if (url.isBlank()) return

        isSyncInProgress = true
        try {
            val result = repository.performCloudDownload()
            if (result.isSuccess) {
                _cloudSyncStatus.value = "synced"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _lastSyncTime.value = timeStr
            }
        } catch (e: Exception) {
            // تجاهل انقطاع الشبكة العابر
        } finally {
            isSyncInProgress = false
        }
    }

    // Toast / Banner Message event
    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    // Confetti trigger event for settled debts
    private val _confettiEvent = MutableSharedFlow<Unit>()
    val confettiEvent = _confettiEvent.asSharedFlow()

    // POS Cart
    private val _posCart = MutableStateFlow<List<PosCartItem>>(emptyList())
    val posCart: StateFlow<List<PosCartItem>> = _posCart.asStateFlow()

    private val _posCustomerId = MutableStateFlow<Long?>(null)
    val posCustomerId: StateFlow<Long?> = _posCustomerId.asStateFlow()

    // App Preferences / Settings
    val allSettings: StateFlow<Map<String, String>> = repository.allSettings
        .map { list -> list.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val isDarkMode: StateFlow<Boolean> = allSettings
        .map { map -> map["isDarkMode"]?.toBooleanStrictOrNull() ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSoundEffectsEnabled: StateFlow<Boolean> = allSettings
        .map { map -> 
            val enabled = map["isSoundEffectsEnabled"]?.toBooleanStrictOrNull() ?: true 
            SoundHelper.setSoundEnabled(enabled)
            enabled
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val brandTheme: StateFlow<String> = allSettings
        .map { map -> map["brandTheme"] ?: "emerald" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "emerald")

    val fontScale: StateFlow<Float> = allSettings
        .map { map ->
            when (map["fontScale"]) {
                "sm" -> 0.85f
                "lg" -> 1.15f
                "xl" -> 1.30f
                else -> 1.0f
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val fontScaleRaw: StateFlow<String> = allSettings
        .map { map -> map["fontScale"] ?: "md" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "md")

    val storeName: StateFlow<String> = allSettings
        .map { map -> map["storeName"] ?: "ماركت أولاد ماهر" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "ماركت أولاد ماهر")

    val storePhone: StateFlow<String> = allSettings
        .map { map -> map["storePhone"] ?: "01012345678" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "01012345678")

    val currency: StateFlow<String> = allSettings
        .map { map -> map["currency"] ?: "ج.م" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "ج.م")

    val googleScriptUrl: StateFlow<String> = allSettings
        .map { map -> map["googleScriptUrl"] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun triggerCloudSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = googleScriptUrl.value
            if (url.isBlank()) return@launch

            _cloudSyncStatus.value = "syncing"
            val result = repository.performCloudSync()
            if (result.isSuccess) {
                _cloudSyncStatus.value = "synced"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _lastSyncTime.value = timeStr
            } else {
                _cloudSyncStatus.value = "offline"
            }
        }
    }

    // Raw streams
    val allCustomers = repository.allCustomers
    val allTransactions = repository.allTransactions
    val allProducts = repository.allProducts

    // Combined Customer items with balance calculation
    val customersWithBalances: StateFlow<List<CustomerWithBalance>> = combine(
        repository.allCustomers,
        repository.allTransactions,
        _customerSearchQuery,
        _currentFilter
    ) { customers, transactions, query, filter ->
        val balanceMap = mutableMapOf<Long, Double>()
        val lastTxMap = mutableMapOf<Long, Transaction>()

        for (c in customers) {
            balanceMap[c.id] = 0.0
        }

        for (t in transactions) {
            val cur = balanceMap[t.customerId] ?: 0.0
            if (t.type == "DEBT") {
                balanceMap[t.customerId] = cur + t.amount
            } else if (t.type == "PAYMENT") {
                balanceMap[t.customerId] = cur - t.amount
            }

            val existing = lastTxMap[t.customerId]
            if (existing == null || t.timestamp > existing.timestamp) {
                lastTxMap[t.customerId] = t
            }
        }

        var list = customers.map { c ->
            CustomerWithBalance(
                customer = c,
                balance = balanceMap[c.id] ?: 0.0,
                lastTransaction = lastTxMap[c.id]
            )
        }

        if (query.isNotBlank()) {
            val qNorm = normalizeArabicText(query)
            val qRaw = query.trim().lowercase()
            list = list.filter {
                normalizeArabicText(it.customer.name).contains(qNorm) ||
                it.customer.name.lowercase().contains(qRaw) ||
                it.customer.phone.contains(qRaw)
            }
        }

        when (filter) {
            "has_debt" -> list.filter { it.balance > 0.0 }
            "settled" -> list.filter { it.balance <= 0.0 }
            "top_debt" -> list.sortedByDescending { it.balance }
            else -> list
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Ledger Metrics calculation
    val ledgerMetrics: StateFlow<LedgerMetrics> = combine(
        repository.allCustomers,
        repository.allTransactions
    ) { customers, transactions ->
        val balanceMap = mutableMapOf<Long, Double>()
        for (c in customers) {
            balanceMap[c.id] = 0.0
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var todayCollections = 0.0
        for (t in transactions) {
            val cur = balanceMap[t.customerId] ?: 0.0
            if (t.type == "DEBT") {
                balanceMap[t.customerId] = cur + t.amount
            } else if (t.type == "PAYMENT") {
                balanceMap[t.customerId] = cur - t.amount
            }

            if (t.type == "PAYMENT" && t.timestamp >= startOfToday) {
                todayCollections += t.amount
            }
        }

        var totalDebt = 0.0
        var debtorsCount = 0
        var settledCount = 0

        for ((_, balance) in balanceMap) {
            if (balance > 0.0) {
                totalDebt += balance
                debtorsCount++
            } else {
                settledCount++
            }
        }

        LedgerMetrics(
            totalMarketDebt = totalDebt,
            todayCollections = todayCollections,
            debtorsCount = debtorsCount,
            totalCustomersCount = customers.size,
            settledCount = settledCount
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, LedgerMetrics())

    // Selected customer data
    val selectedCustomerDetail: StateFlow<CustomerWithBalance?> = combine(
        _selectedCustomerId,
        repository.allCustomers,
        repository.allTransactions
    ) { id, customers, transactions ->
        if (id == null) return@combine null
        val customer = customers.find { it.id == id } ?: return@combine null

        val customerTxs = transactions.filter { it.customerId == id }
        var balance = 0.0
        for (t in customerTxs) {
            if (t.type == "DEBT") balance += t.amount
            if (t.type == "PAYMENT") balance -= t.amount
        }

        val lastTx = customerTxs.maxByOrNull { it.timestamp }
        CustomerWithBalance(customer, balance, lastTx)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, null)

    val selectedCustomerTransactions: StateFlow<List<Transaction>> = combine(
        _selectedCustomerId,
        repository.allTransactions
    ) { id, transactions ->
        if (id == null) emptyList()
        else transactions.filter { it.customerId == id }.sortedByDescending { it.timestamp }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Filtered Products
    val filteredProducts: StateFlow<List<Product>> = combine(
        repository.allProducts,
        _productSearchQuery
    ) { products, query ->
        if (query.isBlank()) products
        else {
            val q = query.trim().lowercase()
            products.filter {
                it.name.lowercase().contains(q) ||
                it.barcode.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Actions
    fun setCustomerSearchQuery(query: String) {
        _customerSearchQuery.value = query
    }

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }

    fun setProductSearchQuery(query: String) {
        _productSearchQuery.value = query
    }

    fun selectCustomer(id: Long?) {
        _selectedCustomerId.value = id
    }

    fun setPosCustomer(id: Long?) {
        _posCustomerId.value = id
    }

    fun toggleDarkMode() {
        val next = !isDarkMode.value
        viewModelScope.launch {
            repository.saveSetting("isDarkMode", next.toString())
        }
    }

    fun toggleSoundEffects() {
        val next = !isSoundEffectsEnabled.value
        viewModelScope.launch {
            repository.saveSetting("isSoundEffectsEnabled", next.toString())
            SoundHelper.setSoundEnabled(next)
            if (next) {
                SoundHelper.playCashChime()
                _uiEvent.emit("تم تفعيل المؤثرات الصوتية 🔊")
            } else {
                _uiEvent.emit("تم كتم المؤثرات الصوتية 🔇")
            }
        }
    }

    fun setBrandTheme(theme: String) {
        viewModelScope.launch {
            repository.saveSetting("brandTheme", theme)
            _uiEvent.emit("تم تغيير هوية الألوان ✨")
        }
    }

    fun setFontScale(scaleKey: String) {
        viewModelScope.launch {
            repository.saveSetting("fontScale", scaleKey)
            val name = when (scaleKey) {
                "sm" -> "صغير"
                "lg" -> "كبير"
                "xl" -> "ضخم"
                else -> "متوسط"
            }
            _uiEvent.emit("تم ضبط حجم الخط: $name")
        }
    }

    fun addCustomer(name: String, phone: String, initialDebt: Double, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiEvent.emit("اسم العميل مطلوب")
                return@launch
            }
            val id = repository.addCustomer(name.trim(), phone.trim(), initialDebt)
            SoundHelper.playSuccess()
            _uiEvent.emit("تمت إضافة العميل ($name) بنجاح")
            triggerCloudSync()
            onSuccess(id)
        }
    }

    fun addTransaction(
        customerId: Long,
        type: String,
        amount: Double,
        description: String,
        paymentMethod: String = "CASH",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _uiEvent.emit("يرجى إدخال مبلغ صحيح أكبر من الصفر")
                return@launch
            }
            repository.addTransaction(customerId, type, amount, description, paymentMethod)
            if (type == "DEBT") {
                SoundHelper.playDebtRecorded()
                _uiEvent.emit("تم تسجيل الدين بنجاح 🔴")
            } else {
                // Check if customer balance is now settled
                val current = selectedCustomerDetail.value
                if (current != null && (current.balance - amount) <= 0.0) {
                    SoundHelper.playCelebrationFanfare()
                    _confettiEvent.emit(Unit)
                } else {
                    SoundHelper.playCashChime()
                }
                _uiEvent.emit("تم تسجيل السداد بنجاح 🟢")
            }
            triggerCloudSync()
            onSuccess()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        deleteTransaction(transaction.id)
    }

    fun deleteTransaction(txId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
            SoundHelper.playDeleteSound()
            _uiEvent.emit("تم حذف الحركة")
            triggerCloudSync()
        }
    }

    fun deleteCustomer(customer: Customer, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            SoundHelper.playDeleteSound()
            _uiEvent.emit("تم حذف العميل وحركاته")
            triggerCloudSync()
            onSuccess()
        }
    }

    fun updateCustomer(customer: Customer, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (customer.name.isBlank()) {
                _uiEvent.emit("اسم العميل مطلوب")
                return@launch
            }
            repository.updateCustomer(customer)
            SoundHelper.playSuccess()
            _uiEvent.emit("تم تحديث بيانات العميل (${customer.name}) بنجاح ✅")
            triggerCloudSync()
            onSuccess()
        }
    }

    fun addProduct(name: String, barcode: String, price: Double, category: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiEvent.emit("اسم الصنف مطلوب")
                return@launch
            }
            repository.addProduct(name.trim(), barcode.trim(), price, category.trim())
            SoundHelper.playSuccess()
            _uiEvent.emit("تمت إضافة الصنف بنجاح 🛒")
            triggerCloudSync()
            onSuccess()
        }
    }

    fun updateProductPrice(productId: Long, newPrice: Double) {
        viewModelScope.launch {
            repository.updateProductPrice(productId, newPrice)
            SoundHelper.playSuccess()
            _uiEvent.emit("تم تحديث السعر بنجاح")
            triggerCloudSync()
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            SoundHelper.playDeleteSound()
            _uiEvent.emit("تم حذف الصنف")
            triggerCloudSync()
        }
    }

    // POS Cart Operations
    fun addToPosCart(barcode: String, name: String, price: Double) {
        val current = _posCart.value.toMutableList()
        val index = current.indexOfFirst { (it.barcode.isNotBlank() && it.barcode == barcode) || it.name == name }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(qty = item.qty + 1)
        } else {
            current.add(PosCartItem(barcode, name, price, 1))
        }
        _posCart.value = current
        SoundHelper.playScannerBeep()
    }

    fun handleScannedBarcode(barcode: String, onNewProductPrompt: (String) -> Unit) {
        viewModelScope.launch {
            val product = repository.findProductByBarcode(barcode)
            if (product != null) {
                addToPosCart(product.barcode, product.name, product.price)
                _uiEvent.emit("تمت إضافة: ${product.name} (${product.price} ج)")
            } else {
                onNewProductPrompt(barcode)
            }
        }
    }

    fun updateCartItemQty(index: Int, delta: Int) {
        val current = _posCart.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            val newQty = item.qty + delta
            if (newQty <= 0) {
                current.removeAt(index)
                SoundHelper.playDeleteSound()
            } else {
                current[index] = item.copy(qty = newQty)
                SoundHelper.playScannerBeep()
            }
            _posCart.value = current
        }
    }

    fun removeCartItem(index: Int) {
        val current = _posCart.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _posCart.value = current
            SoundHelper.playDeleteSound()
        }
    }

    fun clearPosCart() {
        _posCart.value = emptyList()
        SoundHelper.playDeleteSound()
    }

    fun checkoutPosAsDebt(onSuccess: () -> Unit = {}) {
        val customerId = _posCustomerId.value
        val items = _posCart.value
        if (customerId == null) {
            viewModelScope.launch { _uiEvent.emit("يرجى اختيار العميل أولاً لتسجيل الدين") }
            return
        }
        if (items.isEmpty()) {
            viewModelScope.launch { _uiEvent.emit("السلة فارغة") }
            return
        }

        val total = items.sumOf { it.price * it.qty }
        val desc = items.joinToString(" + ") { "${it.name} (${it.qty}x)" }

        viewModelScope.launch {
            repository.addTransaction(customerId, "DEBT", total, desc, "CASH")
            clearPosCart()
            SoundHelper.playDebtRecorded()
            _uiEvent.emit("تم تسجيل فاتورة (${formatMoney(total)} ج) على حساب العميل 🔴")
            triggerCloudSync()
            onSuccess()
        }
    }

    fun checkoutPosCash(onSuccess: () -> Unit = {}) {
        checkoutPosAsCash(onSuccess)
    }

    fun checkoutPosAsCash(onSuccess: () -> Unit = {}) {
        val items = _posCart.value
        if (items.isEmpty()) {
            viewModelScope.launch { _uiEvent.emit("السلة فارغة") }
            return
        }
        val total = items.sumOf { it.price * it.qty }
        clearPosCart()
        viewModelScope.launch {
            SoundHelper.playCashChime()
            _uiEvent.emit("تم تسجيل بيع نقدي فوري (${formatMoney(total)} ج) 💵")
            onSuccess()
        }
    }

    fun saveStoreSettings(name: String, phone: String, cur: String, scriptUrl: String) {
        viewModelScope.launch {
            repository.saveSetting("storeName", name)
            repository.saveSetting("storePhone", phone)
            repository.saveSetting("currency", cur)
            repository.saveSetting("googleScriptUrl", scriptUrl)
            _uiEvent.emit("تم حفظ الإعدادات بنجاح ✅")
            triggerCloudSync()
        }
    }



    fun manualCloudUpload() {
        viewModelScope.launch {
            _cloudSyncStatus.value = "syncing"
            val result = repository.performCloudSync()
            if (result.isSuccess) {
                _cloudSyncStatus.value = "synced"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _lastSyncTime.value = timeStr
                _uiEvent.emit(result.getOrDefault("تم تصدير وتحديث الشيت بنجاح! ☁️"))
            } else {
                _cloudSyncStatus.value = "offline"
                _uiEvent.emit("تعذر التصدير للشيت: ${result.exceptionOrNull()?.localizedMessage ?: "تحقق من الرابط"}")
            }
        }
    }

    fun manualCloudDownload() {
        viewModelScope.launch {
            _cloudSyncStatus.value = "syncing"
            val result = repository.performCloudDownload()
            if (result.isSuccess) {
                _cloudSyncStatus.value = "synced"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _lastSyncTime.value = timeStr
                _uiEvent.emit(result.getOrDefault("تم استيراد البيانات من الشيت وتحديث التطبيق! 📥"))
            } else {
                _cloudSyncStatus.value = "offline"
                _uiEvent.emit("تعذر الاستيراد من الشيت: ${result.exceptionOrNull()?.localizedMessage ?: "تحقق من الرابط"}")
            }
        }
    }

    fun manualCloudSync() {
        manualFullTwoWaySync()
    }

    fun manualFullTwoWaySync() {
        viewModelScope.launch {
            _cloudSyncStatus.value = "syncing"
            // Step 1: Push local state first so edits made in app are safely sent to sheet
            val pushResult = repository.performCloudSync()
            // Step 2: Pull latest data from Google Sheets
            val pullResult = repository.performCloudDownload()

            if (pushResult.isSuccess || pullResult.isSuccess) {
                _cloudSyncStatus.value = "synced"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _lastSyncTime.value = timeStr
                _uiEvent.emit("تمت المزامنة الثنائية الكاملة (تحديث وسحب الشيت) بنجاح 🔄")
            } else {
                _cloudSyncStatus.value = "offline"
                _uiEvent.emit("فشلت المزامنة: ${pushResult.exceptionOrNull()?.localizedMessage ?: "تحقق من الرابط"}")
            }
        }
    }

    companion object {
        private val arabicLocale = Locale.forLanguageTag("ar")
        private val moneyFormat = ThreadLocal.withInitial { DecimalFormat("#,##0.##") }
        private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", arabicLocale) }

        fun formatMoney(amount: Double): String {
            return moneyFormat.get()?.format(amount) ?: String.format(Locale.US, "%.2f", amount)
        }

        fun formatDateRelative(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val mins = diff / 60000
            if (mins < 1) return "الآن"
            if (mins < 60) return "منذ $mins دقيقة"
            val hours = mins / 60
            if (hours < 24) return "منذ $hours ساعة"
            val days = hours / 24
            if (days == 1L) return "أمس"
            if (days < 7) return "منذ $days أيام"
            return dateFormat.get()?.format(Date(timestamp)) ?: "2026"
        }

        fun normalizeArabicText(text: String): String {
            return text.trim().lowercase()
                .replace("[إأآا]".toRegex(), "ا")
                .replace("ة", "ه")
                .replace("ى", "ي")
                .replace("[ًٌٍَُِّْـ]".toRegex(), "")
        }
    }
}

class DaftarKashViewModelFactory(private val repository: DaftarKashRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DaftarKashViewModel::class.java)) {
            return DaftarKashViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
