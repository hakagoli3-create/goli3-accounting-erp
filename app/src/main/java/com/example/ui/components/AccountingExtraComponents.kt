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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.AccountGroupEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.SubsidiaryAccountEntity
import com.example.ui.theme.AccountingCreditRed
import com.example.ui.theme.AccountingDebitGreen
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanBorder
import com.example.ui.theme.CleanCard
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.MatteGold
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PersianGold
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.util.NumberFormatUtils

/**
 * دیالوگ ایجاد حساب کل جدید زیرمجموعه گروه‌های حسابداری
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGeneralAccountDialog(
    groups: List<AccountGroupEntity>,
    generals: List<GeneralAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (groupCode: Int, code: Int, title: String, nature: String) -> Unit
) {
    val context = LocalContext.current
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull()) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    val suggestedCode = remember(selectedGroup, generals) {
        val grp = selectedGroup?.code ?: 1
        val existing = generals.filter { it.groupCode == grp }.map { it.code }
        val maxCode = existing.maxOrNull() ?: (grp * 100)
        (maxCode + 1).toString()
    }

    var codeText by remember(suggestedCode) { mutableStateOf(suggestedCode) }
    var title by remember { mutableStateOf("") }
    var nature by remember(selectedGroup) { mutableStateOf(selectedGroup?.nature ?: "DEBIT") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NavyPrimary)
                    Text("تعریف حساب کل جدید", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("گروه حسابداری مربوطه:", fontSize = 11.sp, color = TextDarkSecondary)

                    ExposedDropdownMenuBox(
                        expanded = groupDropdownExpanded,
                        onExpandedChange = { groupDropdownExpanded = !groupDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedGroup?.let { "گروه ${it.code} - ${it.title}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("گروه حساب") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = groupDropdownExpanded,
                            onDismissRequest = { groupDropdownExpanded = false }
                        ) {
                            groups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text("گروه ${g.code} - ${g.title} (${if (g.nature == "DEBIT") "بدهکار" else "بستانکار"})") },
                                    onClick = {
                                        selectedGroup = g
                                        nature = g.nature
                                        groupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { codeText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("کد حساب کل *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("ماهیت حساب:", fontSize = 11.sp, color = TextDarkSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = nature == "DEBIT",
                                    onClick = { nature = "DEBIT" },
                                    label = { Text("بدهکار", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = nature == "CREDIT",
                                    onClick = { nature = "CREDIT" },
                                    label = { Text("بستانکار", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان حساب کل *") },
                        placeholder = { Text("مثلاً: سرمایه‌گذاری کوتاه‌مدت یا مالیات بر ارزش افزوده") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val grp = selectedGroup ?: return@Button
                        val c = codeText.toIntOrNull() ?: 0
                        if (c <= 0 || title.isBlank()) {
                            Toast.makeText(context, "لطفاً کد و عنوان را به درستی وارد نمایید.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onConfirm(grp.code, c, title.trim(), nature)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("ثبت حساب کل", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("انصراف")
                }
            }
        )
    }
}

/**
 * دیالوگ ایجاد حساب معین جدید زیرمجموعه یک حساب کل
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubsidiaryAccountDialog(
    generals: List<GeneralAccountEntity>,
    subsidiaries: List<SubsidiaryAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (generalCode: Int, code: Int, title: String, nature: String) -> Unit
) {
    val context = LocalContext.current
    var selectedGeneral by remember { mutableStateOf(generals.firstOrNull()) }
    var genDropdownExpanded by remember { mutableStateOf(false) }

    val suggestedCode = remember(selectedGeneral, subsidiaries) {
        val gen = selectedGeneral?.code ?: 101
        val existing = subsidiaries.filter { it.generalCode == gen }.map { it.code }
        val maxCode = existing.maxOrNull() ?: (gen * 100)
        (maxCode + 1).toString()
    }

    var codeText by remember(suggestedCode) { mutableStateOf(suggestedCode) }
    var title by remember { mutableStateOf("") }
    var nature by remember(selectedGeneral) { mutableStateOf(selectedGeneral?.nature ?: "DEBIT") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NavyPrimary)
                    Text("تعریف حساب معین جدید", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("انتخاب حساب کل والد:", fontSize = 11.sp, color = TextDarkSecondary)

                    ExposedDropdownMenuBox(
                        expanded = genDropdownExpanded,
                        onExpandedChange = { genDropdownExpanded = !genDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedGeneral?.let { "کل ${it.code} - ${it.title}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("حساب کل") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genDropdownExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = genDropdownExpanded,
                            onDismissRequest = { genDropdownExpanded = false }
                        ) {
                            generals.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text("کل ${g.code} - ${g.title}") },
                                    onClick = {
                                        selectedGeneral = g
                                        nature = g.nature
                                        genDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { codeText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("کد حساب معین *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("ماهیت حساب:", fontSize = 11.sp, color = TextDarkSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = nature == "DEBIT",
                                    onClick = { nature = "DEBIT" },
                                    label = { Text("بدهکار", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = nature == "CREDIT",
                                    onClick = { nature = "CREDIT" },
                                    label = { Text("بستانکار", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان حساب معین *") },
                        placeholder = { Text("مثلاً: تنخواه‌گردان شماره ۲، هزینه اینترنت و وب...") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val gen = selectedGeneral ?: return@Button
                        val c = codeText.toIntOrNull() ?: 0
                        if (c <= 0 || title.isBlank()) {
                            Toast.makeText(context, "لطفاً کد و عنوان را به درستی وارد نمایید.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onConfirm(gen.code, c, title.trim(), nature)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("ثبت حساب معین", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("انصراف")
                }
            }
        )
    }
}

/**
 * دیالوگ مشاهده جزئیات کامل و ریز آرتیکل‌های یک سند حسابداری
 */
@Composable
fun VoucherDetailsDialog(
    voucher: JournalEntryEntity,
    onDismiss: () -> Unit,
    onLoadItems: suspend (Long) -> List<JournalItemEntity>,
    onExportPdf: (JournalEntryEntity, List<JournalItemEntity>) -> Unit
) {
    var items by remember { mutableStateOf<List<JournalItemEntity>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(voucher.id) {
        isLoading = true
        items = onLoadItems(voucher.id)
        isLoading = false
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = CleanCard,
                border = BorderStroke(1.dp, CleanBorder),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(NavyPrimary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = "سند حسابداری شماره ${NumberFormatUtils.toPersianDigits(voucher.voucherNumber)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "تاریخ سند: ${NumberFormatUtils.toPersianDigits(voucher.jalaliDate)} • وضعیت: ${if (voucher.status == "VOID") "باطل شده" else "ثبت قطعی"}",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }

                    // شرح سند
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CleanBackground,
                        border = BorderStroke(1.dp, CleanBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "شرح سند: ${voucher.description}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDarkPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    HorizontalDivider(color = CleanBorder)

                    Text(text = "سطرهای آرتیکل سند (بدهکار / بستانکار):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        val currentItems = items ?: emptyList()
                        val totalDebit = currentItems.sumOf { it.debit }
                        val totalCredit = currentItems.sumOf { it.credit }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(currentItems) { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CleanSurface,
                                    border = BorderStroke(1.dp, CleanBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "${index + 1}.",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDarkSecondary
                                                )
                                                Text(
                                                    text = "کد معین: ${NumberFormatUtils.toPersianDigits(item.subsidiaryCode)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyPrimary
                                                )
                                            }
                                            Text(
                                                text = item.description,
                                                fontSize = 11.sp,
                                                color = TextDarkPrimary
                                            )
                                        }

                                        Row(modifier = Modifier.weight(1.2f), horizontalArrangement = Arrangement.End) {
                                            if (item.debit > 0) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("بدهکار", fontSize = 9.sp, color = AccountingDebitGreen, fontWeight = FontWeight.Bold)
                                                    Text(NumberFormatUtils.formatPrice(item.debit), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AccountingDebitGreen)
                                                }
                                            } else {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("بستانکار", fontSize = 9.sp, color = AccountingCreditRed, fontWeight = FontWeight.Bold)
                                                    Text(NumberFormatUtils.formatPrice(item.credit), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AccountingCreditRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // تراز نهایی سند
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CleanBackground,
                            border = BorderStroke(1.dp, CleanBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("مجموع بدهکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(NumberFormatUtils.formatPrice(totalDebit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingDebitGreen)
                                }
                                Column {
                                    Text("مجموع بستانکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(NumberFormatUtils.formatPrice(totalCredit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingCreditRed)
                                }
                                val isBalanced = totalDebit == totalCredit
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isBalanced) AccountingDebitGreen.copy(alpha = 0.12f) else AccountingCreditRed.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isBalanced) "تراز کامل ✓" else "ناهمخوان ✕",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBalanced) AccountingDebitGreen else AccountingCreditRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                items?.let { onExportPdf(voucher, it) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NavyPrimary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("چاپ سند PDF", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                        ) {
                            Text("بستن", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * دفتر معین حساب‌های کل و معین سیستم با نمایش ریز گردش، مانده تجمعی و تشخیص ماهیت
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsidiaryAccountLedgerContent(
    subsidiaries: List<SubsidiaryAccountEntity>,
    generals: List<GeneralAccountEntity>,
    selectedSub: SubsidiaryAccountEntity?,
    ledgerEntries: List<JournalItemEntity>,
    onSelectSubsidiary: (Int) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSubs = remember(subsidiaries, searchQuery) {
        if (searchQuery.isBlank()) subsidiaries
        else subsidiaries.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.code.toString().contains(searchQuery)
        }
    }

    val totalDebit = remember(ledgerEntries) { ledgerEntries.sumOf { it.debit } }
    val totalCredit = remember(ledgerEntries) { ledgerEntries.sumOf { it.credit } }
    val netBalance = totalDebit - totalCredit

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // ۱. انتخاب حساب معین و آمار گردش
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(NavyPrimary.copy(alpha = 0.08f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("دفتر معین حساب‌های مالی", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkPrimary)
                                Text("ریز گردش اسناد، مانده تجمعی و تشخیص بدهکار/بستانکار", fontSize = 11.sp, color = TextDarkSecondary)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onExportPdf,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onExportCsv,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, PersianGold)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = PersianGold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اکسل", fontSize = 11.sp, color = PersianGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // کادر کشویی انتخاب حساب معین
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSub?.let { "${NumberFormatUtils.toPersianDigits(it.code)} - ${it.title}" } ?: "انتخاب حساب معین...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("حساب معین مورد نظر") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = {
                                dropdownExpanded = false
                                searchQuery = ""
                            }
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("جستجوی کد یا عنوان معین...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            filteredSubs.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${NumberFormatUtils.toPersianDigits(s.code)} - ${s.title}") },
                                    onClick = {
                                        onSelectSubsidiary(s.code)
                                        dropdownExpanded = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }

                    // خلاصه گردش و مانده معین جاری
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CleanBackground,
                        border = BorderStroke(1.dp, CleanBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("گردش بدهکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text(NumberFormatUtils.formatPrice(totalDebit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingDebitGreen)
                            }
                            Column {
                                Text("گردش بستانکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text(NumberFormatUtils.formatPrice(totalCredit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingCreditRed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("مانده نهایی حساب:", fontSize = 10.sp, color = TextDarkSecondary)
                                val (mColor, mDiag) = when {
                                    netBalance > 0 -> AccountingDebitGreen to "بدهکار (بد)"
                                    netBalance < 0 -> AccountingCreditRed to "بستانکار (بس)"
                                    else -> TextDarkMuted to "تسویه"
                                }
                                Text(
                                    text = "${NumberFormatUtils.formatPrice(Math.abs(netBalance))} $mDiag",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = mColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // ۲. سطرهای دفتر معین
        if (ledgerEntries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    border = BorderStroke(1.dp, CleanBorder)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("گردشی برای این حساب معین ثبت نشده است.", fontSize = 13.sp, color = TextDarkSecondary)
                    }
                }
            }
        } else {
            var runningBalance = 0L
            itemsIndexed(ledgerEntries) { index, item ->
                runningBalance += (item.debit - item.credit)
                val currentRunning = runningBalance

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    border = BorderStroke(1.dp, CleanBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NavyPrimary.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = "ردیف ${NumberFormatUtils.toPersianDigits(index + 1)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "سند روزنامه شماره ${NumberFormatUtils.toPersianDigits(item.voucherId)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDarkPrimary
                                )
                            }

                            val diag = when {
                                currentRunning > 0 -> "بد"
                                currentRunning < 0 -> "بس"
                                else -> "تسویه"
                            }
                            val diagColor = when {
                                currentRunning > 0 -> AccountingDebitGreen
                                currentRunning < 0 -> AccountingCreditRed
                                else -> TextDarkMuted
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = diagColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "مانده: ${NumberFormatUtils.formatPrice(Math.abs(currentRunning))} ($diag)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = diagColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            color = TextDarkPrimary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CleanBackground, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (item.debit > 0) {
                                Text(
                                    text = "بدهکار: ${NumberFormatUtils.formatPrice(item.debit)} تومان",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccountingDebitGreen
                                )
                            } else {
                                Text("بدهکار: ۰", fontSize = 10.sp, color = TextDarkMuted)
                            }

                            if (item.credit > 0) {
                                Text(
                                    text = "بستانکار: ${NumberFormatUtils.formatPrice(item.credit)} تومان",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccountingCreditRed
                                )
                            } else {
                                Text("بستانکار: ۰", fontSize = 10.sp, color = TextDarkMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
