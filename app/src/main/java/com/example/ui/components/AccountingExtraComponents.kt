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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.mutableIntStateOf
import com.example.data.model.AccountGroupEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.JournalItemWithVoucher
import com.example.data.model.PartyEntity
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
import com.example.ui.viewmodel.VoucherItemDraft
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
 * دیالوگ ویرایش حساب کل (عنوان و ماهیت)
 */
@Composable
fun EditGeneralAccountDialog(
    account: GeneralAccountEntity,
    onDismiss: () -> Unit,
    onConfirm: (GeneralAccountEntity) -> Unit
) {
    var title by remember { mutableStateOf(account.title) }
    var nature by remember { mutableStateOf(account.nature) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "ویرایش حساب کل: ${NumberFormatUtils.toPersianDigits(account.code)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان حساب کل") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("ماهیت حساب:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = nature == "DEBIT",
                            onClick = { nature = "DEBIT" },
                            label = { Text("بدهکار", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccountingDebitGreen,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = nature == "CREDIT",
                            onClick = { nature = "CREDIT" },
                            label = { Text("بستانکار", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccountingCreditRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onConfirm(account.copy(title = title.trim(), nature = nature))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ذخیره تغییرات", fontWeight = FontWeight.Bold)
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
 * دیالوگ ویرایش حساب معین (عنوان، ماهیت و حساب کل والد)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSubsidiaryAccountDialog(
    account: SubsidiaryAccountEntity,
    generals: List<GeneralAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (SubsidiaryAccountEntity) -> Unit
) {
    var title by remember { mutableStateOf(account.title) }
    var nature by remember { mutableStateOf(account.nature) }
    var selectedGeneral by remember { mutableStateOf(generals.find { it.code == account.generalCode } ?: generals.firstOrNull()) }
    var genDropdownExpanded by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "ویرایش حساب معین: ${NumberFormatUtils.toPersianDigits(account.code)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = genDropdownExpanded,
                        onExpandedChange = { genDropdownExpanded = !genDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedGeneral?.let { "${NumberFormatUtils.toPersianDigits(it.code)} - ${it.title}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("حساب کل والد") },
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
                                    text = { Text("${NumberFormatUtils.toPersianDigits(g.code)} - ${g.title}") },
                                    onClick = {
                                        selectedGeneral = g
                                        genDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان حساب معین") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("ماهیت حساب:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = nature == "DEBIT",
                            onClick = { nature = "DEBIT" },
                            label = { Text("بدهکار", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccountingDebitGreen,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = nature == "CREDIT",
                            onClick = { nature = "CREDIT" },
                            label = { Text("بستانکار", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccountingCreditRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val genCode = selectedGeneral?.code ?: account.generalCode
                        if (title.isNotBlank()) {
                            onConfirm(account.copy(generalCode = genCode, title = title.trim(), nature = nature))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ذخیره تغییرات", fontWeight = FontWeight.Bold)
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
 * دیالوگ جامع ویرایش کامل سند حسابداری به همراه سطرهای آرتیکل، تغییر کدینگ، اشخاص و تراز دوبل
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFullVoucherDialog(
    voucher: JournalEntryEntity,
    subsidiaries: List<SubsidiaryAccountEntity>,
    parties: List<PartyEntity>,
    onDismiss: () -> Unit,
    onLoadItems: suspend (Long) -> List<JournalItemEntity>,
    onSave: (newDesc: String, newDate: String, items: List<VoucherItemDraft>) -> Unit
) {
    val context = LocalContext.current
    var desc by remember { mutableStateOf(voucher.description) }
    var date by remember { mutableStateOf(voucher.jalaliDate) }
    var items by remember { mutableStateOf<List<VoucherItemDraft>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // فیلدهای ورود سطر جدید
    var selectedSub by remember { mutableStateOf<SubsidiaryAccountEntity?>(null) }
    var subDropdownExpanded by remember { mutableStateOf(false) }
    var subSearchQuery by remember { mutableStateOf("") }

    var selectedParty by remember { mutableStateOf<PartyEntity?>(null) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }

    var lineDesc by remember { mutableStateOf("") }
    var lineDebitText by remember { mutableStateOf("") }
    var lineCreditText by remember { mutableStateOf("") }

    val subMap = remember(subsidiaries) { subsidiaries.associateBy { it.code } }
    val partyMap = remember(parties) { parties.associateBy { it.id } }

    LaunchedEffect(voucher.id) {
        isLoading = true
        val loaded = onLoadItems(voucher.id)
        items = loaded.map { itm ->
            val sub = subMap[itm.subsidiaryCode]
            val p = itm.partyId?.let { partyMap[it] }
            VoucherItemDraft(
                subsidiaryCode = itm.subsidiaryCode,
                subsidiaryTitle = sub?.title ?: "معین ${itm.subsidiaryCode}",
                generalCode = itm.generalCode,
                partyId = itm.partyId,
                partyName = p?.name,
                description = itm.description,
                debit = itm.debit,
                credit = itm.credit
            )
        }
        isLoading = false
    }

    val totalDebit = items.sumOf { it.debit }
    val totalCredit = items.sumOf { it.credit }
    val diff = Math.abs(totalDebit - totalCredit)
    val isBalanced = totalDebit == totalCredit && items.size >= 2

    val filteredSubs = remember(subsidiaries, subSearchQuery) {
        if (subSearchQuery.isBlank()) subsidiaries
        else subsidiaries.filter { it.title.contains(subSearchQuery, ignoreCase = true) || it.code.toString().contains(subSearchQuery) }
    }

    val filteredParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties
        else parties.filter { it.name.contains(partySearchQuery, ignoreCase = true) || it.mobile.contains(partySearchQuery) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = CleanCard,
                border = BorderStroke(1.dp, CleanBorder),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .size(36.dp)
                                    .background(NavyPrimary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "ویرایش سند مالی شماره ${NumberFormatUtils.toPersianDigits(voucher.voucherNumber)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "تغییر شرح، تاریخ، سرفصل‌ها و آرتیکل‌ها با هماهنگی مانده‌ها",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }

                    HorizontalDivider(color = CleanBorder)

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NavyPrimary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // فیلدهای شرح و تاریخ
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = desc,
                                        onValueChange = { desc = it },
                                        label = { Text("شرح سند حسابداری") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(2f)
                                    )
                                    OutlinedTextField(
                                        value = date,
                                        onValueChange = { date = it },
                                        label = { Text("تاریخ (هجری شمسی)") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // سطرهای آرتیکل جاری
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "سطرهای آرتیکل سند (${NumberFormatUtils.toPersianDigits(items.size)} سطر):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextDarkPrimary
                                    )
                                    if (diff > 0L) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AccountingCreditRed.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = "اختلاف تراز: ${NumberFormatUtils.formatPrice(diff)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccountingCreditRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(items) { index, itm ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CleanSurface,
                                    border = BorderStroke(1.dp, CleanBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("${index + 1}.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSecondary)
                                                Text(
                                                    text = "${NumberFormatUtils.toPersianDigits(itm.subsidiaryCode)} - ${itm.subsidiaryTitle}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyPrimary
                                                )
                                                if (!itm.partyName.isNullOrBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MatteGold.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = itm.partyName,
                                                            fontSize = 10.sp,
                                                            color = NavyPrimary,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = itm.description.ifBlank { desc },
                                                fontSize = 11.sp,
                                                color = TextDarkSecondary
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (itm.debit > 0) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("بدهکار", fontSize = 9.sp, color = AccountingDebitGreen, fontWeight = FontWeight.Bold)
                                                    Text(NumberFormatUtils.formatPrice(itm.debit), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccountingDebitGreen)
                                                }
                                            } else {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("بستانکار", fontSize = 9.sp, color = AccountingCreditRed, fontWeight = FontWeight.Bold)
                                                    Text(NumberFormatUtils.formatPrice(itm.credit), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccountingCreditRed)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    items = items.toMutableList().also { it.removeAt(index) }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف سطر", tint = AccountingCreditRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // افزودن سطر جدید
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CleanBackground,
                                    border = BorderStroke(1.dp, CleanBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("افزودن سطر آرتیکل جدید به سند:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)

                                        // حساب معین
                                        ExposedDropdownMenuBox(
                                            expanded = subDropdownExpanded,
                                            onExpandedChange = { subDropdownExpanded = !subDropdownExpanded }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedSub?.let { "${NumberFormatUtils.toPersianDigits(it.code)} - ${it.title}" } ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("حساب معین *", fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subDropdownExpanded) },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().menuAnchor()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = subDropdownExpanded,
                                                onDismissRequest = { subDropdownExpanded = false; subSearchQuery = "" }
                                            ) {
                                                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                    OutlinedTextField(
                                                        value = subSearchQuery,
                                                        onValueChange = { subSearchQuery = it },
                                                        placeholder = { Text("جستجوی معین...", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                filteredSubs.forEach { s ->
                                                    DropdownMenuItem(
                                                        text = { Text("${NumberFormatUtils.toPersianDigits(s.code)} - ${s.title}", fontSize = 11.sp) },
                                                        onClick = {
                                                            selectedSub = s
                                                            subDropdownExpanded = false
                                                            subSearchQuery = ""
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // طرف‌حساب (اختیاری)
                                        ExposedDropdownMenuBox(
                                            expanded = partyDropdownExpanded,
                                            onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedParty?.let { "${it.name} (${it.phone})" } ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("طرف‌حساب (تفصیلی اختیاری)", fontSize = 11.sp) },
                                                trailingIcon = {
                                                    if (selectedParty != null) {
                                                        IconButton(onClick = { selectedParty = null }) {
                                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        }
                                                    } else {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded)
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().menuAnchor()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = partyDropdownExpanded,
                                                onDismissRequest = { partyDropdownExpanded = false; partySearchQuery = "" }
                                            ) {
                                                filteredParties.forEach { p ->
                                                    DropdownMenuItem(
                                                        text = { Text("${p.name} - ${p.phone}", fontSize = 11.sp) },
                                                        onClick = {
                                                            selectedParty = p
                                                            partyDropdownExpanded = false
                                                            partySearchQuery = ""
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = lineDesc,
                                            onValueChange = { lineDesc = it },
                                            label = { Text("شرح آرتیکل", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = lineDebitText,
                                                onValueChange = {
                                                    lineDebitText = it
                                                    if (it.isNotBlank()) lineCreditText = ""
                                                },
                                                label = { Text("بدهکار", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = lineCreditText,
                                                onValueChange = {
                                                    lineCreditText = it
                                                    if (it.isNotBlank()) lineDebitText = ""
                                                },
                                                label = { Text("بستانکار", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (diff > 0L) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (totalDebit > totalCredit) {
                                                            lineCreditText = diff.toString()
                                                            lineDebitText = ""
                                                        } else {
                                                            lineDebitText = diff.toString()
                                                            lineCreditText = ""
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f).height(36.dp)
                                                ) {
                                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MatteGold, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("درج موازنه (${NumberFormatUtils.formatPrice(diff)})", fontSize = 10.sp, color = NavyPrimary)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val sub = selectedSub ?: return@Button
                                                    val debit = lineDebitText.toLongOrNull() ?: 0L
                                                    val credit = lineCreditText.toLongOrNull() ?: 0L
                                                    if (debit <= 0L && credit <= 0L) return@Button

                                                    items = items + VoucherItemDraft(
                                                        subsidiaryCode = sub.code,
                                                        subsidiaryTitle = sub.title,
                                                        generalCode = sub.generalCode,
                                                        partyId = selectedParty?.id,
                                                        partyName = selectedParty?.name,
                                                        description = lineDesc.ifBlank { desc },
                                                        debit = debit,
                                                        credit = credit
                                                    )

                                                    selectedSub = null
                                                    selectedParty = null
                                                    lineDesc = ""
                                                    lineDebitText = ""
                                                    lineCreditText = ""
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                                modifier = Modifier.weight(1f).height(36.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("افزودن سطر", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // جمع کل و نوار تراز
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
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isBalanced) AccountingDebitGreen.copy(alpha = 0.12f) else AccountingCreditRed.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isBalanced) "تراز دوبل کامل ✓" else "ناهمخوان (${NumberFormatUtils.formatPrice(diff)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBalanced) AccountingDebitGreen else AccountingCreditRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // دکمه‌های تایید و انصراف
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("انصراف")
                            }

                            Button(
                                onClick = {
                                    if (isBalanced) {
                                        onSave(desc, date, items)
                                    } else {
                                        Toast.makeText(context, "سند تراز نیست! ابتدا سند را متوازن کنید.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = isBalanced,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBalanced) NavyPrimary else TextDarkMuted,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(2f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ذخیره تغییرات و اعمال وابستگی‌ها", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * دفتر معین حساب‌های کل و معین سیستم با نمایش ریز گردش، مانده تجمعی، تاریخ شمسی، شماره سند و تشخیص ماهیت
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsidiaryAccountLedgerContent(
    subsidiaries: List<SubsidiaryAccountEntity>,
    generals: List<GeneralAccountEntity>,
    selectedSub: SubsidiaryAccountEntity?,
    ledgerEntries: List<JournalItemEntity>,
    ledgerWithVouchers: List<JournalItemWithVoucher> = emptyList(),
    onSelectSubsidiary: (Int) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onViewVoucher: ((Long) -> Unit)? = null
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterText by remember { mutableStateOf("") }

    val filteredSubs = remember(subsidiaries, searchQuery) {
        if (searchQuery.isBlank()) subsidiaries
        else subsidiaries.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.code.toString().contains(searchQuery)
        }
    }

    // استفاده ترجیحی از سطرهای معتبر دفتر به همراه سند متصل
    val useVoucherRows = ledgerWithVouchers.isNotEmpty()
    val activeVoucherRows = remember(ledgerWithVouchers) {
        ledgerWithVouchers.filter { it.voucherStatus != "VOID" }
    }

    val totalDebit = remember(useVoucherRows, activeVoucherRows, ledgerEntries) {
        if (useVoucherRows) activeVoucherRows.sumOf { it.debit } else ledgerEntries.sumOf { it.debit }
    }
    val totalCredit = remember(useVoucherRows, activeVoucherRows, ledgerEntries) {
        if (useVoucherRows) activeVoucherRows.sumOf { it.credit } else ledgerEntries.sumOf { it.credit }
    }
    val netBalance = totalDebit - totalCredit

    val displayedVoucherRows = remember(activeVoucherRows, filterText) {
        if (filterText.isBlank()) activeVoucherRows
        else activeVoucherRows.filter {
            it.description.contains(filterText, ignoreCase = true) ||
            it.voucherNumber.toString().contains(filterText) ||
            it.jalaliDate.contains(filterText)
        }
    }

    val displayedBasicRows = remember(ledgerEntries, filterText) {
        if (filterText.isBlank()) ledgerEntries
        else ledgerEntries.filter {
            it.description.contains(filterText, ignoreCase = true) ||
            it.voucherId.toString().contains(filterText)
        }
    }

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

                    // جستجو در سطرهای معین
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("فیلتر در شرح، تاریخ یا شماره سند گردش حساب...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

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
        if (useVoucherRows) {
            if (displayedVoucherRows.isEmpty()) {
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
                itemsIndexed(displayedVoucherRows) { index, item ->
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
                                        text = "سند شماره ${NumberFormatUtils.toPersianDigits(item.voucherNumber)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                    Text(
                                        text = "مورخ ${NumberFormatUtils.toPersianDigits(item.jalaliDate)}",
                                        fontSize = 10.sp,
                                        color = TextDarkSecondary
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
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
        } else {
            if (displayedBasicRows.isEmpty()) {
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
                itemsIndexed(displayedBasicRows) { index, item ->
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
                                        text = "سند شماره ${NumberFormatUtils.toPersianDigits(item.voucherId)}",
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
}
