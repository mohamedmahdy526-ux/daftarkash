package com.example.daftarkash.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val APPS_SCRIPT_TEMPLATE = """// ==========================================
// دفتر كاش - سكريبت المزامنة الثنائية الذكية (Smart Two-Way Sync)
// ==========================================

// 1. استقبال البيانات من التطبيق والدمج الذكي مع التعديلات اليدوية في الشيت
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // (أ) ورقة العملاء والأرصدة - دمج ذكي
    var cSheet = ss.getSheetByName("العملاء والأرصدة") || ss.insertSheet("العملاء والأرصدة");
    cSheet.setRightToLeft(true);
    
    var customerMap = {};
    if (cSheet.getLastRow() > 1) {
      var numCols = Math.max(cSheet.getLastColumn(), 8);
      var cExisting = cSheet.getRange(2, 1, cSheet.getLastRow() - 1, numCols).getValues();
      for (var i = 0; i < cExisting.length; i++) {
        var r = cExisting[i];
        var cName = (r[1] !== undefined && r[1] !== null) ? r[1].toString().trim() : "";
        if (cName !== '' && cName !== "اسم العميل") {
          var cId = Number(r[0]) || 0;
          customerMap[cName.toLowerCase()] = {
            id: cId,
            name: cName,
            phone: r[2] ? r[2].toString().replace("'", "").trim() : "",
            balance: Number(r[3]) || 0,
            createdAt: r[4] ? r[4].toString().trim() : "",
            address: r[5] ? r[5].toString().trim() : "",
            notes: r[6] ? r[6].toString().trim() : "",
            creditLimit: Number(r[7]) || 0
          };
          if (cId > 0) {
            customerMap["id_" + cId] = customerMap[cName.toLowerCase()];
          }
        }
      }
    }
    
    if (data.customers && data.customers.length > 0) {
      for (var j = 0; j < data.customers.length; j++) {
        var c = data.customers[j];
        var key = c.name ? c.name.toString().trim().toLowerCase() : "";
        if (!key) continue;
        var idKey = "id_" + c.id;
        var existing = customerMap[idKey] || customerMap[key];
        
        var updatedCust = {
          id: c.id || (existing ? existing.id : 0),
          name: c.name || (existing ? existing.name : ''),
          phone: c.phone || (existing ? existing.phone : ''),
          balance: (c.balance !== undefined && c.balance !== null) ? Number(c.balance) : (existing ? existing.balance : 0),
          createdAt: c.createdAt || (existing ? existing.createdAt : ''),
          address: c.address || (existing ? existing.address : ''),
          notes: c.notes || (existing ? existing.notes : ''),
          creditLimit: (c.creditLimit !== undefined && c.creditLimit !== null) ? Number(c.creditLimit) : (existing ? existing.creditLimit : 0)
        };
        customerMap[key] = updatedCust;
        if (c.id > 0) {
          customerMap["id_" + c.id] = updatedCust;
        }
      }
    }
    
    var cKeys = Object.keys(customerMap).filter(function(k) { return k.indexOf("id_") !== 0; });
    var cRows = cKeys.map(function(k) {
      var itm = customerMap[k];
      return [itm.id || 0, itm.name || '', "'" + (itm.phone || ''), Number(itm.balance) || 0, itm.createdAt || '', itm.address || '', itm.notes || '', Number(itm.creditLimit) || 0];
    });
    
    cSheet.clear();
    cSheet.appendRow(["رقم العميل", "اسم العميل", "رقم الهاتف", "الرصيد المتبقي (ج.م)", "تاريخ التسجيل", "العنوان", "ملاحظات", "الحد الائتماني"]);
    cSheet.getRange(1, 1, 1, 8).setBackground("#1E293B").setFontColor("#FFFFFF").setFontWeight("bold");
    if (cRows.length > 0) {
      cSheet.getRange(2, 1, cRows.length, 8).setValues(cRows);
    }

    // (ب) ورقة سجل المعاملات - دمج ذكي
    var tSheet = ss.getSheetByName("سجل المعاملات") || ss.insertSheet("سجل المعاملات");
    tSheet.setRightToLeft(true);
    
    var txMap = {};
    if (tSheet.getLastRow() > 1) {
      var numTCols = Math.max(tSheet.getLastColumn(), 9);
      var tExisting = tSheet.getRange(2, 1, tSheet.getLastRow() - 1, numTCols).getValues();
      for (var k = 0; k < tExisting.length; k++) {
        var tr = tExisting[k];
        var amt = Number(tr[4]);
        if (!isNaN(amt) && amt > 0) {
          var txId = Number(tr[0]) || 0;
          var txKey = (txId > 0) ? ("id_" + txId) : ("tx_" + tr[2] + "_" + amt + "_" + tr[7]);
          txMap[txKey] = {
            id: txId,
            customerId: Number(tr[1]) || 0,
            customerName: tr[2] ? tr[2].toString().trim() : "",
            type: tr[3] ? tr[3].toString().trim() : "DEBT",
            amount: amt,
            description: tr[5] ? tr[5].toString().trim() : "",
            paymentMethod: tr[6] ? tr[6].toString().trim() : "CASH",
            date: tr[7] ? tr[7].toString().trim() : "",
            timestamp: Number(tr[8]) || new Date().getTime()
          };
        }
      }
    }
    
    if (data.transactions && data.transactions.length > 0) {
      for (var l = 0; l < data.transactions.length; l++) {
        var t = data.transactions[l];
        var tKey = (t.id > 0) ? ("id_" + t.id) : ("tx_" + t.customerName + "_" + t.amount + "_" + t.date);
        txMap[tKey] = {
          id: t.id || 0,
          customerId: t.customerId || 0,
          customerName: t.customerName || '',
          type: t.type || 'DEBT',
          amount: Number(t.amount) || 0,
          description: t.description || '',
          paymentMethod: t.paymentMethod || 'CASH',
          date: t.date || '',
          timestamp: Number(t.timestamp) || new Date().getTime()
        };
      }
    }
    
    var tRows = Object.keys(txMap).map(function(k) {
      var itm = txMap[k];
      return [itm.id, itm.customerId, itm.customerName, itm.type, itm.amount, itm.description, itm.paymentMethod, itm.date, itm.timestamp];
    });
    
    tSheet.clear();
    tSheet.appendRow(["رقم الحركة", "رقم العميل", "اسم العميل", "نوع الحركة", "المبلغ (ج.م)", "تفاصيل البضاعة", "طريقة الدفع", "التاريخ والوقت", "Timestamp"]);
    tSheet.getRange(1, 1, 1, 9).setBackground("#0F172A").setFontColor("#FFFFFF").setFontWeight("bold");
    if (tRows.length > 0) {
      tSheet.getRange(2, 1, tRows.length, 9).setValues(tRows);
    }

    // (ج) ورقة قائمة المنتجات والأسعار - دمج ذكي
    var pSheet = ss.getSheetByName("المنتجات والأسعار") || ss.insertSheet("المنتجات والأسعار");
    pSheet.setRightToLeft(true);
    
    var prodMap = {};
    if (pSheet.getLastRow() > 1) {
      var numPCols = Math.max(pSheet.getLastColumn(), 5);
      var pExisting = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, numPCols).getValues();
      for (var m = 0; m < pExisting.length; m++) {
        var pr = pExisting[m];
        var pName = (pr[1] !== undefined && pr[1] !== null) ? pr[1].toString().trim() : "";
        if (pName !== '' && pName !== "اسم المنتج") {
          var pBarcode = pr[2] ? pr[2].toString().replace("'", "").trim() : "";
          var pKey = pBarcode ? ("bc_" + pBarcode) : ("name_" + pName.toLowerCase());
          prodMap[pKey] = {
            id: Number(pr[0]) || 0,
            name: pName,
            barcode: pBarcode,
            price: Number(pr[3]) || 0,
            category: pr[4] ? pr[4].toString().trim() : "عام"
          };
        }
      }
    }
    
    if (data.products && data.products.length > 0) {
      for (var n = 0; n < data.products.length; n++) {
        var p = data.products[n];
        var bc = (p.barcode || '').toString().trim();
        var pk = bc ? ("bc_" + bc) : ("name_" + p.name.toString().trim().toLowerCase());
        var existP = prodMap[pk];
        
        prodMap[pk] = {
          id: p.id || (existP ? existP.id : 0),
          name: p.name || (existP ? existP.name : ''),
          barcode: bc || (existP ? existP.barcode : ''),
          price: Number(p.price) || (existP ? existP.price : 0),
          category: p.category || (existP ? existP.category : 'عام')
        };
      }
    }
    
    var pRows = Object.keys(prodMap).map(function(k) {
      var itm = prodMap[k];
      return [itm.id, itm.name, "'" + (itm.barcode || ''), itm.price, itm.category || 'عام'];
    });
    
    pSheet.clear();
    pSheet.appendRow(["رقم الصنف", "اسم المنتج", "الباركود", "سعر البيع (ج.م)", "القسم / التصنيف"]);
    pSheet.getRange(1, 1, 1, 5).setBackground("#1E3A8A").setFontColor("#FFFFFF").setFontWeight("bold");
    if (pRows.length > 0) {
      pSheet.getRange(2, 1, pRows.length, 5).setValues(pRows);
    }
    
    return ContentService.createTextOutput(JSON.stringify({status: "success", message: "تم الدمج والتحديث بنجاح"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// 2. إرسال بيانات الشيت إلى تطبيق الهاتف (سحب فوري)
function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var result = {
      customers: [],
      transactions: [],
      products: []
    };

    // قراءة العملاء
    var cSheet = ss.getSheetByName("العملاء والأرصدة") || ss.getSheets()[0];
    if (cSheet && cSheet.getLastRow() > 1) {
      var numCols = Math.max(cSheet.getLastColumn(), 8);
      var cData = cSheet.getRange(2, 1, cSheet.getLastRow() - 1, numCols).getValues();
      for (var i = 0; i < cData.length; i++) {
        var row = cData[i];
        var cName = (row[1] !== undefined && row[1] !== null) ? row[1].toString().trim() : "";
        if (cName !== '' && cName !== "اسم العميل") {
          result.customers.push({
            id: Number(row[0]) || 0,
            name: cName,
            phone: row[2] ? row[2].toString().replace("'", "").trim() : "",
            balance: Number(row[3]) || 0,
            createdAt: row[4] ? row[4].toString().trim() : "",
            address: row[5] ? row[5].toString().trim() : "",
            notes: row[6] ? row[6].toString().trim() : "",
            creditLimit: Number(row[7]) || 0
          });
        }
      }
    }

    // قراءة المعاملات
    var tSheet = ss.getSheetByName("سجل المعاملات");
    if (tSheet && tSheet.getLastRow() > 1) {
      var numTCols = Math.max(tSheet.getLastColumn(), 9);
      var tData = tSheet.getRange(2, 1, tSheet.getLastRow() - 1, numTCols).getValues();
      for (var j = 0; j < tData.length; j++) {
        var tRow = tData[j];
        var amountVal = Number(tRow[4]);
        if (!isNaN(amountVal) && amountVal > 0) {
          var rawType = tRow[3] ? tRow[3].toString() : "DEBT";
          var normType = (rawType.indexOf("سداد") !== -1 || rawType.indexOf("PAYMENT") !== -1 || rawType.indexOf("🟢") !== -1) ? "PAYMENT" : "DEBT";
          result.transactions.push({
            id: Number(tRow[0]) || 0,
            customerId: Number(tRow[1]) || 0,
            customerName: tRow[2] ? tRow[2].toString().trim() : "",
            type: normType,
            amount: amountVal,
            description: tRow[5] ? tRow[5].toString().trim() : "",
            paymentMethod: tRow[6] ? tRow[6].toString().trim() : "CASH",
            date: tRow[7] ? tRow[7].toString().trim() : "",
            timestamp: Number(tRow[8]) || new Date().getTime()
          });
        }
      }
    }

    // قراءة المنتجات
    var pSheet = ss.getSheetByName("المنتجات والأسعار");
    if (pSheet && pSheet.getLastRow() > 1) {
      var numPCols = Math.max(pSheet.getLastColumn(), 5);
      var pData = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, numPCols).getValues();
      for (var k = 0; k < pData.length; k++) {
        var pRow = pData[k];
        var pName = (pRow[1] !== undefined && pRow[1] !== null) ? pRow[1].toString().trim() : "";
        if (pName !== '' && pName !== "اسم المنتج") {
          result.products.push({
            id: Number(pRow[0]) || 0,
            name: pName,
            barcode: pRow[2] ? pRow[2].toString().replace("'", "").trim() : "",
            price: Number(pRow[3]) || 0,
            category: pRow[4] ? pRow[4].toString().trim() : "عام"
          });
        }
      }
    }

    return ContentService.createTextOutput(JSON.stringify({status: "success", data: result}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptCodeScreen(
    onBack: () -> Unit,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("كود Google Apps Script", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Apps Script", APPS_SCRIPT_TEMPLATE)
                            clipboard.setPrimaryClip(clip)
                            onCopied()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("btn_copy_script_code")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ الكود")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "طريقة الربط:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "1. افتح شيت جوجل جديد على حسابك.\n2. من القائمة اختر Extensions (الإضافات) ثم Apps Script.\n3. الصق الكود أدناه ثم اضغط Deploy -> New Deployment -> Web App (Anyone).\n4. انسخ رابط الـ URL وضعه في إعدادات المزامنة بالتطبيق.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = APPS_SCRIPT_TEMPLATE,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
