package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccountingDebitGreen
import com.example.ui.theme.CleanBorder
import com.example.ui.theme.MatteGold
import com.example.ui.theme.MatteGoldContainer
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.util.formatWithSeparator

/**
 * ویجت مدرن گزارش ارزش دارایی‌های انبار و کالاها (Inventory Assets Valuation Widget)
 * منطبق با Material 3 و نمایش ارزش انبار در دو ستون مجزا:
 * ۱. ارزش به قیمت خرید (بهای تمام‌شده)
 * ۲. ارزش به قیمت فروش (ارزش تخمینی بازار)
 */
@Composable
fun InventoryValuationWidget(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    onNavigateToInventory: (() -> Unit)? = null
) {
    val purchaseValue by viewModel.inventoryPurchaseValue.collectAsState(initial = 0.0)
    val salesValue by viewModel.inventorySalesValue.collectAsState(initial = 0.0)

    InventoryValuationWidgetContent(
        purchaseValue = purchaseValue,
        salesValue = salesValue,
        modifier = modifier,
        onNavigateToInventory = onNavigateToInventory
    )
}

@Composable
fun InventoryValuationWidget(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToInventory: (() -> Unit)? = null
) {
    val purchaseValue by viewModel.inventoryPurchaseValue.collectAsState(initial = 0.0)
    val salesValue by viewModel.inventorySalesValue.collectAsState(initial = 0.0)

    InventoryValuationWidgetContent(
        purchaseValue = purchaseValue,
        salesValue = salesValue,
        modifier = modifier,
        onNavigateToInventory = onNavigateToInventory
    )
}

@Composable
fun InventoryValuationWidget(
    purchaseValue: Double,
    salesValue: Double,
    modifier: Modifier = Modifier,
    onNavigateToInventory: (() -> Unit)? = null
) {
    InventoryValuationWidgetContent(
        purchaseValue = purchaseValue,
        salesValue = salesValue,
        modifier = modifier,
        onNavigateToInventory = onNavigateToInventory
    )
}

@Composable
private fun InventoryValuationWidgetContent(
    purchaseValue: Double,
    salesValue: Double,
    modifier: Modifier = Modifier,
    onNavigateToInventory: (() -> Unit)? = null
) {
    val potentialProfit = maxOf(0.0, salesValue - purchaseValue)
    val profitMarginPercent = if (purchaseValue > 0.0) {
        ((salesValue - purchaseValue) / purchaseValue * 100).toInt()
    } else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("inventory_valuation_widget")
            .then(
                if (onNavigateToInventory != null) Modifier.clickable { onNavigateToInventory() }
                else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MatteGold.copy(alpha = 0.4f)),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NavyPrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = "ارزش دارایی‌های انبار",
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "ارزش دارایی‌های انبار (کالاها)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ارزیابی موجودی به قیمت خرید و فروش",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextDarkMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MatteGoldContainer,
                        border = BorderStroke(0.5.dp, MatteGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "ارزش‌گذاری انبار",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (onNavigateToInventory != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "مشاهده انبار",
                            tint = TextDarkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = CleanBorder.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(14.dp))

            // دو ستون مجزا: ستون ارزش خرید و ستون ارزش فروش
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ستون ۱: ارزش بر مبنای قیمت خرید (بهای تمام شده)
                InventoryValuationColumnCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inventory_purchase_value_card"),
                    title = "ارزش به قیمت خرید",
                    subtitle = "بهای تمام‌شده موجودی",
                    value = purchaseValue,
                    icon = Icons.Default.Payments,
                    accentColor = AccountingDebitGreen,
                    backgroundColor = AccountingDebitGreen.copy(alpha = 0.05f),
                    borderColor = AccountingDebitGreen.copy(alpha = 0.22f)
                )

                // ستون ۲: ارزش بر مبنای قیمت فروش (نرخ بازار و مصرف‌کننده)
                InventoryValuationColumnCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inventory_sales_value_card"),
                    title = "ارزش به قیمت فروش",
                    subtitle = "ارزش کل بر مبنای نرخ فروش",
                    value = salesValue,
                    icon = Icons.Default.Sell,
                    accentColor = StatusInfoBlue,
                    backgroundColor = StatusInfoBlue.copy(alpha = 0.05f),
                    borderColor = StatusInfoBlue.copy(alpha = 0.22f)
                )
            }

            // نوار خلاصه سود ناخالص پیش‌بینی شده
            if (purchaseValue > 0.0 || salesValue > 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.8.dp, CleanBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = AccountingDebitGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "حاشیه سود ناخالص برآورد شده:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextDarkSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Text(
                            text = "${potentialProfit.formatWithSeparator()} تومان" +
                                    if (profitMarginPercent > 0) " (${com.example.util.NumberFormatUtils.toPersianDigits(profitMarginPercent.toString())}٪+)" else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AccountingDebitGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryValuationColumnCard(
    title: String,
    subtitle: String,
    value: Double,
    icon: ImageVector,
    accentColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // مقدار عددی فرمت‌بندی شده با formatWithSeparator()
            Text(
                text = value.formatWithSeparator(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = TextDarkPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextDarkMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextDarkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
