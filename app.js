/* ===================================================
   دفتر كاش (Daftarkash) - Ultra-Fast HD Barcode Engine & Fixed Navigation
   =================================================== */

// 1. Initialize Dexie Database
const db = new Dexie('DaftarKashDB');

db.version(1).stores({
  customers: '++id, name, phone, address, notes, creditLimit, createdAt, isArchived',
  transactions: '++id, customerId, type, amount, description, paymentMethod, date, timestamp',
  products: '++id, name, barcode, price, category',
  settings: 'key, value'
});

// App State
let currentFilter = 'all';
let selectedCustomerId = null;
let posCart = [];

// Scanner Engine State
let mediaStream = null;
let nativeDetector = null;
let isNativeScannerActive = false;
let fallbackScanner = null;
let isTorchOn = false;
let isScanningPaused = false;

// Navigation History Stack
let viewStack = ['viewLedger'];

// View Switcher
function navigateTo(viewId, title = '', pushToStack = true) {
  const views = document.querySelectorAll('.app-view');
  const targetView = document.getElementById(viewId);
  if (!targetView) return;

  views.forEach(v => v.classList.remove('active'));
  targetView.classList.add('active');

  const isRootView = ['viewLedger', 'viewQuickPOS', 'viewProducts', 'viewSettings'].includes(viewId);
  const brandSection = document.getElementById('headerBrandSection');
  const subSection = document.getElementById('headerSubSection');
  const subTitle = document.getElementById('headerSubTitle');
  const bottomNav = document.getElementById('bottomNav');

  if (isRootView) {
    brandSection.style.display = 'flex';
    subSection.style.display = 'none';
    bottomNav.style.display = 'flex';
    
    document.querySelectorAll('.bottom-nav .nav-item').forEach(b => {
      b.classList.toggle('active', b.dataset.view === viewId);
    });

    if (pushToStack) {
      viewStack = [viewId];
    }
  } else {
    brandSection.style.display = 'none';
    subSection.style.display = 'flex';
    subTitle.textContent = title || 'رجوع';
    bottomNav.style.display = 'none';

    if (pushToStack) {
      viewStack.push(viewId);
    }
  }

  // Stop camera when navigating away from POS
  if (viewId !== 'viewQuickPOS' && (isNativeScannerActive || fallbackScanner)) {
    stopBarcodeScanner();
  }

  window.scrollTo({ top: 0, behavior: 'instant' });
  lucide.createIcons();
}

function navigateBack() {
  if (viewStack.length > 1) {
    viewStack.pop();
    const prevViewId = viewStack[viewStack.length - 1];
    
    if (['viewLedger', 'viewQuickPOS', 'viewProducts', 'viewSettings'].includes(prevViewId)) {
      navigateTo(prevViewId, '', false);
      if (prevViewId === 'viewLedger') refreshLedgerView();
      if (prevViewId === 'viewProducts') renderProductsList();
    } else if (prevViewId === 'viewCustomerDetail' && selectedCustomerId) {
      openCustomerDetails(selectedCustomerId, false);
    } else {
      navigateTo(prevViewId, '', false);
    }
  } else {
    navigateTo('viewLedger', '', true);
    refreshLedgerView();
  }
}

document.getElementById('headerBackBtn').addEventListener('click', navigateBack);

// Audio & Haptic Feedback
function triggerScanFeedback() {
  // Vibration
  if (navigator.vibrate) {
    try { navigator.vibrate(80); } catch(e) {}
  }

  // Sound
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.setValueAtTime(1200, ctx.currentTime);
    gain.gain.setValueAtTime(0.25, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.12);
    osc.start();
    osc.stop(ctx.currentTime + 0.12);
  } catch (e) {}
}

function playCashSound() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.setValueAtTime(523.25, ctx.currentTime);
    osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.08);
    osc.frequency.setValueAtTime(783.99, ctx.currentTime + 0.16);
    gain.gain.setValueAtTime(0.25, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
    osc.start();
    osc.stop(ctx.currentTime + 0.35);
  } catch (e) {}
}

// 2. Pre-seed Default Data
async function seedDefaultDataIfNeeded() {
  const customerCount = await db.customers.count();
  if (customerCount === 0) {
    await db.settings.bulkPut([
      { key: 'storeName', value: 'بقالة البركة' },
      { key: 'storePhone', value: '01012345678' },
      { key: 'currency', value: 'ج.م' },
      { key: 'googleScriptUrl', value: 'https://script.google.com/macros/s/AKfycbyBTXls7mivEUxMuylSjazsqkAvg24Jo9UZqDhQJR3JT-B-BDwYNNTN4QoEkve4nh_Y/exec' }
    ]);

    await db.products.bulkPut([
      { name: 'سكر الأسرة 1 كجم', barcode: '6221001001', price: 35.0, category: 'بقالة أساسية' },
      { name: 'شاي العروسة 40 جم', barcode: '6221001002', price: 12.0, category: 'شاي ومشروبات' },
      { name: 'شاي العروسة 100 جم', barcode: '6221001003', price: 25.0, category: 'شاي ومشروبات' },
      { name: 'زيت كريستال عباد 800مل', barcode: '6221001004', price: 75.0, category: 'زيوت وسمن' },
      { name: 'مكرونة حواء 400 جم', barcode: '6221001005', price: 15.0, category: 'مكرونة وأرز' },
      { name: 'جبنة دومتي بلس 500 جم', barcode: '6221001006', price: 38.0, category: 'ألبان وجبن' },
      { name: 'تونة صن شاين مفتتة', barcode: '6221001007', price: 45.0, category: 'معلبات' },
      { name: 'سجاير كليوباترا بوكس', barcode: '6221001008', price: 34.5, category: 'سجاير ودخان' },
      { name: 'سجاير LM أزرق', barcode: '6221001009', price: 68.0, category: 'سجاير ودخان' },
      { name: 'إندومي خضار سوبر', barcode: '6221001010', price: 10.0, category: 'سناكس' },
      { name: 'بيبسي كانز 330 مل', barcode: '6221001011', price: 15.0, category: 'مشروبات غازية' }
    ]);

    const now = new Date();
    const c1Id = await db.customers.add({
      name: 'أحمد محمود عبد الرحمن',
      phone: '01012345678',
      address: 'شارع الجمهورية - عمارة 5',
      notes: 'جار المحل',
      creditLimit: 2000,
      createdAt: now.toISOString(),
      isArchived: 0
    });

    const c2Id = await db.customers.add({
      name: 'مصطفى علي كامل',
      phone: '01123456789',
      address: 'بجوار المسجد الكبير',
      notes: 'صاحب ورشة النجارة',
      creditLimit: 1500,
      createdAt: now.toISOString(),
      isArchived: 0
    });

    const c3Id = await db.customers.add({
      name: 'إبراهيم حسن النجار',
      phone: '01234567890',
      address: 'شارع المدارس',
      notes: '',
      creditLimit: 1000,
      createdAt: now.toISOString(),
      isArchived: 0
    });

    await db.transactions.bulkAdd([
      {
        customerId: c1Id,
        type: 'DEBT',
        amount: 350.0,
        description: 'طلبات أسبوع (سكر + زيت + شاي)',
        paymentMethod: 'CASH',
        date: '2026-08-12 10:30',
        timestamp: Date.now() - 5 * 86400000
      },
      {
        customerId: c1Id,
        type: 'PAYMENT',
        amount: 200.0,
        description: 'دفعة كاش',
        paymentMethod: 'CASH',
        date: '2026-08-14 18:45',
        timestamp: Date.now() - 3 * 86400000
      },
      {
        customerId: c1Id,
        type: 'DEBT',
        amount: 500.0,
        description: 'سجاير كليوباترا + ألبان وجبن',
        paymentMethod: 'CASH',
        date: '2026-08-17 09:15',
        timestamp: Date.now()
      },
      {
        customerId: c2Id,
        type: 'DEBT',
        amount: 1200.0,
        description: 'بضاعة شهرية للمنزل',
        paymentMethod: 'CASH',
        date: '2026-08-16 20:00',
        timestamp: Date.now() - 86400000
      },
      {
        customerId: c3Id,
        type: 'DEBT',
        amount: 400.0,
        description: 'بقالة متنوعة',
        paymentMethod: 'CASH',
        date: '2026-08-10 11:00',
        timestamp: Date.now() - 7 * 86400000
      },
      {
        customerId: c3Id,
        type: 'PAYMENT',
        amount: 400.0,
        description: 'سداد كامل الحساب نقدياً',
        paymentMethod: 'CASH',
        date: '2026-08-15 17:30',
        timestamp: Date.now() - 2 * 86400000
      }
    ]);
  }
}

// 3. Navigation Bar Initialization
function initNavigation() {
  const navButtons = document.querySelectorAll('.bottom-nav .nav-item');
  navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetViewId = btn.dataset.view;
      navigateTo(targetViewId, '', true);

      if (targetViewId === 'viewLedger') refreshLedgerView();
      if (targetViewId === 'viewProducts') renderProductsList();
      if (targetViewId === 'viewQuickPOS') populatePosCustomers();
      if (targetViewId === 'viewSettings') loadStoreSettings();
    });
  });
}

// Toast Notifications
function showToast(message, type = 'success') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast-msg ${type}`;
  toast.innerHTML = `<i data-lucide="${type === 'success' ? 'check-circle' : 'alert-circle'}"></i> <span>${message}</span>`;
  container.appendChild(toast);
  lucide.createIcons();

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(-20px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 2500);
}

// 4. Calculations & Balances
async function getCustomerBalance(customerId) {
  const txs = await db.transactions.where('customerId').equals(Number(customerId)).toArray();
  let balance = 0;
  for (const t of txs) {
    if (t.type === 'DEBT') balance += Number(t.amount);
    if (t.type === 'PAYMENT') balance -= Number(t.amount);
  }
  return balance;
}

// 5. Render Ledger View (الشاشة الرئيسية)
async function refreshLedgerView() {
  const customers = await db.customers.where('isArchived').equals(0).toArray();
  const txs = await db.transactions.toArray();

  let totalMarketDebt = 0;
  let todayCollections = 0;
  let debtorsCount = 0;
  let countSettled = 0;

  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);

  const customerBalances = new Map();
  const customerLastTx = new Map();

  for (const c of customers) {
    customerBalances.set(c.id, 0);
  }

  for (const t of txs) {
    if (customerBalances.has(t.customerId)) {
      const current = customerBalances.get(t.customerId);
      if (t.type === 'DEBT') customerBalances.set(t.customerId, current + Number(t.amount));
      if (t.type === 'PAYMENT') customerBalances.set(t.customerId, current - Number(t.amount));
    }

    if (t.type === 'PAYMENT' && t.timestamp >= startOfToday.getTime()) {
      todayCollections += Number(t.amount);
    }

    if (!customerLastTx.has(t.customerId) || t.timestamp > customerLastTx.get(t.customerId).timestamp) {
      customerLastTx.set(t.customerId, t);
    }
  }

  for (const [cId, balance] of customerBalances.entries()) {
    if (balance > 0) {
      totalMarketDebt += balance;
      debtorsCount++;
    } else {
      countSettled++;
    }
  }

  document.getElementById('totalMarketDebt').innerHTML = `${formatMoney(totalMarketDebt)} <span class="currency-unit-mini">ج.م</span>`;
  document.getElementById('todayCollections').innerHTML = `${formatMoney(todayCollections)} <span class="currency-unit-mini">ج.م</span>`;
  document.getElementById('debtorsCount').innerHTML = `${debtorsCount} <span class="currency-unit-mini">عميل</span>`;

  document.getElementById('countAll').textContent = customers.length;
  document.getElementById('countDebt').textContent = debtorsCount;
  document.getElementById('countSettled').textContent = countSettled;

  const searchQuery = document.getElementById('customerSearchInput').value.trim().toLowerCase();
  
  let filtered = customers.map(c => ({
    ...c,
    balance: customerBalances.get(c.id) || 0,
    lastTx: customerLastTx.get(c.id) || null
  }));

  if (searchQuery) {
    filtered = filtered.filter(c => 
      c.name.toLowerCase().includes(searchQuery) || 
      (c.phone && c.phone.includes(searchQuery))
    );
  }

  if (currentFilter === 'has_debt') {
    filtered = filtered.filter(c => c.balance > 0);
  } else if (currentFilter === 'settled') {
    filtered = filtered.filter(c => c.balance <= 0);
  } else if (currentFilter === 'top_debt') {
    filtered.sort((a, b) => b.balance - a.balance);
  }

  const container = document.getElementById('customersListContainer');
  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="empty-state">
        <i data-lucide="user-x"></i>
        <p>لا يوجد عملاء مطابقين للبحث أو التصفية</p>
      </div>
    `;
    lucide.createIcons();
    return;
  }

  container.innerHTML = filtered.map(c => {
    const isDebt = c.balance > 0;
    const lastDateStr = c.lastTx ? formatDateRelative(c.lastTx.timestamp) : '';

    return `
      <div class="customer-card" onclick="openCustomerDetails(${c.id})">
        <div class="customer-info">
          <h4 class="customer-name">${c.name}</h4>
          ${lastDateStr ? `<div class="customer-last-tx"><i data-lucide="clock"></i> ${lastDateStr}</div>` : ''}
        </div>
        <div class="customer-balance-box">
          <div class="balance-tag ${isDebt ? 'debt' : 'settled'}">
            ${formatMoney(c.balance)} <span class="currency-unit">ج.م</span>
          </div>
        </div>
      </div>
    `;
  }).join('');

  lucide.createIcons();
}

// 6. Customer Statement Screen Details
async function openCustomerDetails(customerId, pushToStack = true) {
  selectedCustomerId = Number(customerId);
  const customer = await db.customers.get(selectedCustomerId);
  if (!customer) return;

  const balance = await getCustomerBalance(selectedCustomerId);
  const txs = await db.transactions.where('customerId').equals(selectedCustomerId).reverse().sortBy('timestamp');

  document.getElementById('detailCustomerName').textContent = customer.name;
  const avatarElem = document.getElementById('detailCustomerAvatar');
  if (avatarElem) avatarElem.textContent = customer.name.charAt(0);
  document.getElementById('detailCustomerPhone').innerHTML = customer.phone ? `<i data-lucide="phone"></i> ${customer.phone}` : 'بدون هاتف مسجل';

  const balanceAmount = document.getElementById('detailBalanceAmount');
  const balanceBadge = document.getElementById('detailBalanceBadge');

  balanceAmount.innerHTML = `${formatMoney(balance)} <span class="currency-unit">ج.م</span>`;
  balanceBadge.textContent = 'الرصيد:';
  if (balance > 0) {
    balanceAmount.className = 'balance-tag-value text-danger';
  } else {
    balanceAmount.className = 'balance-tag-value text-success';
  }

  document.getElementById('detailTxCount').textContent = `${txs.length} حركة`;

  const timeline = document.getElementById('detailTransactionsList');
  if (txs.length === 0) {
    timeline.innerHTML = `
      <div class="empty-state">
        <i data-lucide="inbox"></i>
        <p>لا توجد أي حركات مسجلة لهذا العميل بعد.</p>
      </div>
    `;
  } else {
    timeline.innerHTML = txs.map(t => {
      const isDebt = t.type === 'DEBT';
      return `
        <div class="tx-card">
          <div class="tx-right">
            <div class="tx-icon-box ${isDebt ? 'debt' : 'payment'}">
              <i data-lucide="${isDebt ? 'plus' : 'minus'}"></i>
            </div>
            <div class="tx-details">
              <h5>${isDebt ? 'سحب بضاعة (دين)' : 'سداد فلوس (دفعة)'}</h5>
              <div class="tx-desc">${t.description || (isDebt ? 'بضاعة متنوعة' : 'دفعة نقدية')}</div>
              <div class="tx-date">${t.date || formatDate(t.timestamp)}</div>
            </div>
          </div>
          <div style="display:flex; align-items:center;">
            <div class="tx-amount ${isDebt ? 'debt' : 'payment'}">
              ${isDebt ? '+' : '-'}${formatMoney(t.amount)} ج
            </div>
            <button class="tx-delete-btn" onclick="deleteTransaction(${t.id})" title="حذف الحركة">
              <i data-lucide="trash-2" style="width:16px; height:16px;"></i>
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  document.getElementById('btnCustomerGoPOS').onclick = async () => {
    navigateTo('viewQuickPOS', `مسح مشتريات: ${customer.name}`, true);
    await populatePosCustomers();
    document.getElementById('posCustomerSelect').value = selectedCustomerId;
  };

  document.getElementById('btnCustomerAddDebt').onclick = () => openAddTxScreen('DEBT', customer.name);
  document.getElementById('btnCustomerAddPayment').onclick = () => openAddTxScreen('PAYMENT', customer.name);
  document.getElementById('btnCustomerWhatsApp').onclick = () => sendWhatsAppReminder(customer, balance);
  document.getElementById('btnCustomerPrintPDF').onclick = () => printCustomerStatement(customer, balance, txs);

  navigateTo('viewCustomerDetail', 'كشف حساب', pushToStack);
}

// 7. Add Transaction Screen
function openAddTxScreen(type = 'DEBT', customerName = '') {
  document.getElementById('fullTxAmount').value = '';
  document.getElementById('fullTxDesc').value = '';
  setFullTxType(type);
  
  const title = type === 'DEBT' ? `سحب بضاعة: ${customerName}` : `سداد دفعة: ${customerName}`;
  navigateTo('viewAddTransaction', title, true);
  setTimeout(() => document.getElementById('fullTxAmount').focus(), 150);
}

function setFullTxType(type) {
  document.getElementById('fullTxType').value = type;
  const tabDebt = document.getElementById('tabModeDebt');
  const tabPayment = document.getElementById('tabModePayment');
  const methodGroup = document.getElementById('fullTxMethodGroup');

  if (type === 'DEBT') {
    tabDebt.className = 'mode-tab active danger';
    tabPayment.className = 'mode-tab';
    methodGroup.style.display = 'none';
  } else {
    tabDebt.className = 'mode-tab';
    tabPayment.className = 'mode-tab active success';
    methodGroup.style.display = 'block';
  }
}

document.getElementById('tabModeDebt').addEventListener('click', () => setFullTxType('DEBT'));
document.getElementById('tabModePayment').addEventListener('click', () => setFullTxType('PAYMENT'));

document.querySelectorAll('.num-chip').forEach(btn => {
  btn.addEventListener('click', () => {
    const input = document.getElementById('fullTxAmount');
    const curVal = parseFloat(input.value) || 0;
    const addVal = parseFloat(btn.dataset.val);
    input.value = curVal + addVal;
  });
});

document.querySelectorAll('.desc-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    const descInput = document.getElementById('fullTxDesc');
    if (descInput.value) {
      descInput.value += ' + ' + chip.textContent;
    } else {
      descInput.value = chip.textContent;
    }
  });
});

document.getElementById('fullTransactionForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const customerId = selectedCustomerId;
  if (!customerId) return;

  const type = document.getElementById('fullTxType').value;
  const amount = parseFloat(document.getElementById('fullTxAmount').value);
  const description = document.getElementById('fullTxDesc').value.trim();
  const paymentMethod = document.getElementById('fullTxMethod').value;

  if (!amount || amount <= 0) {
    showToast('من فضلك أدخل مبلغاً صحيحاً أكبر من الصفر', 'danger');
    return;
  }

  const now = new Date();
  const dateFormatted = `${now.toLocaleDateString('ar-EG')} - ${now.toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}`;

  await db.transactions.add({
    customerId,
    type,
    amount,
    description: description || (type === 'DEBT' ? 'سحب بضاعة' : 'سداد نقدي'),
    paymentMethod,
    date: dateFormatted,
    timestamp: Date.now()
  });

  playCashSound();
  showToast(type === 'DEBT' ? 'تم تسجيل الدين بنجاح 🔴' : 'تم تسجيل السداد بنجاح 🟢');

  const newBalance = await getCustomerBalance(customerId);
  if (type === 'PAYMENT' && newBalance <= 0) {
    triggerConfetti();
  }

  viewStack.pop();
  await openCustomerDetails(customerId, false);
  scheduleAutoCloudSync(300);
});

async function deleteTransaction(txId) {
  if (!confirm('هل أنت متأكد من حذف هذه الحركة؟')) return;
  await db.transactions.delete(txId);
  showToast('تم حذف الحركة');
  if (selectedCustomerId) {
    await openCustomerDetails(selectedCustomerId, false);
  }
  scheduleAutoCloudSync(300);
}

// 8. Add Customer View Handlers
function openAddCustomerScreen() {
  document.getElementById('fullCustomerForm').reset();
  navigateTo('viewAddCustomer', 'إضافة عميل جديد', true);
  setTimeout(() => document.getElementById('fullCustName').focus(), 150);
}

document.getElementById('btnGoAddCustomerTop').addEventListener('click', openAddCustomerScreen);

document.getElementById('fullCustomerForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('fullCustName').value.trim();
  const phone = document.getElementById('fullCustPhone').value.trim();
  const initialDebt = parseFloat(document.getElementById('fullCustInitialDebt').value) || 0;

  if (!name) {
    showToast('اسم العميل مطلوب', 'danger');
    return;
  }

  const newCustomerId = await db.customers.add({
    name,
    phone,
    address: '',
    notes: '',
    creditLimit: 0,
    createdAt: new Date().toISOString(),
    isArchived: 0
  });

  if (initialDebt > 0) {
    const now = new Date();
    await db.transactions.add({
      customerId: newCustomerId,
      type: 'DEBT',
      amount: initialDebt,
      description: 'رصيد دين سابق عند إنشاء الحساب',
      paymentMethod: 'CASH',
      date: `${now.toLocaleDateString('ar-EG')} - ${now.toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}`,
      timestamp: Date.now()
    });
  }

  showToast(`تمت إضافة العميل (${name}) بنجاح`);
  navigateTo('viewLedger', '', false);
  await refreshLedgerView();
  scheduleAutoCloudSync(300);
});

// 9. Add Product Handlers
document.getElementById('btnGoAddProduct').addEventListener('click', () => {
  document.getElementById('fullProductForm').reset();
  navigateTo('viewAddProduct', 'إضافة صنف جديد', true);
  setTimeout(() => document.getElementById('fullProdName').focus(), 150);
});

document.getElementById('fullProductForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('fullProdName').value.trim();
  const barcode = document.getElementById('fullProdBarcode').value.trim();
  const price = parseFloat(document.getElementById('fullProdPrice').value) || 0;
  const category = document.getElementById('fullProdCategory').value.trim() || 'عام';

  await db.products.add({ name, barcode, price, category });
  showToast('تمت إضافة المنتج بنجاح');
  navigateTo('viewProducts', '', false);
  renderProductsList();
  performCloudSync(false);
});

// 10. WhatsApp Messenger
async function sendWhatsAppReminder(customer, balance) {
  if (!customer.phone) {
    const phoneInput = prompt('لم يتم تسجيل رقم هاتف لهذا العميل. يرجى إدخال رقم الواتساب:');
    if (!phoneInput) return;
    customer.phone = phoneInput;
    await db.customers.update(customer.id, { phone: phoneInput });
  }

  let formattedPhone = customer.phone.replace(/[^0-9]/g, '');
  if (formattedPhone.startsWith('0')) {
    formattedPhone = '20' + formattedPhone.substring(1);
  }

  const storeSettings = await getStoreSettings();
  const storeName = storeSettings.storeName || 'بقالة البركة';

  let message = '';
  if (balance > 0) {
    message = `السلام عليكم أستاذ ${customer.name} 🌹\n\nنود تذكير سيادتكم بأن رصيد حسابك الحالي طرف *${storeName}* هو:\n💰 *${formatMoney(balance)} ج.م*\n\nنشكركم لتعاملكم معنا ونسعد دائماً بخدمتكم! ✨`;
  } else {
    message = `السلام عليكم أستاذ ${customer.name} 🌹\n\nحسابك طرف *${storeName}* خالص بالكامل (0 ج.م).\nشكراً لالتزامك الدائم ونسعد بخدمتك دائماً! ✨`;
  }

  const whatsappUrl = `https://api.whatsapp.com/send?phone=${formattedPhone}&text=${encodeURIComponent(message)}`;
  window.open(whatsappUrl, '_blank');
}

// 11. Printable PDF Statement
async function printCustomerStatement(customer, balance, txs) {
  const store = await getStoreSettings();
  const printWindow = window.open('', '_blank');
  
  const rowsHtml = txs.map(t => `
    <tr>
      <td style="padding:8px; border-bottom:1px solid #ddd;">${t.date || formatDate(t.timestamp)}</td>
      <td style="padding:8px; border-bottom:1px solid #ddd;">${t.type === 'DEBT' ? '🔴 سحب بضاعة' : '🟢 سداد نقدي'}</td>
      <td style="padding:8px; border-bottom:1px solid #ddd;">${t.description}</td>
      <td style="padding:8px; border-bottom:1px solid #ddd; font-weight:bold; color:${t.type === 'DEBT' ? '#d32f2f' : '#2e7d32'}; direction:ltr; text-align:left;">
        ${t.type === 'DEBT' ? '+' : '-'}${formatMoney(t.amount)} ج.م
      </td>
    </tr>
  `).join('');

  printWindow.document.write(`
    <!DOCTYPE html>
    <html lang="ar" dir="rtl">
    <head>
      <meta charset="UTF-8">
      <title>كشف حساب - ${customer.name}</title>
      <style>
        body { font-family: 'Cairo', sans-serif, Tahoma; padding: 20px; direction: rtl; color: #111; }
        .header { text-align: center; border-bottom: 2px solid #222; padding-bottom: 12px; margin-bottom: 20px; }
        .meta { display: flex; justify-content: space-between; margin-bottom: 16px; font-size: 14px; }
        table { width: 100%; border-collapse: collapse; margin-top: 15px; }
        th { background-color: #f3f4f6; padding: 8px; border-bottom: 2px solid #333; text-align: right; }
        .total-box { margin-top: 20px; text-align: left; padding: 12px; background: #f9fafb; border-radius: 8px; font-size: 18px; font-weight: bold; }
      </style>
    </head>
    <body>
      <div class="header">
        <h2>${store.storeName || 'بقالة البركة'}</h2>
        <p>كشف حساب عميل وتفاصيل المعاملات</p>
      </div>
      <div class="meta">
        <div><strong>العميل:</strong> ${customer.name}</div>
        <div><strong>الهاتف:</strong> ${customer.phone || 'غير مسجل'}</div>
        <div><strong>تاريخ التقرير:</strong> ${new Date().toLocaleDateString('ar-EG')}</div>
      </div>
      <table>
        <thead>
          <tr>
            <th>التاريخ والوقت</th>
            <th>نوع الحركة</th>
            <th>البيان / الأصناف</th>
            <th style="text-align:left;">المبلغ</th>
          </tr>
        </thead>
        <tbody>
          ${rowsHtml}
        </tbody>
      </table>
      <div class="total-box">
        الرصيد المتبقي المطلوب: <span style="color:#d32f2f;">${formatMoney(balance)} ج.م</span>
      </div>
      <script>
        window.onload = () => { window.print(); }
      </script>
    </body>
    </html>
  `);
  printWindow.document.close();
}

// 12. Ultra-Fast HD Hardware-Accelerated Barcode Scanner Engine
async function startBarcodeScanner() {
  const statusBadge = document.getElementById('scannerStatusBadge');
  const videoElem = document.getElementById('hdScannerVideo');
  const aimBox = document.getElementById('scannerAimBox');
  const startBtn = document.getElementById('startScannerBtn');
  const stopBtn = document.getElementById('stopScannerBtn');
  const torchBtn = document.getElementById('toggleTorchBtn');

  statusBadge.textContent = 'جاري تشغيل الكاميرا بدقة HD...';
  isScanningPaused = false;

  // Check if native BarcodeDetector API is supported (Supported in Chrome Android natively)
  if ('BarcodeDetector' in window) {
    try {
      const formats = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'qr_code'];
      nativeDetector = new window.BarcodeDetector({ formats });

      const constraints = {
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1920, min: 1280 },
          height: { ideal: 1080, min: 720 },
          focusMode: { ideal: 'continuous' }
        }
      };

      mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      videoElem.srcObject = mediaStream;
      videoElem.style.display = 'block';
      document.getElementById('barcodeReader').style.display = 'none';
      aimBox.style.display = 'block';

      await videoElem.play();

      isNativeScannerActive = true;
      startBtn.style.display = 'none';
      stopBtn.style.display = 'inline-flex';
      
      // Check torch support
      const track = mediaStream.getVideoTracks()[0];
      const capabilities = track.getCapabilities ? track.getCapabilities() : {};
      if (capabilities.torch) {
        torchBtn.style.display = 'inline-flex';
      }

      statusBadge.textContent = 'المسح عالي السرعة نشط ⚡';
      statusBadge.className = 'badge text-success';

      runNativeBarcodeScanLoop();
      return;
    } catch (err) {
      console.warn('Native BarcodeDetector init failed, falling back to Html5Qrcode:', err);
    }
  }

  // Fallback: Html5Qrcode with crisp high-res configuration
  try {
    videoElem.style.display = 'none';
    const fallbackBox = document.getElementById('barcodeReader');
    fallbackBox.style.display = 'block';
    aimBox.style.display = 'none';

    if (!fallbackScanner) {
      fallbackScanner = new Html5Qrcode('barcodeReader');
    }

    await fallbackScanner.start(
      { facingMode: 'environment' },
      {
        fps: 25,
        qrbox: { width: 280, height: 160 },
        videoConstraints: {
          facingMode: 'environment',
          width: { ideal: 1280 },
          height: { ideal: 720 },
          focusMode: { ideal: 'continuous' }
        }
      },
      (decodedText) => {
        if (!isScanningPaused) {
          handleScannedBarcode(decodedText);
        }
      },
      (err) => {}
    );

    startBtn.style.display = 'none';
    stopBtn.style.display = 'inline-flex';
    statusBadge.textContent = 'الكاميرا نشطة - وجهها نحو الباركود';
    statusBadge.className = 'badge text-success';
  } catch (err) {
    console.error('Camera fallback error:', err);
    statusBadge.textContent = 'تعذر تشغيل الكاميرا (يرجى السماح بالإذن)';
    statusBadge.className = 'badge text-danger';
  }
}

// Native Scanner Frame Loop (0 latency hardware detector)
async function runNativeBarcodeScanLoop() {
  if (!isNativeScannerActive) return;

  const videoElem = document.getElementById('hdScannerVideo');
  if (videoElem.readyState >= 2 && !isScanningPaused) {
    try {
      const barcodes = await nativeDetector.detect(videoElem);
      if (barcodes.length > 0) {
        const rawValue = barcodes[0].rawValue;
        if (rawValue) {
          isScanningPaused = true;
          handleScannedBarcode(rawValue);
          setTimeout(() => { isScanningPaused = false; }, 1200); // 1.2s debounce
        }
      }
    } catch (e) {
      console.error(e);
    }
  }

  if (isNativeScannerActive) {
    requestAnimationFrame(runNativeBarcodeScanLoop);
  }
}

function stopBarcodeScanner() {
  // Stop native
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop());
    mediaStream = null;
  }
  isNativeScannerActive = false;

  // Stop fallback
  if (fallbackScanner && fallbackScanner.isScanning) {
    fallbackScanner.stop().catch(err => console.error(err));
  }

  document.getElementById('startScannerBtn').style.display = 'inline-flex';
  document.getElementById('stopScannerBtn').style.display = 'none';
  document.getElementById('toggleTorchBtn').style.display = 'none';
  document.getElementById('scannerAimBox').style.display = 'none';
  document.getElementById('hdScannerVideo').style.display = 'none';
  
  const statusBadge = document.getElementById('scannerStatusBadge');
  statusBadge.textContent = 'الكاميرا متوقفة';
  statusBadge.className = 'badge';
}

// Torch Toggle
document.getElementById('toggleTorchBtn').addEventListener('click', async () => {
  if (mediaStream) {
    const track = mediaStream.getVideoTracks()[0];
    isTorchOn = !isTorchOn;
    try {
      await track.applyConstraints({ advanced: [{ torch: isTorchOn }] });
      document.getElementById('toggleTorchBtn').style.color = isTorchOn ? 'var(--warning)' : 'var(--text-main)';
    } catch(e) {
      console.warn(e);
    }
  }
});

document.getElementById('startScannerBtn').addEventListener('click', startBarcodeScanner);
document.getElementById('stopScannerBtn').addEventListener('click', stopBarcodeScanner);

// Manual Barcode Input & USB Gun Scanner Listener
document.getElementById('btnLookupManualBarcode').addEventListener('click', () => {
  const val = document.getElementById('manualBarcodeInput').value.trim();
  if (val) {
    handleScannedBarcode(val);
    document.getElementById('manualBarcodeInput').value = '';
  }
});

document.getElementById('manualBarcodeInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    const val = e.target.value.trim();
    if (val) {
      handleScannedBarcode(val);
      e.target.value = '';
    }
  }
});

// Barcode Lookup & Cart Addition
async function handleScannedBarcode(barcode) {
  triggerScanFeedback();

  const product = await db.products.where('barcode').equals(barcode).first();
  if (product) {
    addToPosCart(product.barcode, product.name, product.price);
    showToast(`تمت إضافة: ${product.name} (${product.price}ج)`);
    return;
  }

  // Not in DB -> Prompt quick registration
  const nameInput = prompt(`صنف جديد! تم قراءة الباركود [${barcode}]\nاكتب اسم المنتج:`, '');
  if (!nameInput) return;
  const priceInput = prompt(`سعر بيع (${nameInput}) بالجنيه:`, '10');
  const price = parseFloat(priceInput) || 0;

  await db.products.add({ name: nameInput, barcode, price, category: 'عام' });
  addToPosCart(barcode, nameInput, price);
  renderQuickTags();
  showToast(`تم حفظ الصنف (${nameInput}) في قاعدة البيانات وإضافته للسلة 🛒`);
  performCloudSync(false);
}

// 13. POS Cart Operations
async function populatePosCustomers() {
  const select = document.getElementById('posCustomerSelect');
  const customers = await db.customers.where('isArchived').equals(0).toArray();
  
  select.innerHTML = '<option value="">-- اضغط لاختيار العميل أو اتركه لبيع كاش --</option>' + 
    customers.map(c => `<option value="${c.id}">${c.name} ${c.phone ? '(' + c.phone + ')' : ''}</option>`).join('');

  renderQuickTags();
}

async function renderQuickTags() {
  const container = document.getElementById('quickTagsContainer');
  const products = await db.products.limit(10).toArray();
  
  container.innerHTML = products.map(p => `
    <button type="button" class="tag-item-btn" onclick="addToPosCart('${p.barcode}', '${p.name}', ${p.price})">
      <span>${p.name}</span>
      <strong class="text-success">${p.price}ج</strong>
    </button>
  `).join('');
}

function addToPosCart(barcode, name, price) {
  const existing = posCart.find(item => item.barcode === barcode || item.name === name);
  if (existing) {
    existing.qty += 1;
  } else {
    posCart.push({ barcode, name, price: Number(price), qty: 1 });
  }
  renderPosCart();
}

function renderPosCart() {
  const body = document.getElementById('posCartBody');
  const totalDisplay = document.getElementById('posCartTotal');
  const saveDebtBtn = document.getElementById('posSaveAsDebtBtn');
  const saveCashBtn = document.getElementById('posSaveAsCashBtn');

  if (posCart.length === 0) {
    body.innerHTML = `
      <div class="empty-cart-msg">
        <i data-lucide="shopping-cart"></i>
        <p>امسح باركود صنف بالكاميرا أو اختر صنفاً سريعاً لإضافته</p>
      </div>
    `;
    totalDisplay.textContent = '0 ج.م';
    saveDebtBtn.disabled = true;
    saveCashBtn.disabled = true;
    lucide.createIcons();
    return;
  }

  let total = 0;
  body.innerHTML = posCart.map((item, idx) => {
    const itemTotal = item.qty * item.price;
    total += itemTotal;
    return `
      <div class="cart-row">
        <div><strong>${item.name}</strong></div>
        <div>
          <button class="icon-btn btn-sm" onclick="updateCartQty(${idx}, -1)" style="display:inline-flex; width:24px; height:24px;">-</button>
          <span style="margin: 0 4px;">${item.qty}</span>
          <button class="icon-btn btn-sm" onclick="updateCartQty(${idx}, 1)" style="display:inline-flex; width:24px; height:24px;">+</button>
        </div>
        <div>${item.price}ج</div>
        <div><strong>${itemTotal}ج</strong></div>
        <div>
          <button class="cart-row-remove" onclick="removeCartItem(${idx})">
            <i data-lucide="x" style="width:16px; height:16px;"></i>
          </button>
        </div>
      </div>
    `;
  }).join('');

  totalDisplay.textContent = `${formatMoney(total)} ج.م`;
  saveDebtBtn.disabled = false;
  saveCashBtn.disabled = false;
  lucide.createIcons();
}

function updateCartQty(index, delta) {
  if (posCart[index]) {
    posCart[index].qty += delta;
    if (posCart[index].qty <= 0) {
      posCart.splice(index, 1);
    }
  }
  renderPosCart();
}

function removeCartItem(index) {
  posCart.splice(index, 1);
  renderPosCart();
}

document.getElementById('posSaveAsDebtBtn').addEventListener('click', async () => {
  const customerId = document.getElementById('posCustomerSelect').value;
  if (!customerId) {
    showToast('يرجى اختيار العميل أولاً من القائمة أعلاه', 'danger');
    document.getElementById('posCustomerSelect').focus();
    return;
  }

  const total = posCart.reduce((sum, i) => sum + i.qty * i.price, 0);
  const itemsDesc = posCart.map(i => `${i.name} (${i.qty}x) ${i.barcode ? '[باركود: ' + i.barcode + ']' : ''}`).join(' + ');

  const now = new Date();
  await db.transactions.add({
    customerId: Number(customerId),
    type: 'DEBT',
    amount: total,
    description: itemsDesc,
    paymentMethod: 'CASH',
    date: `${now.toLocaleDateString('ar-EG')} - ${now.toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}`,
    timestamp: Date.now()
  });

  playCashSound();
  showToast(`تم تسجيل فاتورة (${formatMoney(total)} ج) على حساب العميل 🔴`);
  posCart = [];
  renderPosCart();
  
  if (selectedCustomerId && Number(customerId) === selectedCustomerId) {
    await openCustomerDetails(selectedCustomerId, false);
  } else {
    refreshLedgerView();
  }
  performCloudSync(false);
});

document.getElementById('posSaveAsCashBtn').addEventListener('click', async () => {
  const total = posCart.reduce((sum, i) => sum + i.qty * i.price, 0);
  playCashSound();
  showToast(`تم تسجيل بيع نقدي فوري (${formatMoney(total)} ج) 💵`);
  posCart = [];
  renderPosCart();
});

// 14. Products View Handlers
async function renderProductsList() {
  const container = document.getElementById('productsGridContainer');
  const query = document.getElementById('productSearchInput').value.trim().toLowerCase();
  
  let products = await db.products.toArray();
  if (query) {
    products = products.filter(p => p.name.toLowerCase().includes(query) || (p.barcode && p.barcode.includes(query)));
  }

  if (products.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="grid-column: 1/-1;">
        <i data-lucide="package-x"></i>
        <p>لا توجد منتجات مسجلة.</p>
      </div>
    `;
    lucide.createIcons();
    return;
  }

  container.innerHTML = products.map(p => `
    <div class="product-item-card">
      <div>
        <div class="product-name">${p.name}</div>
        <div class="product-barcode">الباركود: ${p.barcode || 'بدون'}</div>
      </div>
      <div class="product-bottom-row">
        <div class="product-price">${p.price} ج.م</div>
        <button class="edit-price-btn" onclick="quickEditPrice(${p.id}, ${p.price})">
          <i data-lucide="edit-2" style="width:12px; height:12px;"></i> تعديل السعر
        </button>
      </div>
    </div>
  `).join('');

  lucide.createIcons();
}

async function quickEditPrice(productId, currentPrice) {
  const newPrice = prompt('أدخل سعر البيع الجديد (ج.م):', currentPrice);
  if (newPrice && !isNaN(newPrice)) {
    await db.products.update(productId, { price: parseFloat(newPrice) });
    showToast('تم تحديث السعر بنجاح');
    renderProductsList();
  }
}

// 15. Excel & Settings
document.getElementById('exportFullExcelBackupBtn').addEventListener('click', async () => {
  const customers = await db.customers.toArray();
  const txs = await db.transactions.toArray();

  const customerData = [];
  for (const c of customers) {
    const balance = await getCustomerBalance(c.id);
    customerData.push({
      'رقم العميل': c.id,
      'اسم العميل': c.name,
      'رقم الهاتف': c.phone || '',
      'العنوان': c.address || '',
      'الرصيد المتبقي (المديونية)': balance
    });
  }

  const txData = txs.map(t => ({
    'رقم الحركة': t.id,
    'رقم العميل': t.customerId,
    'نوع الحركة': t.type === 'DEBT' ? 'دين / بضاعة' : 'سداد / دفع',
    'المبلغ': t.amount,
    'البيان': t.description,
    'التاريخ': t.date
  }));

  const wb = XLSX.utils.book_new();
  const wsCustomers = XLSX.utils.json_to_sheet(customerData);
  const wsTxs = XLSX.utils.json_to_sheet(txData);

  XLSX.utils.book_append_sheet(wb, wsCustomers, 'أرصدة العملاء');
  XLSX.utils.book_append_sheet(wb, wsTxs, 'سجل المعاملات');

  XLSX.writeFile(wb, `دفتر_كاش_نسخة_${new Date().toISOString().slice(0, 10)}.xlsx`);
  showToast('تم تصدير ملف الإكسيل بنجاح 📊');
});

document.getElementById('exportProductsExcelBtn').addEventListener('click', async () => {
  const products = await db.products.toArray();
  const ws = XLSX.utils.json_to_sheet(products.map(p => ({
    'اسم المنتج': p.name,
    'الباركود': p.barcode,
    'سعر البيع': p.price,
    'القسم': p.category
  })));
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'قائمة المنتجات');
  XLSX.writeFile(wb, 'منتجات_دفتر_كاش.xlsx');
  showToast('تم تصدير المنتجات لإكسيل');
});

document.getElementById('importProductsExcelInput').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = async (event) => {
    try {
      const data = new Uint8Array(event.target.result);
      const workbook = XLSX.read(data, { type: 'array' });
      const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
      const json = XLSX.utils.sheet_to_json(firstSheet);
      
      let count = 0;
      for (const row of json) {
        const name = row['اسم المنتج'] || row['Name'] || row['name'];
        const price = row['سعر البيع'] || row['Price'] || row['price'] || 0;
        const barcode = row['الباركود'] || row['Barcode'] || row['barcode'] || '';
        const category = row['القسم'] || row['Category'] || 'عام';

        if (name) {
          await db.products.add({ name, price: Number(price), barcode: String(barcode), category });
          count++;
        }
      }
      showToast(`تم استيراد ${count} صنف من الإكسيل بنجاح! 🎉`);
      renderProductsList();
    } catch (err) {
      console.error(err);
      showToast('حدث خطأ أثناء قراءة ملف الإكسيل', 'danger');
    }
  };
  reader.readAsArrayBuffer(file);
});

async function loadStoreSettings() {
  const store = await getStoreSettings();
  document.getElementById('settingStoreName').value = store.storeName || 'بقالة البركة';
  document.getElementById('settingStorePhone').value = store.storePhone || '01012345678';
  document.getElementById('settingCurrency').value = store.currency || 'ج.م';
  document.getElementById('settingGoogleScriptUrl').value = store.googleScriptUrl || '';
}

async function getStoreSettings() {
  const storeName = await db.settings.get('storeName');
  const storePhone = await db.settings.get('storePhone');
  const currency = await db.settings.get('currency');
  const googleScriptUrl = await db.settings.get('googleScriptUrl');

  return {
    storeName: storeName ? storeName.value : 'بقالة البركة',
    storePhone: storePhone ? storePhone.value : '01012345678',
    currency: currency ? currency.value : 'ج.م',
    googleScriptUrl: (googleScriptUrl && googleScriptUrl.value) ? googleScriptUrl.value : 'https://script.google.com/macros/s/AKfycbyBTXls7mivEUxMuylSjazsqkAvg24Jo9UZqDhQJR3JT-B-BDwYNNTN4QoEkve4nh_Y/exec'
  };
}

document.getElementById('saveStoreSettingsBtn').addEventListener('click', async () => {
  const storeName = document.getElementById('settingStoreName').value.trim();
  const storePhone = document.getElementById('settingStorePhone').value.trim();
  const currency = document.getElementById('settingCurrency').value.trim();
  const googleScriptUrl = document.getElementById('settingGoogleScriptUrl').value.trim();

  await db.settings.put({ key: 'storeName', value: storeName });
  await db.settings.put({ key: 'storePhone', value: storePhone });
  await db.settings.put({ key: 'currency', value: currency });
  await db.settings.put({ key: 'googleScriptUrl', value: googleScriptUrl });

  document.getElementById('displayStoreName').textContent = storeName;
  showToast('تم حفظ الإعدادات بنجاح');
});

function updateCloudIndicator(state = 'synced') {
  const pill = document.getElementById('cloudSyncIndicator');
  const dot = document.getElementById('syncStatusDot');
  const text = document.getElementById('cloudSyncText');
  const icon = document.getElementById('cloudSyncIcon');
  if (!pill) return;

  pill.classList.remove('syncing', 'offline');

  if (state === 'syncing') {
    pill.classList.add('syncing');
    text.textContent = 'جاري الحفظ... ⏳';
    icon.setAttribute('data-lucide', 'refresh-cw');
  } else if (state === 'offline') {
    pill.classList.add('offline');
    text.textContent = 'أوفلاين 📡';
    icon.setAttribute('data-lucide', 'cloud-off');
  } else {
    text.textContent = 'متزامن ☁️';
    icon.setAttribute('data-lucide', 'cloud');
  }
  lucide.createIcons();
}

let autoSyncTimer = null;
function scheduleAutoCloudSync(delayMs = 600) {
  if (!navigator.onLine) {
    updateCloudIndicator('offline');
    return;
  }

  updateCloudIndicator('syncing');
  clearTimeout(autoSyncTimer);
  autoSyncTimer = setTimeout(async () => {
    await performCloudSync(false);
  }, delayMs);
}

// Network Online/Offline Listeners
window.addEventListener('online', () => {
  updateCloudIndicator('syncing');
  showToast('تم استعادة الاتصال بالإنترنت 🌐 - جاري المزامنة السحابية');
  scheduleAutoCloudSync(200);
});

window.addEventListener('offline', () => {
  updateCloudIndicator('offline');
  showToast('أنت تعمل الآن في وضع الأوفلاين (بدون إنترنت) 📡', 'warning');
});

// Periodic Cloud Heartbeat (every 35 seconds if internet is active)
setInterval(() => {
  if (navigator.onLine) {
    scheduleAutoCloudSync(100);
  }
}, 35000);

async function performCloudSync(isManual = false) {
  if (!navigator.onLine) {
    updateCloudIndicator('offline');
    if (isManual) showToast('لا يوجد اتصال بالإنترنت حالياً. سيتم الحفظ تلقائياً عند الاتصال 📡', 'warning');
    return;
  }

  const store = await getStoreSettings();
  let scriptUrl = store.googleScriptUrl;
  const inputUrlElem = document.getElementById('settingGoogleScriptUrl');
  if (inputUrlElem && inputUrlElem.value.trim()) {
    scriptUrl = inputUrlElem.value.trim();
    await db.settings.put({ key: 'googleScriptUrl', value: scriptUrl });
  }

  if (!scriptUrl) {
    if (isManual) showToast('يرجى وضع رابط Google Apps Script أولاً في الحقل أعلاه', 'danger');
    updateCloudIndicator('synced');
    return;
  }

  updateCloudIndicator('syncing');
  const syncStatus = document.getElementById('syncStatusMessage');
  if (syncStatus && isManual) syncStatus.textContent = 'جاري المزامنة مع Google Sheets... ⏳';

  const customers = await db.customers.toArray();
  const txs = await db.transactions.toArray();
  const products = await db.products.toArray();

  const customerMap = new Map();
  const customersWithBalance = [];
  for (const c of customers) {
    const balance = await getCustomerBalance(c.id);
    customerMap.set(c.id, c.name);
    customersWithBalance.push({
      id: c.id,
      name: c.name,
      phone: c.phone || 'غير مسجل',
      balance: balance,
      createdAt: c.createdAt ? c.createdAt.slice(0, 10) : ''
    });
  }

  const txsWithNames = txs.map(t => ({
    id: t.id,
    customerName: customerMap.get(t.customerId) || `عميل #${t.customerId}`,
    type: t.type === 'DEBT' ? '🔴 سحب دين (+)' : '🟢 سداد دفعة (-)',
    amount: t.amount,
    description: t.description || '',
    date: t.date || formatDate(t.timestamp)
  }));

  const productsFormatted = products.map((p, idx) => ({
    id: p.id || (idx + 1),
    name: p.name || 'صنف',
    barcode: (p.barcode || '').toString().trim(),
    price: Number(p.price) || 0,
    category: p.category || 'عام'
  }));

  const payload = {
    storeName: store.storeName,
    syncTimestamp: new Date().toISOString(),
    customers: customersWithBalance,
    transactions: txsWithNames,
    products: productsFormatted
  };

  try {
    const payloadStr = JSON.stringify(payload);
    await fetch(scriptUrl, {
      method: 'POST',
      mode: 'no-cors',
      headers: {
        'Content-Type': 'text/plain;charset=utf-8'
      },
      body: payloadStr
    });

    updateCloudIndicator('synced');
    if (syncStatus) syncStatus.textContent = `آخر مزامنة ناجحة: ${new Date().toLocaleTimeString('ar-EG')}`;
    if (isManual) showToast('تمت المزامنة مع Google Sheets بنجاح! ☁️');
  } catch (err) {
    console.error('Cloud Sync Error:', err);
    updateCloudIndicator('offline');
    if (syncStatus && isManual) syncStatus.textContent = 'فشلت المزامنة. تأكد من صحة الرابط والاتصال بالإنترنت.';
    if (isManual) showToast('تعذر إتمام المزامنة مع جوجل شيت', 'danger');
  }
}

document.getElementById('manualSyncNowBtn').addEventListener('click', () => performCloudSync(true));

const APPS_SCRIPT_TEMPLATE = `function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // 1. ورقة العملاء والأرصدة الحالية
    var cSheet = ss.getSheetByName("العملاء والأرصدة") || ss.insertSheet("العملاء والأرصدة");
    cSheet.clear();
    cSheet.setRightToLeft(true);
    cSheet.appendRow(["رقم العميل", "اسم العميل", "رقم الهاتف", "الرصيد المتبقي (ج.م)", "تاريخ التسجيل"]);
    cSheet.getRange(1, 1, 1, 5).setBackground("#1E293B").setFontColor("#FFFFFF").setFontWeight("bold");
    
    if (data.customers && data.customers.length > 0) {
      var cRows = data.customers.map(function(c) {
        return [c.id, c.name, c.phone, c.balance, c.createdAt];
      });
      cSheet.getRange(2, 1, cRows.length, 5).setValues(cRows);
    }
    
    // 2. ورقة سجل المعاملات اليومية
    var tSheet = ss.getSheetByName("سجل المعاملات") || ss.insertSheet("سجل المعاملات");
    tSheet.clear();
    tSheet.setRightToLeft(true);
    tSheet.appendRow(["رقم الحركة", "اسم العميل", "نوع الحركة", "المبلغ (ج.م)", "تفاصيل البضاعة", "التاريخ والوقت"]);
    tSheet.getRange(1, 1, 1, 6).setBackground("#0F172A").setFontColor("#FFFFFF").setFontWeight("bold");
    
    if (data.transactions && data.transactions.length > 0) {
      var tRows = data.transactions.map(function(t) {
        return [t.id, t.customerName, t.type, t.amount, t.description, t.date];
      });
      tSheet.getRange(2, 1, tRows.length, 6).setValues(tRows);
    }

    // 3. ورقة قائمة المنتجات والأسعار والباركود
    var pSheet = ss.getSheetByName("المنتجات والأسعار") || ss.insertSheet("المنتجات والأسعار");
    pSheet.clear();
    pSheet.setRightToLeft(true);
    pSheet.appendRow(["رقم الصنف", "اسم المنتج", "الباركود", "سعر البيع (ج.م)", "القسم / التصنيف"]);
    pSheet.getRange(1, 1, 1, 5).setBackground("#1E3A8A").setFontColor("#FFFFFF").setFontWeight("bold");
    
    if (data.products && data.products.length > 0) {
      var pRows = data.products.map(function(p) {
        return [p.id, p.name, "'" + (p.barcode || ''), p.price, p.category || 'عام'];
      });
      pSheet.getRange(2, 1, pRows.length, 5).setValues(pRows);
    }
    
    return ContentService.createTextOutput(JSON.stringify({status: "success"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
};`;

document.getElementById('btnGoScriptCode').addEventListener('click', () => {
  document.getElementById('googleAppsScriptCodeSnippet').textContent = APPS_SCRIPT_TEMPLATE;
  navigateTo('viewScriptCode', 'كود Apps Script', true);
});

document.getElementById('copyAppsScriptBtn').addEventListener('click', () => {
  navigator.clipboard.writeText(APPS_SCRIPT_TEMPLATE);
  showToast('تم نسخ الكود إلى الحافظة 📋');
});

function triggerConfetti() {
  if (typeof confetti === 'function') {
    confetti({ particleCount: 80, spread: 70, origin: { y: 0.6 } });
  }
}

function formatMoney(amount) {
  return Number(amount || 0).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

function formatDate(timestamp) {
  const d = new Date(timestamp);
  return `${d.toLocaleDateString('ar-EG')} - ${d.toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}`;
}

function formatDateRelative(timestamp) {
  const diff = Date.now() - timestamp;
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'الآن';
  if (mins < 60) return `منذ ${mins} دقيقة`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `منذ ${hours} ساعة`;
  const days = Math.floor(hours / 24);
  if (days === 1) return 'أمس';
  if (days < 7) return `منذ ${days} أيام`;
  return new Date(timestamp).toLocaleDateString('ar-EG');
}

// Events
document.getElementById('customerSearchInput').addEventListener('input', (e) => {
  const val = e.target.value.trim();
  document.getElementById('clearSearchBtn').style.display = val ? 'flex' : 'none';
  refreshLedgerView();
});

document.getElementById('clearSearchBtn').addEventListener('click', () => {
  document.getElementById('customerSearchInput').value = '';
  document.getElementById('clearSearchBtn').style.display = 'none';
  refreshLedgerView();
});

document.querySelectorAll('.filter-chips .chip-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.filter-chips .chip-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentFilter = btn.dataset.filter;
    refreshLedgerView();
  });
});

document.getElementById('productSearchInput').addEventListener('input', renderProductsList);

document.getElementById('toggleThemeBtn').addEventListener('click', () => {
  const isLight = document.body.classList.toggle('theme-light');
  const icon = document.getElementById('themeIcon');
  icon.setAttribute('data-lucide', isLight ? 'sun' : 'moon');
  lucide.createIcons();
});

// Font Scaling Management
function applyFontScale(scale = 'md', save = true) {
  const root = document.documentElement;
  root.classList.remove('font-scale-sm', 'font-scale-md', 'font-scale-lg', 'font-scale-xl');
  root.classList.add(`font-scale-${scale}`);

  document.querySelectorAll('.font-size-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.scale === scale);
  });

  if (save) {
    localStorage.setItem('daftarkash_font_scale', scale);
    db.settings.put({ key: 'fontScale', value: scale });
    showToast(`تم ضبط حجم الخط: ${scale === 'sm' ? 'صغير' : scale === 'md' ? 'متوسط' : scale === 'lg' ? 'كبير' : 'ضخم'}`);
  }
}

async function loadFontScaleSetting() {
  const savedLocal = localStorage.getItem('daftarkash_font_scale');
  if (savedLocal) {
    applyFontScale(savedLocal, false);
    return;
  }
  const setting = await db.settings.get('fontScale');
  if (setting && setting.value) {
    applyFontScale(setting.value, false);
  } else {
    applyFontScale('md', false);
  }
}

document.querySelectorAll('.font-size-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const scale = btn.dataset.scale;
    applyFontScale(scale, true);
  });
});

window.addEventListener('DOMContentLoaded', async () => {
  await loadFontScaleSetting();
  await seedDefaultDataIfNeeded();
  initNavigation();
  await refreshLedgerView();
  lucide.createIcons();
});
