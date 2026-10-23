package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AccountGroupEntity
import com.example.data.model.AppSecurityAndSettingsManager
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.GeneralTrialBalanceRow
import com.example.data.model.JournalEntryEntity
import com.example.data.model.OfficialJournalRow
import com.example.data.model.PartyEntity
import com.example.data.model.SixColumnTrialBalanceRow
import com.example.data.model.SubsidiaryAccountEntity
import com.example.data.model.TrialBalanceRow
import com.example.ui.viewmodel.ProfitLossStatement
import com.example.ui.viewmodel.VoucherItemDraft
import com.example.ui.components.ErpTopAppBar
import com.example.ui.components.PartySubsidiaryLedgerContent
import com.example.ui.theme.AccountingCreditRed
import com.example.ui.theme.AccountingDebitGreen
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanBorder
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.MatteGold
import com.example.ui.theme.MatteGoldContainer
import com.example.ui.theme.MatteGoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySurface
import com.example.ui.theme.PersianGold
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.viewmodel.AccountingViewModel
import com.example.util.ExportHelper
import com.example.util.NumberFormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(
    viewModel: AccountingViewModel
) {
    val context = LocalContext.current
    val securityManager = remember { AppSecurityAndSettingsManager.getInstance(context) }
    val currentRole by securityManager.currentRole.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        "سرفصل‌های حسابداری",
        "اسناد و روزنامه",
        "دفتر معین حساب‌ها",
        "ثبت سند دوبل",
        "تراز آزمایشی",
        "صورت سود و زیان"
    )

    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val generals by viewModel.allGenerals.collectAsStateWithLifecycle()
    val subsidiaries by viewModel.allSubsidiaries.collectAsStateWithLifecycle()
    val journalEntries by viewModel.allJournalEntries.collectAsStateWithLifecycle()
    val parties by viewModel.allParties.collectAsStateWithLifecycle()
    val invoices by viewModel.allInvoices.collectAsStateWithLifecycle()
    val selectedParty by viewModel.selectedPartyForLedger.collectAsStateWithLifecycle()
    val partyLedgerEntries by viewModel.partyLedgerEntries.collectAsStateWithLifecycle()
    val selectedSubsidiary by viewModel.selectedSubsidiaryForLedger.collectAsStateWithLifecycle()
    val subsidiaryLedgerEntries by viewModel.subsidiaryLedgerEntries.collectAsStateWithLifecycle()
    val subsidiaryLedgerWithVouchers by viewModel.subsidiaryLedgerWithVouchers.collectAsStateWithLifecycle()
    val trialBalance by viewModel.trialBalance.collectAsStateWithLifecycle()
    val generalTrialBalance by viewModel.generalTrialBalance.collectAsStateWithLifecycle()
    val profitLoss by viewModel.profitLoss.collectAsStateWithLifecycle()

    val officialJournalRows by viewModel.officialJournalRows.collectAsStateWithLifecycle()
    val sixColumnTrialBalance by viewModel.sixColumnTrialBalance.collectAsStateWithLifecycle()
    val draftVoucherType by viewModel.draftVoucherType.collectAsStateWithLifecycle()

    val draftDesc by viewModel.draftVoucherDescription.collectAsStateWithLifecycle()
    val draftDate by viewModel.draftVoucherDate.collectAsStateWithLifecycle()
    val draftItems by viewModel.draftItems.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()

    var ledgerSubTab by remember { mutableStateOf("SUBSIDIARY") } // "SUBSIDIARY" or "PARTY"
    var showAddGeneralDialog by remember { mutableStateOf(false) }
    var showAddSubsidiaryDialog by remember { mutableStateOf(false) }
    var editingGeneralAccount by remember { mutableStateOf<GeneralAccountEntity?>(null) }
    var deletingGeneralAccount by remember { mutableStateOf<GeneralAccountEntity?>(null) }
    var editingSubsidiaryAccount by remember { mutableStateOf<SubsidiaryAccountEntity?>(null) }
    var deletingSubsidiaryAccount by remember { mutableStateOf<SubsidiaryAccountEntity?>(null) }
    var viewingVoucherDetails by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var fullEditingVoucher by remember { mutableStateOf<JournalEntryEntity?>(null) }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(NavyPrimary)) {
                    ErpTopAppBar(
                        title = "حسابداری مالی و اسناد",
                        subtitle = "کدینگ حساب‌ها، اسناد دوبل، دفتر معین، تراز آزمایشی و صورت‌های مالی"
                    )

                    // تب‌های اسکرول‌پذیر با دیزاین سرمه‌ای و طلایی
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = NavyPrimary,
                        contentColor = MatteGold,
                        edgePadding = 12.dp,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MatteGold,
                                    height = 3.dp
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MatteGold else Color.White.copy(alpha = 0.7f)
                                    )
                                },
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("accounting_tab_$index")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CleanBackground)
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> AccountTreeContent(
                        groups = groups,
                        generals = generals,
                        subsidiaries = subsidiaries,
                        trialBalance = trialBalance,
                        onAddGeneral = { showAddGeneralDialog = true },
                        onAddSubsidiary = { showAddSubsidiaryDialog = true },
                        onEditGeneral = { gen -> editingGeneralAccount = gen },
                        onDeleteGeneral = { gen -> deletingGeneralAccount = gen },
                        onEditSubsidiary = { sub -> editingSubsidiaryAccount = sub },
                        onDeleteSubsidiary = { sub -> deletingSubsidiaryAccount = sub },
                        onOpenLedger = { code ->
                            viewModel.selectSubsidiaryForLedger(code)
                            ledgerSubTab = "SUBSIDIARY"
                            selectedTab = 2
                        }
                    )
                    1 -> JournalEntriesContent(
                        entries = journalEntries,
                        officialJournalRows = officialJournalRows,
                        onExportPdf = {
                            val headers = listOf("شماره سند", "تاریخ سند", "شرح سند", "وضعیت")
                            val rows = journalEntries.map {
                                listOf(it.voucherNumber.toString(), it.jalaliDate, it.description, if (it.isBalanced) "تراز" else "ناهمخوان")
                            }
                            val f = ExportHelper.exportReportPdf(context, "دفتر روزنامه و اسناد حسابداری", headers, rows)
                            if (f != null) ExportHelper.shareFile(context, f, "application/pdf", "اسناد حسابداری PDF")
                        },
                        onExportCsv = {
                            val headers = listOf("شماره سند", "تاریخ", "شرح", "وضعیت")
                            val rows = journalEntries.map {
                                listOf(it.voucherNumber.toString(), it.jalaliDate, it.description, it.status)
                            }
                            val f = ExportHelper.exportReportCsv(context, "فهرست اسناد روزنامه", headers, rows)
                            if (f != null) ExportHelper.shareFile(context, f, "text/csv", "خروجی اکسل اسناد")
                        },
                        onExportOfficialJournalPdf = { viewModel.exportOfficialJournalPdf(context) },
                        onExportOfficialJournalCsv = { viewModel.exportOfficialJournalCsv(context) },
                        onVoidVoucher = { entry ->
                            viewModel.voidVoucher(entry) {}
                        },
                        onApproveVoucher = { entry ->
                            viewModel.approveVoucher(entry) {}
                        },
                        onUpdateVoucher = { entry, desc, date ->
                            viewModel.updateVoucher(entry, desc, date) {}
                        },
                        onFullEditVoucher = { entry ->
                            fullEditingVoucher = entry
                        },
                        onDeleteVoucher = { voucherId ->
                            viewModel.deleteVoucher(voucherId) {}
                        },
                        onViewDetails = { entry ->
                            viewingVoucherDetails = entry
                        }
                    )
                    2 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = CleanSurface,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = ledgerSubTab == "SUBSIDIARY",
                                        onClick = { ledgerSubTab = "SUBSIDIARY" },
                                        label = { Text("معین سرفصل‌های سیستم (کل و معین)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NavyPrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f).testTag("tab_sub_ledger")
                                    )
                                    FilterChip(
                                        selected = ledgerSubTab == "PARTY",
                                        onClick = { ledgerSubTab = "PARTY" },
                                        label = { Text("معین تفصیلی اشخاص و مشتریان", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NavyPrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f).testTag("tab_party_ledger")
                                    )
                                }
                            }

                            if (ledgerSubTab == "SUBSIDIARY") {
                                com.example.ui.components.SubsidiaryAccountLedgerContent(
                                    subsidiaries = subsidiaries,
                                    generals = generals,
                                    selectedSub = selectedSubsidiary,
                                    ledgerEntries = subsidiaryLedgerEntries,
                                    ledgerWithVouchers = subsidiaryLedgerWithVouchers,
                                    onSelectSubsidiary = { code -> viewModel.selectSubsidiaryForLedger(code) },
                                    onExportPdf = {
                                        selectedSubsidiary?.let { sub ->
                                            viewModel.exportSubsidiaryLedgerPdf(context, sub, subsidiaryLedgerEntries)
                                        }
                                    },
                                    onExportCsv = {
                                        selectedSubsidiary?.let { sub ->
                                            viewModel.exportSubsidiaryLedgerCsv(context, sub, subsidiaryLedgerEntries)
                                        }
                                    },
                                    onViewVoucher = { vId ->
                                        viewingVoucherDetails = journalEntries.find { it.id == vId }
                                    }
                                )
                            } else {
                                PartySubsidiaryLedgerContent(
                                    parties = parties,
                                    selectedParty = selectedParty,
                                    ledgerEntries = partyLedgerEntries,
                                    onSelectParty = { viewModel.selectPartyForLedger(it.id) },
                                    onAddTransaction = { pId, desc, d, c, dt ->
                                        viewModel.addPartyTransaction(pId, desc, d, c, dt) {}
                                    },
                                    onUpdateTransaction = { itm, desc, d, c ->
                                        viewModel.updatePartyTransaction(itm, desc, d, c) {}
                                    },
                                    onDeleteTransaction = { itm ->
                                        viewModel.deletePartyTransaction(itm) {}
                                    },
                                    onExportPdf = {
                                        if (selectedParty != null) {
                                            viewModel.exportPartyLedgerPdf(context, selectedParty!!, partyLedgerEntries)
                                        }
                                    },
                                    onExportCsv = {
                                        if (selectedParty != null) {
                                            viewModel.exportPartyLedgerCsv(context, selectedParty!!, partyLedgerEntries)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    3 -> CreateJournalVoucherContent(
                        subsidiaries = subsidiaries,
                        generals = generals,
                        parties = parties,
                        draftDesc = draftDesc,
                        draftDate = draftDate,
                        draftType = draftVoucherType,
                        draftItems = draftItems,
                        onDescChange = { viewModel.updateDraftDescription(it) },
                        onDateChange = { viewModel.updateDraftDate(it) },
                        onTypeChange = { viewModel.updateDraftType(it) },
                        onApplyPreset = { viewModel.applyPreset(it) },
                        onAutoBalance = { viewModel.autoBalanceDraft(it) },
                        onAddItem = { viewModel.addDraftItem(it) },
                        onRemoveItem = { viewModel.removeDraftItem(it) },
                        onSubmit = {
                            viewModel.submitJournalVoucher {
                                selectedTab = 1
                            }
                        }
                    )
                    4 -> TrialBalanceContent(
                        trialBalance = trialBalance,
                        generalTrialBalance = generalTrialBalance,
                        sixColumnTrialBalance = sixColumnTrialBalance,
                        onExportPdf = { viewModel.exportTrialBalancePdf(context) },
                        onExportCsv = { viewModel.exportTrialBalanceCsv(context) },
                        onExportGeneralPdf = { viewModel.exportGeneralTrialBalancePdf(context) },
                        onExportGeneralCsv = { viewModel.exportGeneralTrialBalanceCsv(context) },
                        onExportSixColumnPdf = { viewModel.exportSixColumnTrialBalancePdf(context) },
                        onExportSixColumnCsv = { viewModel.exportSixColumnTrialBalanceCsv(context) }
                    )
                    5 -> ProfitLossContent(
                        statement = profitLoss,
                        canViewProfit = currentRole.canViewProfit,
                        onExportPdf = { viewModel.exportProfitLossPdf(context) },
                        onExportCsv = { viewModel.exportProfitLossCsv(context) }
                    )
                }
            }
        }

        if (showAddGeneralDialog) {
            com.example.ui.components.AddGeneralAccountDialog(
                groups = groups,
                generals = generals,
                onDismiss = { showAddGeneralDialog = false },
                onConfirm = { grp, code, title, nature ->
                    viewModel.addGeneralAccount(grp, code, title, nature) {
                        showAddGeneralDialog = false
                    }
                }
            )
        }

        if (showAddSubsidiaryDialog) {
            com.example.ui.components.AddSubsidiaryAccountDialog(
                generals = generals,
                subsidiaries = subsidiaries,
                onDismiss = { showAddSubsidiaryDialog = false },
                onConfirm = { gen, code, title, nature ->
                    viewModel.addSubsidiaryAccount(gen, code, title, nature) {
                        showAddSubsidiaryDialog = false
                    }
                }
            )
        }

        viewingVoucherDetails?.let { voucher ->
            com.example.ui.components.VoucherDetailsDialog(
                voucher = voucher,
                onDismiss = { viewingVoucherDetails = null },
                onLoadItems = { vId -> viewModel.getVoucherItems(vId) },
                onExportPdf = { v, items ->
                    viewModel.exportSingleVoucherPdf(context, v, items)
                }
            )
        }

        fullEditingVoucher?.let { voucher ->
            com.example.ui.components.EditFullVoucherDialog(
                voucher = voucher,
                subsidiaries = subsidiaries,
                parties = parties,
                onDismiss = { fullEditingVoucher = null },
                onLoadItems = { vId -> viewModel.getVoucherItems(vId) },
                onSave = { newDesc, newDate, items ->
                    viewModel.updateFullJournalVoucher(voucher, newDesc, newDate, items) {
                        fullEditingVoucher = null
                    }
                }
            )
        }

        editingGeneralAccount?.let { gen ->
            com.example.ui.components.EditGeneralAccountDialog(
                account = gen,
                onDismiss = { editingGeneralAccount = null },
                onConfirm = { updated ->
                    viewModel.updateGeneralAccount(updated) {
                        editingGeneralAccount = null
                    }
                }
            )
        }

        deletingGeneralAccount?.let { gen ->
            AlertDialog(
                onDismissRequest = { deletingGeneralAccount = null },
                title = { Text("حذف حساب کل", fontWeight = FontWeight.Bold, color = AccountingCreditRed) },
                text = { Text("آیا از حذف حساب کل «${gen.title}» (کد ${NumberFormatUtils.toPersianDigits(gen.code)}) اطمینان دارید؟ توجه: اگر حساب دارای حساب‌های معین یا گردش باشد، حذف نخواهد شد.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteGeneralAccount(gen) {
                                deletingGeneralAccount = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed)
                    ) {
                        Text("حذف قطعی", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingGeneralAccount = null }) {
                        Text("انصراف")
                    }
                }
            )
        }

        editingSubsidiaryAccount?.let { sub ->
            com.example.ui.components.EditSubsidiaryAccountDialog(
                account = sub,
                generals = generals,
                onDismiss = { editingSubsidiaryAccount = null },
                onConfirm = { updated ->
                    viewModel.updateSubsidiaryAccount(updated) {
                        editingSubsidiaryAccount = null
                    }
                }
            )
        }

        deletingSubsidiaryAccount?.let { sub ->
            AlertDialog(
                onDismissRequest = { deletingSubsidiaryAccount = null },
                title = { Text("حذف حساب معین", fontWeight = FontWeight.Bold, color = AccountingCreditRed) },
                text = { Text("آیا از حذف حساب معین «${sub.title}» (کد ${NumberFormatUtils.toPersianDigits(sub.code)}) اطمینان دارید؟ در صورت وجود گردش مالی یا آرتیکل ثبت شده، امکان حذف وجود نخواهد داشت.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSubsidiaryAccount(sub) {
                                deletingSubsidiaryAccount = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed)
                    ) {
                        Text("حذف قطعی", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingSubsidiaryAccount = null }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

// ============================================================================
// تب ۰: سرفصل‌های حسابداری (درخت حساب‌ها / کدینگ استاندارد)
// ============================================================================
@Composable
private fun AccountTreeContent(
    groups: List<AccountGroupEntity>,
    generals: List<GeneralAccountEntity>,
    subsidiaries: List<SubsidiaryAccountEntity>,
    trialBalance: List<TrialBalanceRow>,
    onAddGeneral: () -> Unit,
    onAddSubsidiary: () -> Unit,
    onEditGeneral: (GeneralAccountEntity) -> Unit = {},
    onDeleteGeneral: (GeneralAccountEntity) -> Unit = {},
    onEditSubsidiary: (SubsidiaryAccountEntity) -> Unit = {},
    onDeleteSubsidiary: (SubsidiaryAccountEntity) -> Unit = {},
    onOpenLedger: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandAll by remember { mutableStateOf(true) }

    val tbMap = remember(trialBalance) { trialBalance.associateBy { it.subsidiaryCode } }

    val filteredGroups = remember(groups, generals, subsidiaries, searchQuery) {
        if (searchQuery.isBlank()) groups
        else {
            val q = searchQuery.trim()
            groups.filter { g ->
                g.title.contains(q, ignoreCase = true) ||
                g.code.toString().contains(q) ||
                generals.any { gen -> gen.groupCode == g.code && (gen.title.contains(q, ignoreCase = true) || gen.code.toString().contains(q)) } ||
                subsidiaries.any { sub -> sub.code.toString().contains(q) || sub.title.contains(q, ignoreCase = true) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // نوار ابزار سرفصل‌ها + دکمه‌های تعریف حساب جدید
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
                                Icon(Icons.Default.Folder, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("ساختار کدینگ و سرفصل‌های حسابداری", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkPrimary)
                                Text("مدیریت درخت حساب‌ها و مشاهده مانده لحظه‌ای", fontSize = 11.sp, color = TextDarkSecondary)
                            }
                        }

                        TextButton(onClick = { expandAll = !expandAll }) {
                            Text(
                                text = if (expandAll) "بستن همه" else "باز کردن همه",
                                fontSize = 11.sp,
                                color = MatteGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // دکمه‌های تعریف حساب کل و معین جدید
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddGeneral,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("add_general_account_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MatteGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حساب کل جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onAddSubsidiary,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("add_subsidiary_account_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MatteGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حساب معین جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو در عنوان یا کد حساب (گروه، کل، معین)...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface,
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    // ردیف آمار سرفصل‌ها
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CleanBackground, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "گروه‌ها: ${NumberFormatUtils.toPersianDigits(groups.size)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "حساب‌های کل: ${NumberFormatUtils.toPersianDigits(generals.size)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "حساب‌های معین: ${NumberFormatUtils.toPersianDigits(subsidiaries.size)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MatteGold
                        )
                    }
                }
            }
        }

        // کارت‌های گروه‌های حسابداری
        items(filteredGroups) { group ->
            var expanded by remember(expandAll) { mutableStateOf(expandAll) }
            val groupGenerals = generals.filter { it.groupCode == group.code }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // سربرگ گروه حساب
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NavyPrimary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "گروه ${NumberFormatUtils.toPersianDigits(group.code)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkPrimary
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isDebit = group.nature == "DEBIT"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDebit) AccountingDebitGreen.copy(alpha = 0.1f) else AccountingCreditRed.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = if (isDebit) "ماهیت بدهکار" else "ماهیت بستانکار",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDebit) AccountingDebitGreen else AccountingCreditRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextDarkSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (groupGenerals.isEmpty()) {
                                Text(
                                    text = "حساب کلی در این گروه تعریف نشده است.",
                                    fontSize = 11.sp,
                                    color = TextDarkMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            groupGenerals.forEach { gen ->
                                val genSubs = subsidiaries.filter { it.generalCode == gen.code }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = CleanBackground),
                                    border = BorderStroke(1.dp, CleanBorder)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // حساب کل
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MatteGold.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "کل: ${NumberFormatUtils.toPersianDigits(gen.code)}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MatteGold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = gen.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDarkPrimary
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "${NumberFormatUtils.toPersianDigits(genSubs.size)} معین",
                                                    fontSize = 10.sp,
                                                    color = TextDarkSecondary
                                                )
                                                IconButton(
                                                    onClick = { onEditGeneral(gen) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش حساب کل", tint = NavyPrimary, modifier = Modifier.size(13.dp))
                                                }
                                                IconButton(
                                                    onClick = { onDeleteGeneral(gen) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف حساب کل", tint = AccountingCreditRed, modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }

                                        // حساب‌های معین زیرمجموعه
                                        if (genSubs.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                genSubs.forEach { sub ->
                                                    val tb = tbMap[sub.code]
                                                    Card(
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                                                        border = BorderStroke(1.dp, CleanBorder),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Text(
                                                                        text = "↳ ${NumberFormatUtils.toPersianDigits(sub.code)}",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = NavyPrimary
                                                                    )
                                                                    Text(
                                                                        text = sub.title,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        color = TextDarkPrimary
                                                                    )
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(
                                                                        text = if (sub.nature == "DEBIT") "بدهکار" else "بستانکار",
                                                                        fontSize = 9.sp,
                                                                        color = if (sub.nature == "DEBIT") AccountingDebitGreen else AccountingCreditRed,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    OutlinedButton(
                                                                        onClick = { onOpenLedger(sub.code) },
                                                                        shape = RoundedCornerShape(6.dp),
                                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(26.dp)
                                                                    ) {
                                                                        Text("دفتر معین", fontSize = 9.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                                                                    }
                                                                    IconButton(
                                                                        onClick = { onEditSubsidiary(sub) },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Edit, contentDescription = "ویرایش معین", tint = NavyPrimary, modifier = Modifier.size(13.dp))
                                                                    }
                                                                    IconButton(
                                                                        onClick = { onDeleteSubsidiary(sub) },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Delete, contentDescription = "حذف معین", tint = AccountingCreditRed, modifier = Modifier.size(13.dp))
                                                                    }
                                                                }
                                                            }

                                                            // نمایش گردش و مانده در صورت وجود
                                                            if (tb != null && (tb.totalDebit > 0 || tb.totalCredit > 0)) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .background(CleanBackground, RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Text(
                                                                        text = "گردش: بد: ${NumberFormatUtils.formatPrice(tb.totalDebit)} | بس: ${NumberFormatUtils.formatPrice(tb.totalCredit)}",
                                                                        fontSize = 9.sp,
                                                                        color = TextDarkSecondary
                                                                    )
                                                                    val diag = if (tb.balanceDebit > 0) "بد" else "بس"
                                                                    val balAmt = if (tb.balanceDebit > 0) tb.balanceDebit else tb.balanceCredit
                                                                    val bColor = if (tb.balanceDebit > 0) AccountingDebitGreen else AccountingCreditRed
                                                                    Text(
                                                                        text = "مانده: ${NumberFormatUtils.formatPrice(balAmt)} ($diag)",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = bColor
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// تب ۱: اسناد حسابداری (دفتر روزنامه و اسناد مالی)
// ============================================================================
@Composable
private fun JournalEntriesContent(
    entries: List<JournalEntryEntity>,
    officialJournalRows: List<OfficialJournalRow>,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportOfficialJournalPdf: () -> Unit,
    onExportOfficialJournalCsv: () -> Unit,
    onVoidVoucher: (JournalEntryEntity) -> Unit,
    onApproveVoucher: (JournalEntryEntity) -> Unit,
    onUpdateVoucher: (JournalEntryEntity, String, String) -> Unit,
    onFullEditVoucher: (JournalEntryEntity) -> Unit = {},
    onDeleteVoucher: (Long) -> Unit,
    onViewDetails: (JournalEntryEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BALANCED, UNBALANCED, VOID, DRAFT, FINAL
    var viewFormat by remember { mutableStateOf("VOUCHERS") } // VOUCHERS or OFFICIAL_JOURNAL

    var editingVoucher by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var voidingVoucher by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var deletingVoucherId by remember { mutableStateOf<Long?>(null) }

    val filteredEntries = remember(entries, searchQuery, selectedFilter) {
        entries.filter { entry ->
            val matchesSearch = searchQuery.isBlank() ||
                entry.voucherNumber.toString().contains(searchQuery) ||
                entry.description.contains(searchQuery, ignoreCase = true) ||
                entry.jalaliDate.contains(searchQuery)

            val matchesFilter = when (selectedFilter) {
                "BALANCED" -> entry.isBalanced && entry.status != "VOID"
                "UNBALANCED" -> !entry.isBalanced && entry.status != "VOID"
                "VOID" -> entry.status == "VOID"
                "DRAFT" -> entry.status == "DRAFT"
                "FINAL" -> entry.status == "FINAL"
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    val filteredOfficialRows = remember(officialJournalRows, searchQuery) {
        if (searchQuery.isBlank()) officialJournalRows
        else {
            val q = searchQuery.trim()
            officialJournalRows.filter { r ->
                r.voucherNumber.toString().contains(q) ||
                r.jalaliDate.contains(q) ||
                r.voucherDescription.contains(q, ignoreCase = true) ||
                r.accountTitle.contains(q, ignoreCase = true) ||
                r.accountCode.toString().contains(q) ||
                (r.partyName?.contains(q, ignoreCase = true) == true) ||
                r.lineDescription.contains(q, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // نوار جستجو، سوییچ بین اسناد و دفتر روزنامه رسمی، و دکمه‌های پرینت
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
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = if (viewFormat == "OFFICIAL_JOURNAL") "دفتر روزنامه عمومی (رویدادهای مالی رسمی)" else "دفتر اسناد حسابداری دوبل",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDarkPrimary
                                )
                                Text(
                                    text = if (viewFormat == "OFFICIAL_JOURNAL") "فهرست ترتیبی آرتیکل‌ها با ستون‌های بدهکار و بستانکار" else "مجموع ${NumberFormatUtils.toPersianDigits(entries.size)} سند ثبت شده",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (viewFormat == "OFFICIAL_JOURNAL") onExportOfficialJournalPdf() else onExportPdf()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (viewFormat == "OFFICIAL_JOURNAL") onExportOfficialJournalCsv() else onExportCsv()
                                },
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

                    // انتخاب فرمت نمایش: اسناد حسابداری یا دفتر روزنامه رسمی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = viewFormat == "VOUCHERS",
                            onClick = { viewFormat = "VOUCHERS" },
                            label = { Text("اسناد حسابداری (کارت‌ها)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = viewFormat == "OFFICIAL_JOURNAL",
                            onClick = { viewFormat = "OFFICIAL_JOURNAL" },
                            label = { Text("دفتر روزنامه ترتیبی (آرتیکل‌ها)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PersianGold,
                                selectedLabelColor = NavyPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو در شماره سند، شرح، حساب یا تاریخ...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface,
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    // چیپ‌های فیلتر وضعیت اسناد (فقط در حالت اسناد)
                    if (viewFormat == "VOUCHERS") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == "ALL",
                                    onClick = { selectedFilter = "ALL" },
                                    label = { Text("همه اسناد (${NumberFormatUtils.toPersianDigits(entries.size)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                val balancedCount = entries.count { it.isBalanced && it.status != "VOID" }
                                FilterChip(
                                    selected = selectedFilter == "BALANCED",
                                    onClick = { selectedFilter = "BALANCED" },
                                    label = { Text("تراز (${NumberFormatUtils.toPersianDigits(balancedCount)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccountingDebitGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                val unbalancedCount = entries.count { !it.isBalanced && it.status != "VOID" }
                                FilterChip(
                                    selected = selectedFilter == "UNBALANCED",
                                    onClick = { selectedFilter = "UNBALANCED" },
                                    label = { Text("ناهمخوان (${NumberFormatUtils.toPersianDigits(unbalancedCount)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccountingCreditRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                val draftCount = entries.count { it.status == "DRAFT" }
                                FilterChip(
                                    selected = selectedFilter == "DRAFT",
                                    onClick = { selectedFilter = "DRAFT" },
                                    label = { Text("پیش‌نویس (${NumberFormatUtils.toPersianDigits(draftCount)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MatteGold,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                val finalCount = entries.count { it.status == "FINAL" }
                                FilterChip(
                                    selected = selectedFilter == "FINAL",
                                    onClick = { selectedFilter = "FINAL" },
                                    label = { Text("قطعی (${NumberFormatUtils.toPersianDigits(finalCount)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                val voidCount = entries.count { it.status == "VOID" }
                                FilterChip(
                                    selected = selectedFilter == "VOID",
                                    onClick = { selectedFilter = "VOID" },
                                    label = { Text("باطل شده (${NumberFormatUtils.toPersianDigits(voidCount)})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TextDarkMuted,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // اگر حالت دفتر روزنامه رسمی انتخاب شده باشد
        if (viewFormat == "OFFICIAL_JOURNAL") {
            if (filteredOfficialRows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("سطری در دفتر روزنامه ثبت نشده است.", fontSize = 13.sp, color = TextDarkSecondary)
                        }
                    }
                }
            } else {
                items(filteredOfficialRows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = NavyPrimary.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            text = "سند ${NumberFormatUtils.toPersianDigits(row.voucherNumber)} / ردیف ${NumberFormatUtils.toPersianDigits(row.rowNumber)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${NumberFormatUtils.toPersianDigits(row.accountCode)} - ${row.accountTitle}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextDarkPrimary
                                    )
                                }
                                Text(
                                    text = NumberFormatUtils.toPersianDigits(row.jalaliDate),
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }

                            if (!row.partyName.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MatteGold.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "طرف حساب: ${row.partyName}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NavyPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = row.lineDescription.ifBlank { row.voucherDescription },
                                fontSize = 11.sp,
                                color = TextDarkSecondary
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("بدهکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.debit > 0) NumberFormatUtils.formatPrice(row.debit) else "-",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.debit > 0) AccountingDebitGreen else TextDarkMuted
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("بستانکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.credit > 0) NumberFormatUtils.formatPrice(row.credit) else "-",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.credit > 0) AccountingCreditRed else TextDarkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // حالت اسناد حسابداری (کارت‌ها)
            if (filteredEntries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(40.dp))
                                Text(
                                    text = if (entries.isEmpty()) "هیچ سند حسابداری هنوز ثبت نشده است." else "سندی با مشخصات جستجو شده یافت نشد.",
                                    fontSize = 13.sp,
                                    color = TextDarkSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredEntries) { entry ->
                    val isVoid = entry.status == "VOID"
                    val isDraft = entry.status == "DRAFT"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVoid) CleanSurface.copy(alpha = 0.7f) else CleanSurface
                        ),
                        border = BorderStroke(1.dp, if (isVoid) CleanBorder else if (!entry.isBalanced) AccountingCreditRed.copy(alpha = 0.4f) else CleanBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isVoid) TextDarkMuted.copy(alpha = 0.15f) else NavyPrimary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = "سند شماره ${NumberFormatUtils.toPersianDigits(entry.voucherNumber)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isVoid) TextDarkMuted else NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CleanBackground
                                    ) {
                                        Text(
                                            text = NumberFormatUtils.toPersianDigits(entry.jalaliDate),
                                            fontSize = 11.sp,
                                            color = TextDarkSecondary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = entry.description,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isVoid) TextDarkMuted else TextDarkPrimary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // وضعیت تراز و وضعیت قطعی/باطل
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (entry.isBalanced) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (entry.isBalanced) AccountingDebitGreen else AccountingCreditRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (entry.isBalanced) "سند تراز دوبل دارد" else "عدم انطباق تراز!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (entry.isBalanced) AccountingDebitGreen else AccountingCreditRed
                                    )
                                }

                                val (statusLabel, statusColor) = when {
                                    isVoid -> "باطل شده" to AccountingCreditRed
                                    entry.status == "FINAL" -> "ثبت قطعی" to NavyPrimary
                                    else -> "پیش‌نویس" to MatteGold
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = statusLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = CleanBorder
                            )

                            // دکمه‌های عملیاتی: مشاهده آرتیکل‌ها، تایید و قطعی‌سازی، ویرایش، ابطال و حذف
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { onViewDetails(entry) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("view_voucher_details_${entry.id}")
                                    ) {
                                        Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("آرتیکل‌ها و چاپ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (isDraft && !isVoid && entry.isBalanced) {
                                        OutlinedButton(
                                            onClick = { onApproveVoucher(entry) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, AccountingDebitGreen),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = AccountingDebitGreen.copy(alpha = 0.08f)),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccountingDebitGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("تأیید و قطعی", fontSize = 11.sp, color = AccountingDebitGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!isVoid) {
                                        OutlinedButton(
                                            onClick = { onFullEditVoucher(entry) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.4f)),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("ویرایش کامل", fontSize = 11.sp, color = NavyPrimary)
                                        }
                                        OutlinedButton(
                                            onClick = { voidingVoucher = entry },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, AccountingCreditRed.copy(alpha = 0.5f)),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("ابطال", fontSize = 11.sp, color = AccountingCreditRed)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { deletingVoucherId = entry.id },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        border = BorderStroke(1.dp, CleanBorder),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: ویرایش شرح و تاریخ سند
    editingVoucher?.let { v ->
        var desc by remember { mutableStateOf(v.description) }
        var date by remember { mutableStateOf(v.jalaliDate) }

        AlertDialog(
            onDismissRequest = { editingVoucher = null },
            title = {
                Text(
                    text = "ویرایش سند شماره ${NumberFormatUtils.toPersianDigits(v.voucherNumber)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("شرح سند مالی") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("تاریخ سند (هجری شمسی)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateVoucher(v, desc, date)
                        editingVoucher = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ذخیره تغییرات", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingVoucher = null }) {
                    Text("انصراف", color = TextDarkSecondary)
                }
            }
        )
    }

    // Modal: تایید ابطال سند
    voidingVoucher?.let { v ->
        AlertDialog(
            onDismissRequest = { voidingVoucher = null },
            title = {
                Text(
                    text = "ابطال سند شماره ${NumberFormatUtils.toPersianDigits(v.voucherNumber)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AccountingCreditRed
                )
            },
            text = {
                Text(
                    text = "آیا از ابطال این سند حسابداری اطمینان دارید؟ وضعیت سند به VOID تغییر خواهد یافت و اثر مالی آن متوقف می‌شود.",
                    fontSize = 13.sp,
                    color = TextDarkPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVoidVoucher(v)
                        voidingVoucher = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ابطال قطعی سند", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { voidingVoucher = null }) {
                    Text("انصراف", color = TextDarkSecondary)
                }
            }
        )
    }

    // Modal: تایید حذف سند
    deletingVoucherId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingVoucherId = null },
            title = {
                Text(
                    text = "حذف کامل سند مالی",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AccountingCreditRed
                )
            },
            text = {
                Text(
                    text = "آیا از حذف این سند و آرتیکل‌های آن اطمینان دارید؟ این عملیات غیرقابل بازگشت است.",
                    fontSize = 13.sp,
                    color = TextDarkPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVoucher(id)
                        deletingVoucherId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccountingCreditRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حذف سند", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVoucherId = null }) {
                    Text("انصراف", color = TextDarkSecondary)
                }
            }
        )
    }
}

// ============================================================================
// تب ۳: ثبت سند دوبل (بدهکار و بستانکار)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateJournalVoucherContent(
    subsidiaries: List<SubsidiaryAccountEntity>,
    generals: List<GeneralAccountEntity>,
    parties: List<PartyEntity>,
    draftDesc: String,
    draftDate: String,
    draftType: String,
    draftItems: List<VoucherItemDraft>,
    onDescChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onAutoBalance: (SubsidiaryAccountEntity) -> Unit,
    onAddItem: (VoucherItemDraft) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var subSearchQuery by remember { mutableStateOf("") }
    var selectedSub by remember { mutableStateOf<SubsidiaryAccountEntity?>(null) }
    
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }
    var selectedParty by remember { mutableStateOf<PartyEntity?>(null) }

    var itemDesc by remember { mutableStateOf("") }
    var itemDebitText by remember { mutableStateOf("") }
    var itemCreditText by remember { mutableStateOf("") }

    val totalDebit = draftItems.sumOf { it.debit }
    val totalCredit = draftItems.sumOf { it.credit }
    val isBalanced = totalDebit == totalCredit && draftItems.isNotEmpty()
    val balanceDifference = Math.abs(totalDebit - totalCredit)

    val filteredSubs = remember(subsidiaries, subSearchQuery) {
        if (subSearchQuery.isBlank()) subsidiaries
        else subsidiaries.filter {
            it.title.contains(subSearchQuery, ignoreCase = true) ||
            it.code.toString().contains(subSearchQuery)
        }
    }

    val filteredParties = remember(parties, partySearchQuery) {
        if (partySearchQuery.isBlank()) parties
        else parties.filter {
            it.name.contains(partySearchQuery, ignoreCase = true) ||
            it.phone.contains(partySearchQuery)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // الگوهای سریع و هوشمند صدور سند مالی
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = MatteGold, modifier = Modifier.size(18.dp))
                        Text("الگوهای سریع صدور سند دوبل استاندارد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Surface(
                                onClick = { onApplyPreset("CASH_TO_BANK") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("واریز صندوق به بانک", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            Surface(
                                onClick = { onApplyPreset("BANK_TO_CASH") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("برداشت بانک به صندوق", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            Surface(
                                onClick = { onApplyPreset("EXPENSE_CASH") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("هزینه نقدی از صندوق", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            Surface(
                                onClick = { onApplyPreset("PARTY_RECEIVE") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("دریافت از مشتری / طرف حساب", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            Surface(
                                onClick = { onApplyPreset("PARTY_PAY") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("پرداخت به تامین‌کننده", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            Surface(
                                onClick = { onApplyPreset("CAPITAL_INJECTION") },
                                shape = RoundedCornerShape(8.dp),
                                color = CleanBackground,
                                border = BorderStroke(1.dp, CleanBorder)
                            ) {
                                Text("آورده نقدی شرکاء (سرمایه)", fontSize = 11.sp, color = NavyPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        // ۱. مشخصات عمومی سند و نوع سند مالی
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(NavyPrimary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("اطلاعات عمومی سند حسابداری", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkPrimary)
                            Text("نوع سند، شرح کلی و تاریخ صدور سند دوبل مالی", fontSize = 11.sp, color = TextDarkSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // نوع سند حسابداری (عادی، افتتاحیه، اختتامیه، تسویه)
                    Text("نوع سند حسابداری:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "NORMAL" to "عادی",
                            "OPENING" to "افتتاحیه",
                            "CLOSING" to "اختتامیه",
                            "SETTLEMENT" to "تسویه حساب‌ها"
                        ).forEach { (typeCode, typeLabel) ->
                            FilterChip(
                                selected = draftType == typeCode,
                                onClick = { onTypeChange(typeCode) },
                                label = { Text(typeLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = draftDesc,
                        onValueChange = onDescChange,
                        label = { Text("شرح کلی سند حسابداری") },
                        placeholder = { Text("مثلاً: ثبت هزینه کرایه حمل، خرید ملزومات اداری یا تسویه صندوق") },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder,
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voucher_description_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = draftDate,
                        onValueChange = onDateChange,
                        label = { Text("تاریخ سند (هجری شمسی)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder,
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ۲. فرم افزودن سطر آرتیکل جدید
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MatteGold.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MatteGold, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("افزودن سطر آرتیکل جدید", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkPrimary)
                            Text("انتخاب حساب معین، طرف حساب و ثبت مبالغ", fontSize = 11.sp, color = TextDarkSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // انتخـاب حسـاب معین
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSub?.let { "${NumberFormatUtils.toPersianDigits(it.code)} - ${it.title}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("انتخاب حساب معین *") },
                            placeholder = { Text("برای انتخاب کلیک فرمایید...") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            shape = RoundedCornerShape(10.dp),
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
                            expanded = expanded,
                            onDismissRequest = {
                                expanded = false
                                subSearchQuery = ""
                            },
                            modifier = Modifier.background(CleanSurface)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = subSearchQuery,
                                    onValueChange = { subSearchQuery = it },
                                    placeholder = { Text("جستجوی نام یا کد معین...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            filteredSubs.forEach { sub ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${NumberFormatUtils.toPersianDigits(sub.code)} - ${sub.title}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (sub.nature == "DEBIT") "بدهکار" else "بستانکار",
                                                fontSize = 10.sp,
                                                color = if (sub.nature == "DEBIT") AccountingDebitGreen else AccountingCreditRed
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSub = sub
                                        expanded = false
                                        subSearchQuery = ""
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // انتخـاب طرف حساب یا مشتری مرتبط (تفصیلی شناور)
                    ExposedDropdownMenuBox(
                        expanded = partyDropdownExpanded,
                        onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedParty?.let { "${it.name} (${it.phone})" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("طرف حساب / مشتری مرتبط (اختیاری)") },
                            placeholder = { Text("در صورت ارتباط با شخص، انتخاب فرمایید...") },
                            trailingIcon = {
                                if (selectedParty != null) {
                                    IconButton(onClick = { selectedParty = null }) {
                                        Icon(Icons.Default.Clear, contentDescription = "حذف شخص", modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
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
                                    placeholder = { Text("جستجوی نام شخص یا تلفن...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            filteredParties.forEach { party ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(party.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(party.phone, fontSize = 10.sp, color = TextDarkSecondary)
                                        }
                                    },
                                    onClick = {
                                        selectedParty = party
                                        partyDropdownExpanded = false
                                        partySearchQuery = ""
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = itemDesc,
                        onValueChange = { itemDesc = it },
                        label = { Text("شرح سطر آرتیکل (اختیاری)") },
                        placeholder = { Text("در صورت خالی بودن، شرح کلی سند درج می‌شود") },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder,
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // دکمه پیشنهاد تکمیل خودکار مبلغ اختلاف تراز
                    if (draftItems.isNotEmpty() && balanceDifference > 0L) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (totalDebit > totalCredit) {
                                        itemCreditText = balanceDifference.toString()
                                        itemDebitText = ""
                                    } else {
                                        itemDebitText = balanceDifference.toString()
                                        itemCreditText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MatteGold),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = MatteGold.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MatteGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val neededSide = if (totalDebit > totalCredit) "بستانکار" else "بدهکار"
                                Text(
                                    "تراز خودکار: درج ${NumberFormatUtils.formatPrice(balanceDifference)} در سمت $neededSide",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }

                            if (selectedSub != null) {
                                Button(
                                    onClick = { onAutoBalance(selectedSub!!) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccountingDebitGreen, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "افزودن مستقیم سطر موازنه‌ساز برای «${selectedSub!!.title}»",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = itemDebitText,
                            onValueChange = {
                                itemDebitText = it
                                if (it.isNotBlank()) itemCreditText = ""
                            },
                            label = { Text("بدهکار (تومان)") },
                            placeholder = { Text("۰") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccountingDebitGreen,
                                unfocusedBorderColor = CleanBorder,
                                focusedContainerColor = CleanSurface,
                                unfocusedContainerColor = CleanSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("debit_input")
                        )

                        OutlinedTextField(
                            value = itemCreditText,
                            onValueChange = {
                                itemCreditText = it
                                if (it.isNotBlank()) itemDebitText = ""
                            },
                            label = { Text("بستانکار (تومان)") },
                            placeholder = { Text("۰") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccountingCreditRed,
                                unfocusedBorderColor = CleanBorder,
                                focusedContainerColor = CleanSurface,
                                unfocusedContainerColor = CleanSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("credit_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val sub = selectedSub ?: return@Button
                            val debit = itemDebitText.toLongOrNull() ?: 0L
                            val credit = itemCreditText.toLongOrNull() ?: 0L
                            if (debit == 0L && credit == 0L) return@Button

                            onAddItem(
                                VoucherItemDraft(
                                    subsidiaryCode = sub.code,
                                    subsidiaryTitle = sub.title,
                                    generalCode = sub.generalCode,
                                    partyId = selectedParty?.id,
                                    partyName = selectedParty?.name,
                                    description = itemDesc.ifBlank { draftDesc.ifBlank { "آرتیکل سند مالی" } },
                                    debit = debit,
                                    credit = credit
                                )
                            )
                            itemDesc = ""
                            itemDebitText = ""
                            itemCreditText = ""
                            selectedParty = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("add_item_to_voucher_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MatteGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("افزودن سطر به سند", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ۳. فهرست سطرهای اضافه شده به پیش‌نویس
        if (draftItems.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سطرهای آرتیکل سند (${NumberFormatUtils.toPersianDigits(draftItems.size)} ردیف)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextDarkPrimary
                    )
                }
            }

            items(draftItems.size) { index ->
                val item = draftItems[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    border = BorderStroke(1.dp, CleanBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = NavyPrimary.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = "${NumberFormatUtils.toPersianDigits(index + 1)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = NavyPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "${NumberFormatUtils.toPersianDigits(item.subsidiaryCode)} - ${item.subsidiaryTitle}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextDarkPrimary
                                )
                            }

                            if (!item.partyName.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MatteGold.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "طرف حساب: ${item.partyName}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NavyPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                fontSize = 11.sp,
                                color = TextDarkSecondary
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (item.debit > 0) {
                                    Text(
                                        text = "بدهکار: ${NumberFormatUtils.formatPrice(item.debit)}",
                                        color = AccountingDebitGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                if (item.credit > 0) {
                                    Text(
                                        text = "بستانکار: ${NumberFormatUtils.formatPrice(item.credit)}",
                                        color = AccountingCreditRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onRemoveItem(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف سطر",
                                tint = AccountingCreditRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ۴. ویجت مانیتور تراز لحظه‌ای دوبل
        item {
            val balanceDiff = Math.abs(totalDebit - totalCredit)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBalanced) AccountingDebitGreen.copy(alpha = 0.08f) else AccountingCreditRed.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, if (isBalanced) AccountingDebitGreen.copy(alpha = 0.3f) else AccountingCreditRed.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "وضعیت تراز دوبل سند:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextDarkPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isBalanced) AccountingDebitGreen else AccountingCreditRed
                        ) {
                            Text(
                                text = if (isBalanced) "سند تراز است ✓" else "اختلاف تراز دارد ✕",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = (if (isBalanced) AccountingDebitGreen else AccountingCreditRed).copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مجموع بدهکار:", fontSize = 12.sp, color = TextDarkSecondary)
                        Text(
                            text = NumberFormatUtils.formatPrice(totalDebit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AccountingDebitGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مجموع بستانکار:", fontSize = 12.sp, color = TextDarkSecondary)
                        Text(
                            text = NumberFormatUtils.formatPrice(totalCredit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AccountingCreditRed
                        )
                    }

                    if (!isBalanced && draftItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("اختلاف بدهکار و بستانکار:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingCreditRed)
                            Text(
                                text = NumberFormatUtils.formatPrice(balanceDiff),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AccountingCreditRed
                            )
                        }
                    }
                }
            }
        }

        // ۵. دکمه ثبت نهایی سند حسابداری
        item {
            Button(
                onClick = onSubmit,
                enabled = isBalanced && draftItems.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_voucher_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = CleanBorder,
                    disabledContentColor = TextDarkMuted
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (isBalanced && draftItems.size >= 2) MatteGold else TextDarkMuted)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ثبت نهایی و صدور سند حسابداری",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// تب ۴: تراز آزمایشی (چهارستونی معین)
// ============================================================================
@Composable
private fun TrialBalanceContent(
    trialBalance: List<TrialBalanceRow>,
    generalTrialBalance: List<GeneralTrialBalanceRow>,
    sixColumnTrialBalance: List<SixColumnTrialBalanceRow>,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGeneralPdf: () -> Unit,
    onExportGeneralCsv: () -> Unit,
    onExportSixColumnPdf: () -> Unit,
    onExportSixColumnCsv: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var onlyActive by remember { mutableStateOf(false) }
    var levelMode by remember { mutableStateOf("SUBSIDIARY") } // SUBSIDIARY or GENERAL
    var columnMode by remember { mutableStateOf("FOUR_COL") } // TWO_COL, FOUR_COL, SIX_COL

    val isGeneral = levelMode == "GENERAL" && columnMode != "SIX_COL"
    val isSixCol = columnMode == "SIX_COL"

    val filteredSubRows = remember(trialBalance, searchQuery, onlyActive) {
        trialBalance.filter { row ->
            val matchesSearch = searchQuery.isBlank() ||
                row.subsidiaryCode.toString().contains(searchQuery) ||
                row.title.contains(searchQuery, ignoreCase = true) ||
                row.generalTitle.contains(searchQuery, ignoreCase = true)
            val matchesActive = !onlyActive || (row.totalDebit > 0 || row.totalCredit > 0 || row.balanceDebit > 0 || row.balanceCredit > 0)
            matchesSearch && matchesActive
        }
    }

    val filteredGenRows = remember(generalTrialBalance, searchQuery, onlyActive) {
        generalTrialBalance.filter { row ->
            val matchesSearch = searchQuery.isBlank() ||
                row.generalCode.toString().contains(searchQuery) ||
                row.title.contains(searchQuery, ignoreCase = true) ||
                row.groupTitle.contains(searchQuery, ignoreCase = true)
            val matchesActive = !onlyActive || (row.totalDebit > 0 || row.totalCredit > 0 || row.balanceDebit > 0 || row.balanceCredit > 0)
            matchesSearch && matchesActive
        }
    }

    val filteredSixColRows = remember(sixColumnTrialBalance, searchQuery, onlyActive) {
        sixColumnTrialBalance.filter { row ->
            val matchesSearch = searchQuery.isBlank() ||
                row.code.toString().contains(searchQuery) ||
                row.title.contains(searchQuery, ignoreCase = true) ||
                row.generalTitle.contains(searchQuery, ignoreCase = true)
            val matchesActive = !onlyActive || (
                row.openingDebit > 0 || row.openingCredit > 0 ||
                row.periodDebit > 0 || row.periodCredit > 0 ||
                row.finalDebit > 0 || row.finalCredit > 0
            )
            matchesSearch && matchesActive
        }
    }

    val sumTurnoverDebit = remember(trialBalance, generalTrialBalance, isGeneral) {
        if (isGeneral) generalTrialBalance.sumOf { it.totalDebit } else trialBalance.sumOf { it.totalDebit }
    }
    val sumTurnoverCredit = remember(trialBalance, generalTrialBalance, isGeneral) {
        if (isGeneral) generalTrialBalance.sumOf { it.totalCredit } else trialBalance.sumOf { it.totalCredit }
    }
    val sumBalanceDebit = remember(trialBalance, generalTrialBalance, isGeneral) {
        if (isGeneral) generalTrialBalance.sumOf { it.balanceDebit } else trialBalance.sumOf { it.balanceDebit }
    }
    val sumBalanceCredit = remember(trialBalance, generalTrialBalance, isGeneral) {
        if (isGeneral) generalTrialBalance.sumOf { it.balanceCredit } else trialBalance.sumOf { it.balanceCredit }
    }

    val sixSumOpeningDebit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.openingDebit } }
    val sixSumOpeningCredit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.openingCredit } }
    val sixSumPeriodDebit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.periodDebit } }
    val sixSumPeriodCredit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.periodCredit } }
    val sixSumFinalDebit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.finalDebit } }
    val sixSumFinalCredit = remember(sixColumnTrialBalance) { sixColumnTrialBalance.sumOf { it.finalCredit } }

    val isTrialBalanced = if (isSixCol) {
        sixSumOpeningDebit == sixSumOpeningCredit &&
        sixSumPeriodDebit == sixSumPeriodCredit &&
        sixSumFinalDebit == sixSumFinalCredit
    } else {
        sumBalanceDebit == sumBalanceCredit
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // ۱. کارت خلاصه و کنترل‌های تراز آزمایشی
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(NavyPrimary.copy(alpha = 0.08f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (isSixCol) "تراز آزمایشی ۶ ستونی جامع (استاندارد حسابرسی)" else if (isGeneral) "تراز آزمایشی سطح حساب‌های کل" else "تراز آزمایشی سطح معین",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDarkPrimary
                                )
                                Text(
                                    text = when (columnMode) {
                                        "SIX_COL" -> "گردش قبل، گردش طی دوره و مانده نهایی"
                                        "FOUR_COL" -> "تراز ۴ ستونی (گردش و مانده)"
                                        else -> "تراز ۲ ستونی (فقط مانده نهایی)"
                                    },
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    when {
                                        isSixCol -> onExportSixColumnPdf()
                                        isGeneral -> onExportGeneralPdf()
                                        else -> onExportPdf()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    when {
                                        isSixCol -> onExportSixColumnCsv()
                                        isGeneral -> onExportGeneralCsv()
                                        else -> onExportCsv()
                                    }
                                },
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

                    // انتخاب سطح تراز و نوع ستون‌بندی (۲ ستونی، ۴ ستونی، ۶ ستونی)
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = levelMode == "SUBSIDIARY" && !isSixCol,
                                onClick = {
                                    levelMode = "SUBSIDIARY"
                                    if (columnMode == "SIX_COL") columnMode = "FOUR_COL"
                                },
                                label = { Text("سطح معین", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White)
                            )
                        }
                        item {
                            FilterChip(
                                selected = levelMode == "GENERAL" && !isSixCol,
                                onClick = {
                                    levelMode = "GENERAL"
                                    if (columnMode == "SIX_COL") columnMode = "FOUR_COL"
                                },
                                label = { Text("سطح کل", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White)
                            )
                        }
                        item {
                            FilterChip(
                                selected = columnMode == "SIX_COL",
                                onClick = { columnMode = "SIX_COL" },
                                label = { Text("۶ ستونی (جامع)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MatteGold, selectedLabelColor = NavyPrimary)
                            )
                        }
                        item {
                            FilterChip(
                                selected = columnMode == "FOUR_COL",
                                onClick = { columnMode = "FOUR_COL" },
                                label = { Text("۴ ستونی", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PersianGold, selectedLabelColor = NavyPrimary)
                            )
                        }
                        item {
                            FilterChip(
                                selected = columnMode == "TWO_COL",
                                onClick = { columnMode = "TWO_COL" },
                                label = { Text("۲ ستونی", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PersianGold, selectedLabelColor = NavyPrimary)
                            )
                        }
                    }

                    // خلاصه وضعیت انطباق مانده‌ها
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTrialBalanced) AccountingDebitGreen.copy(alpha = 0.08f) else AccountingCreditRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isTrialBalanced) AccountingDebitGreen.copy(alpha = 0.3f) else AccountingCreditRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTrialBalanced) "تراز سیستم تأیید است (مجموع بدهکار = بستانکار) ✓" else "عدم انطباق در تراز آزمایشی! ✕",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTrialBalanced) AccountingDebitGreen else AccountingCreditRed
                            )
                            Icon(
                                imageVector = if (isTrialBalanced) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isTrialBalanced) AccountingDebitGreen else AccountingCreditRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // جدول سرجمع ستون‌ها بر اساس حالت ستونی
                    if (isSixCol) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CleanBackground, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سرجمع گردش افتتاحیه / قبل:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text("بد: ${NumberFormatUtils.formatPrice(sixSumOpeningDebit)} | بس: ${NumberFormatUtils.formatPrice(sixSumOpeningCredit)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سرجمع گردش طی دوره:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text("بد: ${NumberFormatUtils.formatPrice(sixSumPeriodDebit)} | بس: ${NumberFormatUtils.formatPrice(sixSumPeriodCredit)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سرجمع مانده نهایی پایان دوره:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                Text("بد: ${NumberFormatUtils.formatPrice(sixSumFinalDebit)} | بس: ${NumberFormatUtils.formatPrice(sixSumFinalCredit)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sixSumFinalDebit == sixSumFinalCredit) AccountingDebitGreen else AccountingCreditRed)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CleanBackground, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (columnMode == "FOUR_COL") {
                                Column {
                                    Text("گردش بد:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(NumberFormatUtils.formatPrice(sumTurnoverDebit), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                                }
                                Column {
                                    Text("گردش بس:", fontSize = 10.sp, color = TextDarkSecondary)
                                    Text(NumberFormatUtils.formatPrice(sumTurnoverCredit), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                                }
                            }
                            Column {
                                Text("مانده بدهکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text(NumberFormatUtils.formatPrice(sumBalanceDebit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingDebitGreen)
                            }
                            Column {
                                Text("مانده بستانکار:", fontSize = 10.sp, color = TextDarkSecondary)
                                Text(NumberFormatUtils.formatPrice(sumBalanceCredit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccountingCreditRed)
                            }
                        }
                    }

                    // فیلتر و جستجو
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو در کد، نام یا گروه حساب...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CleanSurface,
                            unfocusedContainerColor = CleanSurface,
                            focusedBorderColor = MatteGold,
                            unfocusedBorderColor = CleanBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilterChip(
                            selected = onlyActive,
                            onClick = { onlyActive = !onlyActive },
                            label = { Text("فقط حساب‌های دارای گردش یا مانده", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // ۲. ردیف‌های تراز آزمایشی بر اساس سطح و حالت ستونی
        if (isSixCol) {
            if (filteredSixColRows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("موردی برای نمایش در تراز ۶ ستونی یافت نشد.", fontSize = 12.sp, color = TextDarkSecondary)
                        }
                    }
                }
            } else {
                items(filteredSixColRows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = NavyPrimary.copy(alpha = 0.08f)) {
                                        Text(
                                            text = NumberFormatUtils.toPersianDigits(row.code),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(row.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                }
                                Text(row.groupTitle, fontSize = 10.sp, color = TextDarkSecondary, fontWeight = FontWeight.Medium)
                            }

                            Text(
                                text = "حساب کل: ${row.generalTitle}",
                                fontSize = 11.sp,
                                color = TextDarkMuted
                            )

                            // ۳ جفت ستون ۶گانه
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("۱. قبل / افتتاحیه:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text("بد: ${NumberFormatUtils.formatPrice(row.openingDebit)} | بس: ${NumberFormatUtils.formatPrice(row.openingCredit)}", fontSize = 10.sp, color = TextDarkPrimary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("۲. طی دوره:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text("بد: ${NumberFormatUtils.formatPrice(row.periodDebit)} | بس: ${NumberFormatUtils.formatPrice(row.periodCredit)}", fontSize = 10.sp, color = TextDarkPrimary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("۳. مانده نهایی:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("بد: ${if (row.finalDebit > 0) NumberFormatUtils.formatPrice(row.finalDebit) else "-"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (row.finalDebit > 0) AccountingDebitGreen else TextDarkMuted)
                                        Text("بس: ${if (row.finalCredit > 0) NumberFormatUtils.formatPrice(row.finalCredit) else "-"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (row.finalCredit > 0) AccountingCreditRed else TextDarkMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (isGeneral) {
            if (filteredGenRows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("موردی برای نمایش در تراز کل یافت نشد.", fontSize = 12.sp, color = TextDarkSecondary)
                        }
                    }
                }
            } else {
                items(filteredGenRows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = NavyPrimary.copy(alpha = 0.08f)) {
                                        Text(
                                            text = NumberFormatUtils.toPersianDigits(row.generalCode),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(row.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                }
                                Text(row.groupTitle, fontSize = 10.sp, color = TextDarkSecondary, fontWeight = FontWeight.Medium)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (columnMode == "FOUR_COL") {
                                    Column {
                                        Text("گردش بد:", fontSize = 9.sp, color = TextDarkSecondary)
                                        Text(NumberFormatUtils.formatPrice(row.totalDebit), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                                    }
                                    Column {
                                        Text("گردش بس:", fontSize = 9.sp, color = TextDarkSecondary)
                                        Text(NumberFormatUtils.formatPrice(row.totalCredit), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                                    }
                                }

                                Column {
                                    Text("مانده بدهکار:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.balanceDebit > 0) NumberFormatUtils.formatPrice(row.balanceDebit) else "-",
                                        color = if (row.balanceDebit > 0) AccountingDebitGreen else TextDarkMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("مانده بستانکار:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.balanceCredit > 0) NumberFormatUtils.formatPrice(row.balanceCredit) else "-",
                                        color = if (row.balanceCredit > 0) AccountingCreditRed else TextDarkMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (filteredSubRows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("موردی برای نمایش در تراز معین یافت نشد.", fontSize = 12.sp, color = TextDarkSecondary)
                        }
                    }
                }
            } else {
                items(filteredSubRows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanSurface),
                        border = BorderStroke(1.dp, CleanBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = NavyPrimary.copy(alpha = 0.08f)) {
                                        Text(
                                            text = NumberFormatUtils.toPersianDigits(row.subsidiaryCode),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(row.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                }
                                Text(row.groupTitle, fontSize = 10.sp, color = TextDarkSecondary, fontWeight = FontWeight.Medium)
                            }

                            Text(
                                text = "حساب کل: ${row.generalTitle}",
                                fontSize = 11.sp,
                                color = TextDarkMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (columnMode == "FOUR_COL") {
                                    Column {
                                        Text("گردش بد:", fontSize = 9.sp, color = TextDarkSecondary)
                                        Text(NumberFormatUtils.formatPrice(row.totalDebit), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                                    }
                                    Column {
                                        Text("گردش بس:", fontSize = 9.sp, color = TextDarkSecondary)
                                        Text(NumberFormatUtils.formatPrice(row.totalCredit), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                                    }
                                }

                                Column {
                                    Text("مانده بد:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.balanceDebit > 0) NumberFormatUtils.formatPrice(row.balanceDebit) else "-",
                                        color = if (row.balanceDebit > 0) AccountingDebitGreen else TextDarkMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("مانده بس:", fontSize = 9.sp, color = TextDarkSecondary)
                                    Text(
                                        text = if (row.balanceCredit > 0) NumberFormatUtils.formatPrice(row.balanceCredit) else "-",
                                        color = if (row.balanceCredit > 0) AccountingCreditRed else TextDarkMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// تب ۵: صورت سود و زیان (گزارش جامع مالی دوره)
// ============================================================================
@Composable
private fun ProfitLossContent(
    statement: ProfitLossStatement,
    canViewProfit: Boolean,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    if (!canViewProfit) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(48.dp))
                    Text("محدودیت دسترسی نقش کاربر", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDarkPrimary)
                    Text(
                        text = "مشاهده صورت سود و زیان مالی تنها برای نقش «مدیر کل» مجاز است. لطفاً از بخش تنظیمات نقش خود را تغییر دهید.",
                        fontSize = 12.sp,
                        color = TextDarkSecondary
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(NavyPrimary.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("صورت سود و زیان جامع دوره مالی", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkPrimary)
                        Text("محاسبه عملکرد مالی بر اساس استاندارد حسابداری", fontSize = 11.sp, color = TextDarkSecondary)
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
        }

        // کارت صورت مالی به تفکیک اقلام استاندارد
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanSurface),
                border = BorderStroke(1.dp, CleanBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ۱. فروش ناخالص و درآمدهای عملیاتی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = AccountingDebitGreen, modifier = Modifier.size(18.dp))
                            Text("فروش ناخالص و درآمدهای عملیاتی:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDarkPrimary)
                        }
                        Text(
                            text = NumberFormatUtils.formatPrice(statement.totalRevenue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AccountingDebitGreen
                        )
                    }

                    // ۲. کسر می‌شود: برگشت از فروش و تخفیفات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MatteGold, modifier = Modifier.size(18.dp))
                            Text("کسر می‌شود: برگشت از فروش و تخفیفات:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDarkPrimary)
                        }
                        Text(
                            text = if (statement.salesReturnsAndDiscounts > 0) "- ${NumberFormatUtils.formatPrice(statement.salesReturnsAndDiscounts)}" else NumberFormatUtils.formatPrice(0L),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (statement.salesReturnsAndDiscounts > 0) MatteGold else TextDarkMuted
                        )
                    }

                    HorizontalDivider(color = CleanBorder)

                    // ۳. خالص فروش و درآمدهای عملیاتی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "خالص درآمدهای فروش و عملیاتی:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NavyPrimary,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                        )
                        Text(
                            text = NumberFormatUtils.formatPrice(statement.netSalesRevenue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NavyPrimary
                        )
                    }

                    // ۴. بهای تمام شده کالای فروش رفته (COGS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = "کسر می‌شود: بهای تمام شده کالای فروش‌رفته:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDarkPrimary
                            )
                        }
                        Text(
                            text = if (statement.costOfGoodsSold > 0) "- ${NumberFormatUtils.formatPrice(statement.costOfGoodsSold)}" else NumberFormatUtils.formatPrice(0L),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (statement.costOfGoodsSold > 0) AccountingCreditRed else TextDarkMuted
                        )
                    }

                    HorizontalDivider(color = CleanBorder)

                    // ۵. سود (زیان) ناخالص
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سود (زیان) ناخالص:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextDarkPrimary,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                        )
                        Text(
                            text = NumberFormatUtils.formatPrice(statement.grossProfit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (statement.grossProfit >= 0) AccountingDebitGreen else AccountingCreditRed
                        )
                    }

                    // ۶. هزینه‌های عمومی، اداری و توزیع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(18.dp))
                            Text("کسر می‌شود: هزینه‌های عمومی، اداری و توزیع:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDarkPrimary)
                        }
                        Text(
                            text = if (statement.operatingExpenses > 0) "- ${NumberFormatUtils.formatPrice(statement.operatingExpenses)}" else NumberFormatUtils.formatPrice(0L),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (statement.operatingExpenses > 0) AccountingCreditRed else TextDarkMuted
                        )
                    }

                    HorizontalDivider(color = CleanBorder)

                    // ۷. سود (زیان) عملیاتی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سود (زیان) عملیاتی دوره:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextDarkPrimary,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                        )
                        Text(
                            text = NumberFormatUtils.formatPrice(statement.operatingProfit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (statement.operatingProfit >= 0) AccountingDebitGreen else AccountingCreditRed
                        )
                    }

                    HorizontalDivider(thickness = 2.dp, color = MatteGold)

                    // ۸. سود یا زیان خالص نهایی دوره
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (statement.netProfit >= 0) AccountingDebitGreen.copy(alpha = 0.08f) else AccountingCreditRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (statement.netProfit >= 0) AccountingDebitGreen.copy(alpha = 0.3f) else AccountingCreditRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                                Text(
                                    text = "سود (زیان) خالص نهایی دوره:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDarkPrimary
                                )
                                Text(
                                    text = if (statement.netProfit >= 0) "سوددهی دوره مالی مثبت است ✓" else "دوره با زیان مالی همراه بوده است ⚠",
                                    fontSize = 11.sp,
                                    color = if (statement.netProfit >= 0) AccountingDebitGreen else AccountingCreditRed
                                )
                            }
                            Text(
                                text = NumberFormatUtils.formatPrice(statement.netProfit),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = if (statement.netProfit >= 0) AccountingDebitGreen else AccountingCreditRed
                            )
                        }
                    }

                    // ۹. نسبت‌های مالی سه‌گانه استاندارد
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CleanBackground, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("حاشیه سود ناخالص", fontSize = 10.sp, color = TextDarkSecondary)
                            Text(
                                text = "%${NumberFormatUtils.toPersianDigits(statement.grossMarginPercent)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (statement.grossMarginPercent >= 0) NavyPrimary else AccountingCreditRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("حاشیه سود خالص", fontSize = 10.sp, color = TextDarkSecondary)
                            Text(
                                text = "%${NumberFormatUtils.toPersianDigits(statement.netMarginPercent)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (statement.netMarginPercent >= 0) AccountingDebitGreen else AccountingCreditRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("نسبت هزینه به درآمد", fontSize = 10.sp, color = TextDarkSecondary)
                            Text(
                                text = "%${NumberFormatUtils.toPersianDigits(statement.expenseToRevenuePercent)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatteGold
                            )
                        }
                    }
                }
            }
        }

        // تفکیک درآمدهای عملیاتی
        if (statement.revenueBreakdown.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    border = BorderStroke(1.dp, CleanBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = AccountingDebitGreen, modifier = Modifier.size(16.dp))
                            Text("تفکیک سرفصل‌های درآمدی دوره:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDarkPrimary)
                        }
                        statement.revenueBreakdown.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.first, fontSize = 11.sp, color = TextDarkPrimary, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                                Text(
                                    text = NumberFormatUtils.formatPrice(item.second),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccountingDebitGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // تفکیک هزینه‌های اداری، عمومی و توزیع
        if (statement.expenseBreakdown.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    border = BorderStroke(1.dp, CleanBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = AccountingCreditRed, modifier = Modifier.size(16.dp))
                            Text("تفکیک سرفصل‌های هزینه‌ای دوره:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDarkPrimary)
                        }
                        statement.expenseBreakdown.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CleanBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.first, fontSize = 11.sp, color = TextDarkPrimary, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                                Text(
                                    text = NumberFormatUtils.formatPrice(item.second),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccountingCreditRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
