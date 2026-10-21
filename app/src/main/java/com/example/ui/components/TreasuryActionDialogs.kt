package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.IncomeCategoryEntity
import com.example.data.model.PartyEntity
import com.example.ui.theme.AccountingCreditRed
import com.example.ui.theme.AccountingDebitGreen
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanBorder
import com.example.ui.theme.CleanCard
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.MatteGold
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.util.JalaliDate
import com.example.util.NumberFormatUtils

/**
 * دیالوگ مدرن و پیشرفته دریافت وجه / واریز به خزانه
 * با اتصال کامل به طرف‌حساب‌ها، سرفصل درآمدها، قابلیت ایجاد طرف‌حساب جدید و اعمال اتوماتیک منطق دوبل حسابداری
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedReceiptDialog(
    cashDesks: List<CashDeskEntity>,
    bankAccounts: List<BankAccountEntity>,
    parties: List<PartyEntity>,
    incomeCategories: List<IncomeCategoryEntity>,
    onDismiss: () -> Unit,
    onQuickAddParty: (PartyEntity, onCreated: (PartyEntity) -> Unit) -> Unit,
    onQuickAddIncomeCategory: (title: String, onCreated: (IncomeCategoryEntity) -> Unit) -> Unit,
    onConfirm: (
        isPartyReceipt: Boolean,
        partyId: Long?,
        partyName: String,
        incomeCategoryId: Long?,
        incomeCategoryTitle: String,
        title: String,
        amount: Long,
        destinationType: String,
        targetId: Long,
        jalaliDate: String,
        trackingNumber: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var isPartyMode by remember { mutableStateOf(true) } // true: طرف‌حساب, false: درآمد متفرقه

    var selectedParty by remember { mutableStateOf<PartyEntity?>(parties.firstOrNull { it.partyType in listOf("CUSTOMER", "BOTH") } ?: parties.firstOrNull()) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }
    var showQuickAddParty by remember { mutableStateOf(false) }

    var selectedIncomeCategory by remember { mutableStateOf<IncomeCategoryEntity?>(incomeCategories.firstOrNull()) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showQuickAddCategory by remember { mutableStateOf(false) }
    var newCategoryTitle by remember { mutableStateOf("") }

    var entryTitle by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var destinationType by remember { mutableStateOf("BANK") } // "CASH" or "BANK"
    var selectedCashId by remember { mutableStateOf(cashDesks.firstOrNull()?.id ?: 0L) }
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id ?: 0L) }
    var jalaliDate by remember { mutableStateOf(JalaliDate.now().toStandardString()) }
    var trackingNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val filteredParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties
        else parties.filter {
            it.name.contains(partySearchQuery, ignoreCase = true) ||
            it.code.contains(partySearchQuery, ignoreCase = true) ||
            it.mobile.contains(partySearchQuery)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = CleanCard,
                border = BorderStroke(1.dp, CleanBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccountingDebitGreen.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = AccountingDebitGreen, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "ثبت دریافت وجه / واریز به خزانه",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = NavyPrimary)
                                )
                                Text(
                                    text = "اتصال به معین طرف‌حساب و به‌روزرسانی موجودی صندوق/بانک",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextDarkSecondary)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextDarkSecondary)
                        }
                    }

                    HorizontalDivider(color = CleanBorder)

                    // نوع منبع دریافت: طرف‌حساب یا درآمد متفرقه
                    Text(text = "مبدأ و سرفصل دریافت:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isPartyMode,
                            onClick = { isPartyMode = true },
                            label = { Text("دریافت از طرف‌حساب / مشتری", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = MatteGold
                            ),
                            modifier = Modifier.weight(1.3f).testTag("receipt_party_mode_chip")
                        )
                        FilterChip(
                            selected = !isPartyMode,
                            onClick = { isPartyMode = false },
                            label = { Text("درآمد متفرقه و سایر", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = MatteGold
                            ),
                            modifier = Modifier.weight(1f).testTag("receipt_other_income_mode_chip")
                        )
                    }

                    // در صورت انتخاب دریافت از طرف‌حساب
                    if (isPartyMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "انتخاب طرف‌حساب (بدهکار / مشتری):", fontSize = 11.sp, color = TextDarkSecondary)
                            TextButton(
                                onClick = { showQuickAddParty = true },
                                modifier = Modifier.testTag("quick_add_party_btn_receipt")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعریف طرف‌حساب جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = partyDropdownExpanded,
                            onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedParty?.let { "${it.name} (کد: ${it.code})" } ?: "انتخاب از فهرست طرف‌حساب‌ها...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("طرف‌حساب پرداخت‌کننده *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MatteGold,
                                    unfocusedBorderColor = CleanBorder,
                                    focusedContainerColor = CleanSurface,
                                    unfocusedContainerColor = CleanSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("receipt_party_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = partyDropdownExpanded,
                                onDismissRequest = {
                                    partyDropdownExpanded = false
                                    partySearchQuery = ""
                                },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    OutlinedTextField(
                                        value = partySearchQuery,
                                        onValueChange = { partySearchQuery = it },
                                        placeholder = { Text("جستجوی نام، تلفن یا کد...", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                filteredParties.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                                    Text("کد: ${p.code} • تلفن: ${p.mobile.ifBlank { p.phone.ifBlank { "—" } }}", fontSize = 11.sp, color = TextDarkSecondary)
                                                }
                                                val (bText, bColor) = when {
                                                    p.currentBalance > 0 -> "بدهکار: ${NumberFormatUtils.formatPrice(p.currentBalance)}" to AccountingDebitGreen
                                                    p.currentBalance < 0 -> "بستانکار: ${NumberFormatUtils.formatPrice(-p.currentBalance)}" to AccountingCreditRed
                                                    else -> "تسویه" to TextDarkMuted
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = bColor.copy(alpha = 0.12f)
                                                ) {
                                                    Text(bText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedParty = p
                                            partyDropdownExpanded = false
                                            partySearchQuery = ""
                                            if (entryTitle.isBlank()) {
                                                entryTitle = "دریافت وجه از ${p.name}"
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // نمایش وضعیت مانده بدهی طرف‌حساب انتخاب‌شده
                        selectedParty?.let { p ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("مانده حساب فعلی:", fontSize = 10.sp, color = TextDarkSecondary)
                                        val bColor = if (p.currentBalance > 0) AccountingDebitGreen else if (p.currentBalance < 0) AccountingCreditRed else TextDarkMuted
                                        val bLabel = if (p.currentBalance > 0) "${NumberFormatUtils.formatPrice(p.currentBalance)} تومان (بدهکار به ما)"
                                                     else if (p.currentBalance < 0) "${NumberFormatUtils.formatPrice(-p.currentBalance)} تومان (بستانکار)"
                                                     else "بی‌حساب و تسویه"
                                        Text(bLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColor)
                                    }
                                    if (p.currentBalance > 0) {
                                        OutlinedButton(
                                            onClick = { amountText = p.currentBalance.toString() },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("تسویه کل بدهی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // در صورت انتخاب درآمد متفرقه
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "سرفصل درآمد:", fontSize = 11.sp, color = TextDarkSecondary)
                            TextButton(onClick = { showQuickAddCategory = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("سرفصل درآمد جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedIncomeCategory?.title ?: "انتخاب سرفصل درآمد...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("دسته‌بندی درآمد *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MatteGold,
                                    unfocusedBorderColor = CleanBorder,
                                    focusedContainerColor = CleanSurface,
                                    unfocusedContainerColor = CleanSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                incomeCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.title, fontSize = 13.sp, color = TextDarkPrimary) },
                                        onClick = {
                                            selectedIncomeCategory = cat
                                            categoryDropdownExpanded = false
                                            if (entryTitle.isBlank()) {
                                                entryTitle = "دریافت ${cat.title}"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // شرح و مبلغ
                    OutlinedTextField(
                        value = entryTitle,
                        onValueChange = { entryTitle = it },
                        label = { Text("شرح عملیات / بابت دریافت وجه *") },
                        placeholder = { Text(if (isPartyMode) "مثال: تسویه فاکتور دستی، پیش‌دریافت، واریز بدهی" else "مثال: واریز سود سپرده، فروش ضایعات") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("receipt_title_input")
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("مبلغ دریافتی (تومان) *") },
                        placeholder = { Text("۰") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            val a = amountText.toLongOrNull() ?: 0L
                            if (a > 0) {
                                Text("معادل: ${NumberFormatUtils.formatPrice(a)} تومان", color = MatteGold, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("receipt_amount_input")
                    )

                    // حساب مقصد (صندوق یا بانک)
                    Text(text = "حساب مقصد واریز:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = destinationType == "BANK",
                            onClick = { destinationType = "BANK" },
                            label = { Text("حساب بانکی / کارت‌خوان", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = destinationType == "CASH",
                            onClick = { destinationType = "CASH" },
                            label = { Text("صندوق نقدی و دخل", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (destinationType == "BANK") {
                        if (bankAccounts.isEmpty()) {
                            Text("هیچ حساب بانکی فعالی ثبت نشده است.", fontSize = 11.sp, color = AccountingCreditRed)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                bankAccounts.forEach { b ->
                                    val isSelected = selectedBankId == b.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                        border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedBankId = b.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = if (isSelected) NavyPrimary else TextDarkSecondary, modifier = Modifier.size(16.dp))
                                                Text(b.bankName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = TextDarkPrimary)
                                                if (b.cardNumber.isNotBlank()) {
                                                    Text("(${b.cardNumber.takeLast(4)}*)", fontSize = 10.sp, color = TextDarkSecondary)
                                                }
                                            }
                                            Text(NumberFormatUtils.formatPrice(b.currentBalance) + " ت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (b.currentBalance >= 0) AccountingDebitGreen else AccountingCreditRed)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (cashDesks.isEmpty()) {
                            Text("هیچ صندوق نقدی فعالی ثبت نشده است.", fontSize = 11.sp, color = AccountingCreditRed)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                cashDesks.forEach { c ->
                                    val isSelected = selectedCashId == c.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                        border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedCashId = c.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (isSelected) NavyPrimary else TextDarkSecondary, modifier = Modifier.size(16.dp))
                                                Text(c.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = TextDarkPrimary)
                                            }
                                            Text(NumberFormatUtils.formatPrice(c.currentBalance) + " ت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (c.currentBalance >= 0) AccountingDebitGreen else AccountingCreditRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = jalaliDate,
                            onValueChange = { jalaliDate = it },
                            label = { Text("تاریخ شمسی") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = trackingNumber,
                            onValueChange = { trackingNumber = it },
                            label = { Text("شماره فیش / پیگیری") },
                            placeholder = { Text("اختیاری") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("توضیحات تکمیلی سند") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0L
                            if (amount <= 0) {
                                Toast.makeText(context, "لطفاً مبلغ دریافتی را بیشتر از صفر وارد نمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (isPartyMode && selectedParty == null) {
                                Toast.makeText(context, "لطفاً طرف‌حساب را انتخاب نمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val targetId = if (destinationType == "CASH") selectedCashId else selectedBankId
                            if (targetId == 0L) {
                                Toast.makeText(context, "لطفاً حساب یا صندوق مقصد را انتخاب فرمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val title = if (entryTitle.isNotBlank()) entryTitle
                            else if (isPartyMode) "دریافت وجه از ${selectedParty?.name}"
                            else "دریافت درآمد ${selectedIncomeCategory?.title ?: "عملیاتی"}"

                            onConfirm(
                                isPartyMode,
                                selectedParty?.id,
                                selectedParty?.name ?: "",
                                selectedIncomeCategory?.id,
                                selectedIncomeCategory?.title ?: "",
                                title,
                                amount,
                                destinationType,
                                targetId,
                                jalaliDate,
                                trackingNumber,
                                notes
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccountingDebitGreen, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_receipt_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ثبت دریافت و صدور سند دوبل معین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // دیالوگ تعریف سریع طرف‌حساب جدید
        if (showQuickAddParty) {
            QuickAddPartyDialog(
                initialType = "CUSTOMER",
                onDismiss = { showQuickAddParty = false },
                onConfirm = { newParty ->
                    onQuickAddParty(newParty) { created ->
                        selectedParty = created
                        showQuickAddParty = false
                        if (entryTitle.isBlank()) {
                            entryTitle = "دریافت وجه از ${created.name}"
                        }
                    }
                }
            )
        }

        // دیالوگ افزودن سریع سرفصل درآمد
        if (showQuickAddCategory) {
            AlertDialog(
                onDismissRequest = { showQuickAddCategory = false },
                title = { Text("تعریف سرفصل درآمد جدید", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    OutlinedTextField(
                        value = newCategoryTitle,
                        onValueChange = { newCategoryTitle = it },
                        label = { Text("عنوان سرفصل درآمد *") },
                        placeholder = { Text("مثال: خدمات مشاوره‌ای") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCategoryTitle.isNotBlank()) {
                                onQuickAddIncomeCategory(newCategoryTitle) { created ->
                                    selectedIncomeCategory = created
                                    showQuickAddCategory = false
                                    newCategoryTitle = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                    ) {
                        Text("افزودن و انتخاب")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickAddCategory = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

/**
 * دیالوگ مدرن و پیشرفته پرداخت وجه / خروج از خزانه
 * با اتصال کامل به طرف‌حساب‌ها و سرفصل هزینه‌ها، قابلیت ایجاد طرف‌حساب و هزینه جدید و اعمال اتوماتیک منطق دوبل حسابداری
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedPaymentDialog(
    cashDesks: List<CashDeskEntity>,
    bankAccounts: List<BankAccountEntity>,
    parties: List<PartyEntity>,
    expenseCategories: List<ExpenseCategoryEntity>,
    onDismiss: () -> Unit,
    onQuickAddParty: (PartyEntity, onCreated: (PartyEntity) -> Unit) -> Unit,
    onQuickAddExpenseCategory: (title: String, description: String, onCreated: (ExpenseCategoryEntity) -> Unit) -> Unit,
    onConfirm: (
        isPartyPayment: Boolean,
        partyId: Long?,
        partyName: String,
        expenseCategoryId: Long?,
        expenseCategoryTitle: String,
        title: String,
        amount: Long,
        sourceType: String,
        sourceId: Long,
        jalaliDate: String,
        trackingNumber: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var isPartyMode by remember { mutableStateOf(true) } // true: پرداخت به طرف‌حساب/تامین‌کننده, false: پرداخت هزینه جاری

    var selectedParty by remember { mutableStateOf<PartyEntity?>(parties.firstOrNull { it.partyType in listOf("SUPPLIER", "BOTH") } ?: parties.firstOrNull()) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }
    var showQuickAddParty by remember { mutableStateOf(false) }

    var selectedExpenseCategory by remember { mutableStateOf<ExpenseCategoryEntity?>(expenseCategories.firstOrNull()) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showQuickAddExpenseCategory by remember { mutableStateOf(false) }
    var newExpenseTitle by remember { mutableStateOf("") }
    var newExpenseDesc by remember { mutableStateOf("") }

    var entryTitle by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf("BANK") } // "CASH" or "BANK"
    var selectedCashId by remember { mutableStateOf(cashDesks.firstOrNull()?.id ?: 0L) }
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id ?: 0L) }
    var jalaliDate by remember { mutableStateOf(JalaliDate.now().toStandardString()) }
    var trackingNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val filteredParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties
        else parties.filter {
            it.name.contains(partySearchQuery, ignoreCase = true) ||
            it.code.contains(partySearchQuery, ignoreCase = true) ||
            it.mobile.contains(partySearchQuery)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = CleanCard,
                border = BorderStroke(1.dp, CleanBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccountingCreditRed.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "ثبت پرداخت وجه / خروج از خزانه",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = NavyPrimary)
                                )
                                Text(
                                    text = "پرداخت به بستانکاران یا ثبت هزینه با کسر اتوماتیک از صندوق/بانک",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextDarkSecondary)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextDarkSecondary)
                        }
                    }

                    HorizontalDivider(color = CleanBorder)

                    // نوع پرداخت: طرف‌حساب یا پرداخت هزینه جاری
                    Text(text = "مقصد و نوع پرداخت وجه:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isPartyMode,
                            onClick = { isPartyMode = true },
                            label = { Text("پرداخت به طرف‌حساب / تامین‌کننده", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = MatteGold
                            ),
                            modifier = Modifier.weight(1.3f).testTag("payment_party_mode_chip")
                        )
                        FilterChip(
                            selected = !isPartyMode,
                            onClick = { isPartyMode = false },
                            label = { Text("پرداخت هزینه جاری", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = MatteGold
                            ),
                            modifier = Modifier.weight(1f).testTag("payment_expense_mode_chip")
                        )
                    }

                    // در صورت پرداخت به طرف‌حساب
                    if (isPartyMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "انتخاب طرف‌حساب (بستانکار / شخص):", fontSize = 11.sp, color = TextDarkSecondary)
                            TextButton(
                                onClick = { showQuickAddParty = true },
                                modifier = Modifier.testTag("quick_add_party_btn_payment")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعریف طرف‌حساب جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = partyDropdownExpanded,
                            onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedParty?.let { "${it.name} (کد: ${it.code})" } ?: "انتخاب از فهرست طرف‌حساب‌ها...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("طرف‌حساب دریافت‌کننده *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MatteGold,
                                    unfocusedBorderColor = CleanBorder,
                                    focusedContainerColor = CleanSurface,
                                    unfocusedContainerColor = CleanSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("payment_party_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = partyDropdownExpanded,
                                onDismissRequest = {
                                    partyDropdownExpanded = false
                                    partySearchQuery = ""
                                },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    OutlinedTextField(
                                        value = partySearchQuery,
                                        onValueChange = { partySearchQuery = it },
                                        placeholder = { Text("جستجوی نام، تلفن یا کد...", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                filteredParties.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                                    Text("کد: ${p.code} • ${if (p.partyType == "SUPPLIER") "تامین‌کننده" else "مشتری/شخص"}", fontSize = 11.sp, color = TextDarkSecondary)
                                                }
                                                val (bText, bColor) = when {
                                                    p.currentBalance < 0 -> "بستانکار: ${NumberFormatUtils.formatPrice(-p.currentBalance)}" to AccountingCreditRed
                                                    p.currentBalance > 0 -> "بدهکار: ${NumberFormatUtils.formatPrice(p.currentBalance)}" to AccountingDebitGreen
                                                    else -> "تسویه" to TextDarkMuted
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = bColor.copy(alpha = 0.12f)
                                                ) {
                                                    Text(bText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedParty = p
                                            partyDropdownExpanded = false
                                            partySearchQuery = ""
                                            if (entryTitle.isBlank()) {
                                                entryTitle = "پرداخت وجه به ${p.name}"
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // وضعیت مانده حساب طرف‌حساب انتخاب‌شده
                        selectedParty?.let { p ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("مانده حساب فعلی:", fontSize = 10.sp, color = TextDarkSecondary)
                                        val bColor = if (p.currentBalance < 0) AccountingCreditRed else if (p.currentBalance > 0) AccountingDebitGreen else TextDarkMuted
                                        val bLabel = if (p.currentBalance < 0) "${NumberFormatUtils.formatPrice(-p.currentBalance)} تومان (طلبکار / بستانکار از ما)"
                                                     else if (p.currentBalance > 0) "${NumberFormatUtils.formatPrice(p.currentBalance)} تومان (بدهکار به ما)"
                                                     else "بی‌حساب و تسویه"
                                        Text(bLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bColor)
                                    }
                                    if (p.currentBalance < 0) {
                                        OutlinedButton(
                                            onClick = { amountText = (-p.currentBalance).toString() },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("تسویه کل طلب", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // در صورت پرداخت هزینه جاری
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "سرفصل هزینه:", fontSize = 11.sp, color = TextDarkSecondary)
                            TextButton(
                                onClick = { showQuickAddExpenseCategory = true },
                                modifier = Modifier.testTag("quick_add_expense_category_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعریف سرفصل هزینه جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedExpenseCategory?.title ?: "انتخاب سرفصل هزینه...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("سرفصل هزینه جاری *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MatteGold,
                                    unfocusedBorderColor = CleanBorder,
                                    focusedContainerColor = CleanSurface,
                                    unfocusedContainerColor = CleanSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("payment_expense_category_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier.background(CleanSurface)
                            ) {
                                expenseCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(cat.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                                if (cat.description.isNotBlank()) {
                                                    Text(cat.description, fontSize = 11.sp, color = TextDarkSecondary)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedExpenseCategory = cat
                                            categoryDropdownExpanded = false
                                            if (entryTitle.isBlank()) {
                                                entryTitle = "پرداخت هزینه ${cat.title}"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // شرح و مبلغ پرداختی
                    OutlinedTextField(
                        value = entryTitle,
                        onValueChange = { entryTitle = it },
                        label = { Text("شرح عملیات / بابت پرداخت وجه *") },
                        placeholder = { Text(if (isPartyMode) "مثال: تسویه فاکتور خرید، مساعده، پرداخت بدهی" else "مثال: پرداخت اجاره فروردین، قبض برق، خرید کاغذ") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("payment_title_input")
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("مبلغ پرداختی (تومان) *") },
                        placeholder = { Text("۰") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            val a = amountText.toLongOrNull() ?: 0L
                            if (a > 0) {
                                Text("معادل: ${NumberFormatUtils.formatPrice(a)} تومان", color = MatteGold, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )

                    // حساب مبدأ پرداخت (صندوق یا بانک)
                    Text(text = "مبدأ پرداخت وجه (کسر از):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sourceType == "BANK",
                            onClick = { sourceType = "BANK" },
                            label = { Text("حساب بانکی / کارت", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = sourceType == "CASH",
                            onClick = { sourceType = "CASH" },
                            label = { Text("صندوق نقدی و دخل", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (sourceType == "BANK") {
                        if (bankAccounts.isEmpty()) {
                            Text("هیچ حساب بانکی فعالی ثبت نشده است.", fontSize = 11.sp, color = AccountingCreditRed)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                bankAccounts.forEach { b ->
                                    val isSelected = selectedBankId == b.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                        border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedBankId = b.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = if (isSelected) NavyPrimary else TextDarkSecondary, modifier = Modifier.size(16.dp))
                                                Text(b.bankName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = TextDarkPrimary)
                                                if (b.cardNumber.isNotBlank()) {
                                                    Text("(${b.cardNumber.takeLast(4)}*)", fontSize = 10.sp, color = TextDarkSecondary)
                                                }
                                            }
                                            Text(NumberFormatUtils.formatPrice(b.currentBalance) + " ت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (b.currentBalance >= 0) AccountingDebitGreen else AccountingCreditRed)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (cashDesks.isEmpty()) {
                            Text("هیچ صندوق نقدی فعالی ثبت نشده است.", fontSize = 11.sp, color = AccountingCreditRed)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                cashDesks.forEach { c ->
                                    val isSelected = selectedCashId == c.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                        border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedCashId = c.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (isSelected) NavyPrimary else TextDarkSecondary, modifier = Modifier.size(16.dp))
                                                Text(c.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, color = TextDarkPrimary)
                                            }
                                            Text(NumberFormatUtils.formatPrice(c.currentBalance) + " ت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (c.currentBalance >= 0) AccountingDebitGreen else AccountingCreditRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = jalaliDate,
                            onValueChange = { jalaliDate = it },
                            label = { Text("تاریخ شمسی") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = trackingNumber,
                            onValueChange = { trackingNumber = it },
                            label = { Text("شماره ارجاع / پیگیری") },
                            placeholder = { Text("اختیاری") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("توضیحات تکمیلی سند") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0L
                            if (amount <= 0) {
                                Toast.makeText(context, "لطفاً مبلغ پرداختی را بیشتر از صفر وارد نمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (isPartyMode && selectedParty == null) {
                                Toast.makeText(context, "لطفاً طرف‌حساب را انتخاب نمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val sourceId = if (sourceType == "CASH") selectedCashId else selectedBankId
                            if (sourceId == 0L) {
                                Toast.makeText(context, "لطفاً حساب یا صندوق مبدأ پرداخت را انتخاب فرمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val title = if (entryTitle.isNotBlank()) entryTitle
                            else if (isPartyMode) "پرداخت وجه به ${selectedParty?.name}"
                            else "پرداخت هزینه ${selectedExpenseCategory?.title ?: "عمومی"}"

                            onConfirm(
                                isPartyMode,
                                selectedParty?.id,
                                selectedParty?.name ?: "",
                                selectedExpenseCategory?.id,
                                selectedExpenseCategory?.title ?: "",
                                title,
                                amount,
                                sourceType,
                                sourceId,
                                jalaliDate,
                                trackingNumber,
                                notes
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_payment_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ثبت پرداخت و صدور سند دوبل معین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // دیالوگ تعریف سریع طرف‌حساب جدید
        if (showQuickAddParty) {
            QuickAddPartyDialog(
                initialType = "SUPPLIER",
                onDismiss = { showQuickAddParty = false },
                onConfirm = { newParty ->
                    onQuickAddParty(newParty) { created ->
                        selectedParty = created
                        showQuickAddParty = false
                        if (entryTitle.isBlank()) {
                            entryTitle = "پرداخت وجه به ${created.name}"
                        }
                    }
                }
            )
        }

        // دیالوگ تعریف سریع سرفصل هزینه جدید
        if (showQuickAddExpenseCategory) {
            AlertDialog(
                onDismissRequest = { showQuickAddExpenseCategory = false },
                title = { Text("تعریف سرفصل هزینه جدید", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newExpenseTitle,
                            onValueChange = { newExpenseTitle = it },
                            label = { Text("عنوان هزینه *") },
                            placeholder = { Text("مثال: هزینه مشاوره حقوقی") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newExpenseDesc,
                            onValueChange = { newExpenseDesc = it },
                            label = { Text("توضیحات سرفصل") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newExpenseTitle.isNotBlank()) {
                                onQuickAddExpenseCategory(newExpenseTitle, newExpenseDesc) { created ->
                                    selectedExpenseCategory = created
                                    showQuickAddExpenseCategory = false
                                    newExpenseTitle = ""
                                    newExpenseDesc = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                    ) {
                        Text("افزودن و انتخاب")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickAddExpenseCategory = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

/**
 * دیالوگ ثبت چک صیادی با کادر کشویی طرف‌حساب و قابلیت ایجاد طرف‌حساب جدید
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAddChequeDialog(
    parties: List<PartyEntity>,
    onDismiss: () -> Unit,
    onQuickAddParty: (PartyEntity, onCreated: (PartyEntity) -> Unit) -> Unit,
    onConfirm: (
        number: String,
        sayad: String,
        type: String,
        partyId: Long?,
        partyName: String,
        bank: String,
        branch: String,
        amount: Long,
        issue: String,
        due: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var number by remember { mutableStateOf("") }
    var sayad by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("RECEIVABLE") } // "RECEIVABLE" or "PAYABLE"

    var selectedParty by remember { mutableStateOf<PartyEntity?>(parties.firstOrNull()) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }
    var showQuickAddParty by remember { mutableStateOf(false) }

    var manualPartyName by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf(JalaliDate.now().toStandardString()) }
    var dueDate by remember { mutableStateOf(JalaliDate.now().addDays(30).toStandardString()) }
    var notes by remember { mutableStateOf("") }

    val filteredParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties
        else parties.filter {
            it.name.contains(partySearchQuery, ignoreCase = true) ||
            it.code.contains(partySearchQuery, ignoreCase = true) ||
            it.mobile.contains(partySearchQuery)
        }
    }

    val bankSuggestions = listOf("ملی", "صادرات", "ملت", "تجارت", "سپه", "پاسارگاد", "پارسیان", "سامان", "کشاورزی", "مسکن", "رفاه", "آینده", "رسالت")

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = CleanCard,
                border = BorderStroke(1.dp, CleanBorder),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MatteGold.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MatteGold, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column {
                                Text("ثبت چک صیادی در خزانه‌داری", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary))
                                Text("اتصال به طرف‌حساب و صدور سند اسناد دریافتنی/پرداختنی", fontSize = 11.sp, color = TextDarkSecondary)
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "بستن") }
                    }

                    HorizontalDivider(color = CleanBorder)

                    // نوع چک
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "RECEIVABLE",
                            onClick = { type = "RECEIVABLE" },
                            label = { Text("چک دریافتی (از مشتری)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccountingDebitGreen,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).testTag("cheque_receivable_chip")
                        )
                        FilterChip(
                            selected = type == "PAYABLE",
                            onClick = { type = "PAYABLE" },
                            label = { Text("چک پرداختی (به تامین‌کننده)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MatteGold,
                                selectedLabelColor = NavyDark
                            ),
                            modifier = Modifier.weight(1f).testTag("cheque_payable_chip")
                        )
                    }

                    // کادر کشویی طرف‌حساب + دکمه تعریف سریع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (type == "RECEIVABLE") "طرف‌حساب صادرکننده چک:" else "طرف‌حساب ذینفع چک:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )
                        TextButton(
                            onClick = { showQuickAddParty = true },
                            modifier = Modifier.testTag("quick_add_party_btn_cheque")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعریف طرف‌حساب جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = partyDropdownExpanded,
                        onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedParty?.let { "${it.name} (کد: ${it.code})" } ?: manualPartyName.ifBlank { "انتخاب از طرف‌حساب‌ها یا تایپ نام..." },
                            onValueChange = {
                                manualPartyName = it
                                selectedParty = null
                            },
                            readOnly = false,
                            label = { Text("طرف‌حساب / صادرکننده *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MatteGold,
                                unfocusedBorderColor = CleanBorder,
                                focusedContainerColor = CleanSurface,
                                unfocusedContainerColor = CleanSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("cheque_party_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = partyDropdownExpanded,
                            onDismissRequest = {
                                partyDropdownExpanded = false
                                partySearchQuery = ""
                            },
                            modifier = Modifier.background(CleanSurface)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = partySearchQuery,
                                    onValueChange = { partySearchQuery = it },
                                    placeholder = { Text("جستجوی نام یا کد طرف‌حساب...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            filteredParties.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                            Text("کد: ${p.code}", fontSize = 11.sp, color = TextDarkSecondary)
                                        }
                                    },
                                    onClick = {
                                        selectedParty = p
                                        manualPartyName = p.name
                                        partyDropdownExpanded = false
                                        partySearchQuery = ""
                                    }
                                )
                            }
                        }
                    }

                    // شماره چک و شناسه صیادی
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("شماره چک *") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("cheque_number_input")
                        )
                        OutlinedTextField(
                            value = sayad,
                            onValueChange = { if (it.length <= 16) sayad = it.filter { c -> c.isDigit() } },
                            label = { Text("شناسه صیادی (۱۶ رقم)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                Text("${sayad.length}/۱۶ رقم", fontSize = 10.sp, color = if (sayad.length == 16) AccountingDebitGreen else TextDarkSecondary)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f).testTag("cheque_sayad_input")
                        )
                    }

                    // بانک و شعبه
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bank,
                            onValueChange = { bank = it },
                            label = { Text("نام بانک *") },
                            placeholder = { Text("مثال: ملت") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f).testTag("cheque_bank_input")
                        )
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("شعبه") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    // بانک‌های پیشنهادی سریع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bankSuggestions.take(6).forEach { bName ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (bank == bName) MatteGold.copy(alpha = 0.2f) else CleanBackground,
                                border = BorderStroke(1.dp, if (bank == bName) MatteGold else CleanBorder),
                                modifier = Modifier.clickable { bank = bName }
                            ) {
                                Text(bName, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = TextDarkPrimary)
                            }
                        }
                    }

                    // مبلغ چک
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("مبلغ چک به تومان *") },
                        placeholder = { Text("۰") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            val a = amountText.toLongOrNull() ?: 0L
                            if (a > 0) {
                                Text("معادل: ${NumberFormatUtils.formatPrice(a)} تومان", color = MatteGold, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("cheque_amount_input")
                    )

                    // تاریخ صدور و سررسید
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = issueDate,
                            onValueChange = { issueDate = it },
                            label = { Text("تاریخ صدور") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dueDate,
                            onValueChange = { dueDate = it },
                            label = { Text("تاریخ سررسید *") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("cheque_due_date_input")
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("توضیحات و بابت") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0L
                            val pName = selectedParty?.name ?: manualPartyName.trim()
                            if (amount <= 0 || number.isBlank() || bank.isBlank() || pName.isBlank()) {
                                Toast.makeText(context, "لطفاً شماره چک، بانک، طرف‌حساب و مبلغ را تکمیل فرمایید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            onConfirm(
                                number.trim(),
                                sayad.trim(),
                                type,
                                selectedParty?.id,
                                pName,
                                bank.trim(),
                                branch.trim(),
                                amount,
                                issueDate,
                                dueDate,
                                notes.trim()
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_add_cheque_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MatteGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ثبت چک صیادی و صدور سند معین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        if (showQuickAddParty) {
            QuickAddPartyDialog(
                initialType = if (type == "RECEIVABLE") "CUSTOMER" else "SUPPLIER",
                onDismiss = { showQuickAddParty = false },
                onConfirm = { newParty ->
                    onQuickAddParty(newParty) { created ->
                        selectedParty = created
                        manualPartyName = created.name
                        showQuickAddParty = false
                    }
                }
            )
        }
    }
}

/**
 * دیالوگ وصول و پاس شدن چک با عملیات کامل حسابداری و به‌روزرسانی موجودی بانک یا صندوق
 */
@Composable
fun ChequeClearDialog(
    cheque: ChequeEntity,
    bankAccounts: List<BankAccountEntity>,
    cashDesks: List<CashDeskEntity>,
    onDismiss: () -> Unit,
    onConfirm: (accountType: String, accountId: Long) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("BANK") } // "BANK" or "CASH"
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id ?: 0L) }
    var selectedCashId by remember { mutableStateOf(cashDesks.firstOrNull()?.id ?: 0L) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).background(AccountingDebitGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccountingDebitGreen, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = if (cheque.type == "RECEIVABLE") "وصول چک دریافتی (واریز به حساب)" else "پاس شدن چک پرداختی (برداشت از حساب)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NavyPrimary
                        )
                        Text(
                            text = "چک شماره ${NumberFormatUtils.toPersianDigits(cheque.chequeNumber)} • مبلغ: ${NumberFormatUtils.formatPrice(cheque.amount)} تومان",
                            fontSize = 11.sp,
                            color = TextDarkSecondary
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (cheque.type == "RECEIVABLE") "وجه این چک به کدام حساب بانکی یا صندوق واریز گردید؟" else "مبلغ چک از کدام حساب بانکی کسر گردید؟",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDarkPrimary
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "BANK",
                            onClick = { selectedType = "BANK" },
                            label = { Text("حساب بانکی", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == "CASH",
                            onClick = { selectedType = "CASH" },
                            label = { Text("صندوق نقدی", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedType == "BANK") {
                        bankAccounts.forEach { b ->
                            val isSelected = selectedBankId == b.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                modifier = Modifier.fillMaxWidth().clickable { selectedBankId = b.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(b.bankName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text(NumberFormatUtils.formatPrice(b.currentBalance) + " ت", fontSize = 11.sp, color = AccountingDebitGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        cashDesks.forEach { c ->
                            val isSelected = selectedCashId == c.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else CleanBackground,
                                border = BorderStroke(1.dp, if (isSelected) NavyPrimary else CleanBorder),
                                modifier = Modifier.fillMaxWidth().clickable { selectedCashId = c.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text(NumberFormatUtils.formatPrice(c.currentBalance) + " ت", fontSize = 11.sp, color = AccountingDebitGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val accId = if (selectedType == "BANK") selectedBankId else selectedCashId
                        if (accId == 0L) {
                            Toast.makeText(context, "لطفاً حساب مورد نظر را انتخاب نمایید.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onConfirm(selectedType, accId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccountingDebitGreen)
                ) {
                    Text("ثبت وصول و اعمال در موجودی")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("انصراف", color = TextDarkSecondary) }
            }
        )
    }
}

/**
 * دیالوگ اعلام برگشت چک و احیای حساب طرف‌حساب در معین
 */
@Composable
fun ChequeBounceDialog(
    cheque: ChequeEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccountingCreditRed)
                    Text("ثبت برگشت چک صیادی", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccountingCreditRed)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "آیا از ثبت وضعیت «برگشت خورده» برای چک شماره ${cheque.chequeNumber} به مبلغ ${NumberFormatUtils.formatPrice(cheque.amount)} تومان اطمینان دارید؟",
                        fontSize = 13.sp,
                        color = TextDarkPrimary
                    )
                    Text(
                        text = if (cheque.type == "RECEIVABLE")
                            "توجه: با ثبت برگشت این چک، مبلغ به بدهی طرف‌حساب (${cheque.partyName}) در دفتر معین بازگردانده شده و سند برگشت صادر می‌شود."
                        else
                            "توجه: با ثبت برگشت این چک، بستانکاری طرف‌حساب (${cheque.partyName}) مجدداً در سیستم احیا می‌گردد.",
                        fontSize = 11.sp,
                        color = TextDarkSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed)
                ) {
                    Text("تأیید و برگشت زدن چک")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        )
    }
}
