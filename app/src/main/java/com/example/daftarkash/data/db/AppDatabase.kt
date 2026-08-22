package com.example.daftarkash.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Product
import com.example.daftarkash.data.model.Setting
import com.example.daftarkash.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [Customer::class, Transaction::class, Product::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun productDao(): ProductDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "DaftarKashDB"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val customerDao = database.customerDao()
            val transactionDao = database.transactionDao()
            val productDao = database.productDao()
            val settingDao = database.settingDao()

            if (customerDao.getCustomerCount() == 0) {
                settingDao.setSettings(
                    listOf(
                        Setting("storeName", "ماركت أولاد ماهر"),
                        Setting("storePhone", "01012345678"),
                        Setting("currency", "ج.م"),
                        Setting("brandTheme", "emerald"),
                        Setting("isDarkMode", "false"),
                        Setting("isSoundEffectsEnabled", "true"),
                        Setting("fontScale", "md"),
                        Setting("googleScriptUrl", "https://script.google.com/macros/s/AKfycbyBTXls7mivEUxMuylSjazsqkAvg24Jo9UZqDhQJR3JT-B-BDwYNNTN4QoEkve4nh_Y/exec")
                    )
                )

                productDao.insertProducts(
                    listOf(
                        Product(name = "سكر الأسرة 1 كجم", barcode = "6221001001", price = 35.0, category = "بقالة أساسية"),
                        Product(name = "شاي العروسة 40 جم", barcode = "6221001002", price = 12.0, category = "شاي ومشروبات"),
                        Product(name = "شاي العروسة 100 جم", barcode = "6221001003", price = 25.0, category = "شاي ومشروبات"),
                        Product(name = "زيت كريستال عباد 800مل", barcode = "6221001004", price = 75.0, category = "زيوت وسمن"),
                        Product(name = "مكرونة حواء 400 جم", barcode = "6221001005", price = 15.0, category = "مكرونة وأرز"),
                        Product(name = "جبنة دومتي بلس 500 جم", barcode = "6221001006", price = 38.0, category = "ألبان وجبن"),
                        Product(name = "تونة صن شاين مفتتة", barcode = "6221001007", price = 45.0, category = "معلبات"),
                        Product(name = "سجاير كليوباترا بوكس", barcode = "6221001008", price = 34.5, category = "سجاير ودخان"),
                        Product(name = "سجاير LM أزرق", barcode = "6221001009", price = 68.0, category = "سجاير ودخان"),
                        Product(name = "إندومي خضار سوبر", barcode = "6221001010", price = 10.0, category = "سناكس"),
                        Product(name = "بيبسي كانز 330 مل", barcode = "6221001011", price = 15.0, category = "مشروبات غازية")
                    )
                )

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))
                val now = System.currentTimeMillis()

                val c1Id = customerDao.insertCustomer(
                    Customer(
                        name = "أحمد محمود عبد الرحمن",
                        phone = "01012345678",
                        address = "شارع الجمهورية - عمارة 5",
                        notes = "جار المحل",
                        creditLimit = 2000.0,
                        createdAt = "2026-08-10"
                    )
                )

                val c2Id = customerDao.insertCustomer(
                    Customer(
                        name = "مصطفى علي كامل",
                        phone = "01123456789",
                        address = "بجوار المسجد الكبير",
                        notes = "صاحب ورشة النجارة",
                        creditLimit = 1500.0,
                        createdAt = "2026-08-12"
                    )
                )

                val c3Id = customerDao.insertCustomer(
                    Customer(
                        name = "إبراهيم حسن النجار",
                        phone = "01234567890",
                        address = "شارع المدارس",
                        notes = "",
                        creditLimit = 1000.0,
                        createdAt = "2026-08-13"
                    )
                )

                transactionDao.insertTransactions(
                    listOf(
                        Transaction(
                            customerId = c1Id,
                            type = "DEBT",
                            amount = 350.0,
                            description = "طلبات أسبوع (سكر + زيت + شاي)",
                            date = "2026-08-12 10:30",
                            timestamp = now - 5 * 86400000L
                        ),
                        Transaction(
                            customerId = c1Id,
                            type = "PAYMENT",
                            amount = 200.0,
                            description = "دفعة كاش",
                            date = "2026-08-14 18:45",
                            timestamp = now - 3 * 86400000L
                        ),
                        Transaction(
                            customerId = c1Id,
                            type = "DEBT",
                            amount = 500.0,
                            description = "سجاير كليوباترا + ألبان وجبن",
                            date = "2026-08-17 09:15",
                            timestamp = now
                        ),
                        Transaction(
                            customerId = c2Id,
                            type = "DEBT",
                            amount = 1200.0,
                            description = "بضاعة شهرية للمنزل",
                            date = "2026-08-16 20:00",
                            timestamp = now - 86400000L
                        ),
                        Transaction(
                            customerId = c3Id,
                            type = "DEBT",
                            amount = 400.0,
                            description = "بقالة متنوعة",
                            date = "2026-08-10 11:00",
                            timestamp = now - 7 * 86400000L
                        ),
                        Transaction(
                            customerId = c3Id,
                            type = "PAYMENT",
                            amount = 400.0,
                            description = "سداد كامل الحساب نقدياً",
                            date = "2026-08-15 17:30",
                            timestamp = now - 2 * 86400000L
                        )
                    )
                )
            }
        }
    }
}
