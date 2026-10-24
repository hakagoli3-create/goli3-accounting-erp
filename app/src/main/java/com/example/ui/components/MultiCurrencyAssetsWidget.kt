package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CurrencyAssetDto
import com.example.ui.theme.MatteGold
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.viewmodel.AccountingViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.util.formatWithSeparator

/**
 * ویجت نمایش گزارش کل دارایی‌ها به تفکیک نوع ارز
 * منطبق با ساختار پیشنهادی: دریافت از Flow ویومدل و رندر ستونی ارزها (تومان، دلار، یورو، لیر و...)
 */
@Composable
fun MultiCurrencyAssetsWidget(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    onNavigateToTreasury: (() -> Unit)? = null
) {
    val currencyAssets by viewModel.assetsByCurrency.collectAsState(initial = emptyList())
    MultiCurrencyAssetsWidgetContent(
        currencyAssets = currencyAssets,
        modifier = modifier,
        onNavigateToTreasury = onNavigateToTreasury
    )
}

@Composable
fun MultiCurrencyAssetsWidget(
    viewModel: AccountingViewModel,
    modifier: Modifier = Modifier,
    onNavigateToTreasury: (() -> Unit)? = null
) {
    val currencyAssets by viewModel.assetsByCurrency.collectAsState(initial = emptyList())
    MultiCurrencyAssetsWidgetContent(
        currencyAssets = currencyAssets,
        modifier = modifier,
        onNavigateToTreasury = onNavigateToTreasury
    )
}

@Composable
fun MultiCurrencyAssetsWidget(
    currencyAssets: List<CurrencyAssetDto>,
    modifier: Modifier = Modifier,
    onNavigateToTreasury: (() -> Unit)? = null
) {
    MultiCurrencyAssetsWidgetContent(
        currencyAssets = currencyAssets,
        modifier = modifier,
        onNavigateToTreasury = onNavigateToTreasury
    )
}

@Composable
private fun MultiCurrencyAssetsWidgetContent(
    currencyAssets: List<CurrencyAssetDto>,
    modifier: Modifier = Modifier,
    onNavigateToTreasury: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("multi_currency_assets_widget")
            .then(
                if (onNavigateToTreasury != null) Modifier.clickable { onNavigateToTreasury() }
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MatteGold.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // سربرگ ویجت
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MatteGold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyExchange,
                            contentDescription = null,
                            tint = MatteGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "گزارش کل دارایی‌ها به تفکیک ارز",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "دارایی خالص / سرمایه واقعی (نقدینگی + مطالبات - بدهی‌ها)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextDarkSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MatteGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "چندارزی",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // نمایش به صورت ستون‌های تفکیک‌شده (تومان، دلار، یورو، لیر و...)
            if (currencyAssets.isEmpty()) {
                Text(
                    text = "در حال بارگذاری اطلاعات دارایی‌ها...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDarkSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    currencyAssets.forEachIndexed { index, asset ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = asset.currencyType,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDarkSecondary,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = asset.totalAmount.toLong().formatWithSeparator(),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
