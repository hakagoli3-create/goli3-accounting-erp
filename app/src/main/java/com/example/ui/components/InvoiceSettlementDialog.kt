package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.PartyEntity
import com.example.ui.theme.AccountingCreditRed
import com.example.ui.theme.AccountingDebitGreen
import com.example.ui.theme.PersianGold
import com.example.util.JalaliDate
import com.example.util.NumberFormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSettlementDialog(
    totalAmount: Long,
    cashDesks: List<CashDeskEntity>,
    bankAccounts: List<BankAccountEntity>,
    selectedParty: PartyEntity?,
    currencyCode: String = "TOMAN",
    currencySymbol: String = "تومان",
    currencyRateToBase: Double = 1.0,
    invoiceType: String = "SALE",
    initialPaymentType: String = "CASH",
    initialCashAmount: Long = 0L,
    initialPosAmount: Long = 0L,
    initialChequeAmount: Long = 0L,
    initialCreditAmount: Long = 0L,
    initialNotes: String = "",
    isSubmitting: Boolean = false,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSettlement: (
        paymentType: String,
        cashAmount: Long,
        posAmount: Long,
        chequeAmount: Long,
        creditAmount: Long,
        cashDeskId: Long,
        bankAccountId: Long,
        chequeNumber: String,
        sayadNumber: String,
        chequeDueDate: String,
        chequeBankName: String,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var mode by remember(initialPaymentType) { mutableStateOf(initialPaymentType) }

    var selectedCashDeskId by remember {
        mutableLongStateOf(cashDesks.firstOrNull()?.id ?: 1L)
    }
    var selectedBankAccountId by remember {
        mutableLongStateOf(bankAccounts.firstOrNull()?.id ?: 1L)
    }

    var cashInput by remember(initialPaymentType, initialCashAmount, totalAmount) {
        mutableStateOf(if (initialPaymentType == "CASH") totalAmount.toString() else if (initialCashAmount > 0) initialCashAmount.toString() else "0")
    }
    var posInput by remember(initialPaymentType, initialPosAmount, totalAmount) {
        mutableStateOf(if (initialPaymentType == "POS") totalAmount.toString() else if (initialPosAmount > 0) initialPosAmount.toString() else "0")
    }
    var chequeInput by remember(initialPaymentType, initialChequeAmount, totalAmount) {
        mutableStateOf(if (initialPaymentType == "CHEQUE") totalAmount.toString() else if (initialChequeAmount > 0) initialChequeAmount.toString() else "0")
    }
    var creditInput by remember(initialPaymentType, initialCreditAmount, totalAmount) {
        mutableStateOf(if (initialPaymentType == "CREDIT") totalAmount.toString() else if (initialCreditAmount > 0) initialCreditAmount.toString() else "0")
    }

    var chequeNumber by remember { mutableStateOf("") }
    var sayadNumber by remember { mutableStateOf("") }
    var chequeDueDate by remember { mutableStateOf(JalaliDate.now().addDays(30).toStandardString()) }
    var chequeBankName by remember { mutableStateOf("بانک ملی ایران") }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }

    var cashDeskExpanded by remember { mutableStateOf(false) }
    var bankAccountExpanded by remember { mutableStateOf(false) }

    fun updateAmountsForMode(newMode: String) {
        mode = newMode
        when (newMode) {
            "CASH" -> {
                cashInput = totalAmount.toString()
                posInput = "0"
                chequeInput = "0"
                creditInput = "0"
            }
            "POS" -> {
                cashInput = "0"
                posInput = totalAmount.toString()
                chequeInput = "0"
                creditInput = "0"
            }
            "CREDIT" -> {
                cashInput = "0"
                posInput = "0"
                chequeInput = "0"
                creditInput = totalAmount.toString()
            }
            "CHEQUE" -> {
                cashInput = "0"
                posInput = "0"
                chequeInput = totalAmount.toString()
                creditInput = "0"
            }
            "MIXED" -> {
                // Keep current inputs or split evenly
            }
        }
    }

    val cashVal = NumberFormatUtils.parseLongSafe(cashInput, 0L)
    val posVal = NumberFormatUtils.parseLongSafe(posInput, 0L)
    val chequeVal = NumberFormatUtils.parseLongSafe(chequeInput, 0L)
    val creditVal = NumberFormatUtils.parseLongSafe(creditInput, 0L)
    val totalPaid = cashVal + posVal + chequeVal + creditVal
    val remainingBalance = totalAmount - totalPaid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                val settlementTitle = if (isEditing) {
                    when (invoiceType) {
                        "PURCHASE" -> "ویرایش تسویه فاکتور خرید"
                        "RETURN_SALE" -> "ویرایش تسویه مرجوعی فروش"
                        "RETURN_PURCHASE" -> "ویرایش تسویه مرجوعی خرید"
                        else -> "ویرایش تسویه فاکتور فروش"
                    }
                } else {
                    when (invoiceType) {
                        "PURCHASE" -> "تسویه و پرداخت فاکتور خرید"
                        "RETURN_SALE" -> "استرداد وجه و تسویه مرجوعی فروش"
                        "RETURN_PURCHASE" -> "دریافت وجه و تسویه مرجوعی خرید"
                        else -> "تسویه و دریافت وجه فاکتور فروش"
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = settlementTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "مبلغ قابل پرداخت: ${NumberFormatUtils.formatPrice(totalAmount)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Settlement Type Chips
                Text(
                    text = "شیوه تسویه حساب:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        "CASH" to "نقدی (صندوق)",
                        "POS" to "پوز / بانک",
                        "CHEQUE" to "چک صیادی",
                        "CREDIT" to "نسیه"
                    )
                    modes.forEach { (m, label) ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { updateAmountsForMode(m) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).testTag("settlement_chip_$m")
                        )
                    }
                }

                FilterChip(
                    selected = mode == "MIXED",
                    onClick = { updateAmountsForMode("MIXED") },
                    label = { Text("تسویه ترکیبی چندگانه (نقد + پوز + چک + نسیه)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("settlement_chip_MIXED")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Detailed Inputs for Cash / Bank / Cheque / Credit
                val isPurchaseOrReturnSale = invoiceType in listOf("PURCHASE", "RETURN_SALE")
                val isPurchaseOrReturnPurchase = invoiceType in listOf("PURCHASE", "RETURN_PURCHASE")
                val partyRoleName = if (isPurchaseOrReturnPurchase) "تأمین‌کننده" else "مشتری"

                if (mode == "MIXED" || mode == "CASH") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val cashTitle = if (isPurchaseOrReturnSale) "پرداخت نقدی از صندوق به طرف حساب" else "واریز نقدی مشتری به صندوق"
                            Text(cashTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (mode == "MIXED") {
                                OutlinedTextField(
                                    value = cashInput,
                                    onValueChange = { cashInput = it },
                                    label = { Text("مبلغ نقد (تومان)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            // Cash Desk selector
                            ExposedDropdownMenuBox(
                                expanded = cashDeskExpanded,
                                onExpandedChange = { cashDeskExpanded = it }
                            ) {
                                val currentDesk = cashDesks.find { it.id == selectedCashDeskId } ?: cashDesks.firstOrNull()
                                val deskLabel = if (isPurchaseOrReturnSale) "صندوق پرداخت‌کننده" else "صندوق مقصد"
                                OutlinedTextField(
                                    value = currentDesk?.name ?: "انتخاب صندوق",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(deskLabel) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cashDeskExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = cashDeskExpanded,
                                    onDismissRequest = { cashDeskExpanded = false }
                                ) {
                                    cashDesks.forEach { desk ->
                                        DropdownMenuItem(
                                            text = { Text("${desk.name} (مانده: ${NumberFormatUtils.formatPrice(desk.currentBalance)})") },
                                            onClick = {
                                                selectedCashDeskId = desk.id
                                                cashDeskExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (mode == "MIXED" || mode == "POS") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val posTitle = if (isPurchaseOrReturnSale) "پرداخت از حساب بانکی به طرف‌حساب" else "دریافت از طریق کارتخوان / بانک"
                            Text(posTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (mode == "MIXED") {
                                OutlinedTextField(
                                    value = posInput,
                                    onValueChange = { posInput = it },
                                    label = { Text("مبلغ پوز / بانک (تومان)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            // Bank Account selector
                            ExposedDropdownMenuBox(
                                expanded = bankAccountExpanded,
                                onExpandedChange = { bankAccountExpanded = it }
                            ) {
                                val currentBank = bankAccounts.find { it.id == selectedBankAccountId } ?: bankAccounts.firstOrNull()
                                val bankLabel = if (isPurchaseOrReturnSale) "حساب بانکی پرداخت‌کننده" else "حساب بانکی / پایانه پوز"
                                OutlinedTextField(
                                    value = currentBank?.bankName ?: "انتخاب حساب بانکی",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(bankLabel) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankAccountExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = bankAccountExpanded,
                                    onDismissRequest = { bankAccountExpanded = false }
                                ) {
                                    bankAccounts.forEach { bank ->
                                        DropdownMenuItem(
                                            text = { Text("${bank.bankName} - کارت: ${bank.cardNumber} (مانده: ${NumberFormatUtils.formatPrice(bank.currentBalance)})") },
                                            onClick = {
                                                selectedBankAccountId = bank.id
                                                bankAccountExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (mode == "MIXED" || mode == "CHEQUE") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val chkTitle = if (isPurchaseOrReturnSale) "اطلاعات چک صیادی پرداختی به طرف حساب" else "اطلاعات چک صیادی دریافتی از طرف حساب"
                            Text(chkTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (mode == "MIXED") {
                                OutlinedTextField(
                                    value = chequeInput,
                                    onValueChange = { chequeInput = it },
                                    label = { Text("مبلغ چک (تومان)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val chkNumLabel = if (isPurchaseOrReturnSale) "شماره چک پرداختی" else "شماره سریال چک"
                                OutlinedTextField(
                                    value = chequeNumber,
                                    onValueChange = { chequeNumber = it },
                                    label = { Text(chkNumLabel) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = sayadNumber,
                                    onValueChange = { sayadNumber = it },
                                    label = { Text("شناسه ۱۶ رقمی صیادی") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = chequeDueDate,
                                    onValueChange = { chequeDueDate = it },
                                    label = { Text("سررسید (۱۴۰۵/۰۲/۱۵)") },
                                    modifier = Modifier.weight(1f)
                                )
                                val chkBankLabel = if (isPurchaseOrReturnSale) "بانک پرداخت‌کننده (حساب ما)" else "نام بانک صادرکننده چک"
                                OutlinedTextField(
                                    value = chequeBankName,
                                    onValueChange = { chequeBankName = it },
                                    label = { Text(chkBankLabel) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (mode == "MIXED" || mode == "CREDIT") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedParty != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val creditSectionTitle = if (isPurchaseOrReturnPurchase) "خرید نسیه و مانده بدهی به تأمین‌کننده" else "فروش نسیه و مانده حساب مشتری"
                            Text(creditSectionTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedParty == null) {
                                Text(
                                    text = "توجه: برای ثبت نسیه، حتماً باید یک $partyRoleName از لیست انتخاب شده باشد.",
                                    color = AccountingCreditRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                val creditLimitText = if (isPurchaseOrReturnPurchase) "" else " | سقف اعتبار: ${NumberFormatUtils.formatPrice(selectedParty.creditLimit)}"
                                Text(
                                    text = "$partyRoleName: ${selectedParty.name}$creditLimitText | مانده حساب: ${NumberFormatUtils.formatPrice(selectedParty.currentBalance)}",
                                    fontSize = 11.sp
                                )
                            }
                            if (mode == "MIXED") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = creditInput,
                                    onValueChange = { creditInput = it },
                                    label = { Text("مبلغ نسیه (تومان)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Balance Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (remainingBalance == 0L) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مجموع ارقام وارد شده:", fontSize = 12.sp)
                            Text(NumberFormatUtils.formatPrice(totalPaid), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مانده تسویه نشده:", fontSize = 12.sp)
                            Text(
                                text = if (remainingBalance == 0L) "تسویه کامل (تراز)" else NumberFormatUtils.formatPrice(remainingBalance),
                                color = if (remainingBalance == 0L) AccountingDebitGreen else AccountingCreditRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (mode == "MIXED" && remainingBalance > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PersianGold.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        creditInput = (creditVal + remainingBalance).toString()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "تخصیص خودکار مانده (${NumberFormatUtils.formatPrice(remainingBalance)} تومان) به نسیه",
                                    fontSize = 11.sp,
                                    color = PersianGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        var finalCredit = creditVal
                        if (mode == "MIXED") {
                            if (remainingBalance > 0L) {
                                if (selectedParty == null) {
                                    Toast.makeText(context, "مجموع پرداختی کمتر از مبلغ فاکتور است. برای ثبت مانده به صورت نسیه، مشخص کردن $partyRoleName الزامی است.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalCredit += remainingBalance
                            } else if (remainingBalance < 0L) {
                                Toast.makeText(context, "مجموع مبالغ پرداختی از مبلغ فاکتور بیشتر است!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        if (mode == "CREDIT" || (mode == "MIXED" && finalCredit > 0)) {
                            if (selectedParty == null) {
                                Toast.makeText(context, "لطفاً ابتدا $partyRoleName را برای ثبت نسیه انتخاب کنید.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isPurchaseOrReturnPurchase && selectedParty.partyType != "SUPPLIER" && !selectedParty.canAcceptCredit(finalCredit)) {
                                Toast.makeText(context, "مبلغ نسیه از سقف اعتبار مشتری فراتر است!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        onConfirmSettlement(
                            mode,
                            cashVal,
                            posVal,
                            chequeVal,
                            finalCredit,
                            selectedCashDeskId,
                            selectedBankAccountId,
                            chequeNumber,
                            sayadNumber,
                            chequeDueDate,
                            chequeBankName,
                            notes
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_settlement_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSubmitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "در حال ذخیره تغییرات..." else "در حال ثبت نهایی...")
                    } else {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "ذخیره تغییرات فاکتور" else "ثبت نهایی و صدور فاکتور")
                    }
                }
            }
        }
    }
}
