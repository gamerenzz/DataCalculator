package com.example.datacalculator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 规划规则数据类
data class UsageRule(var days: String, var gbPerDay: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DataCalculatorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCalculatorScreen() {
    val context = LocalContext.current
    // 本地存储读取器
    val prefs = remember { context.getSharedPreferences("data_calc_prefs", Context.MODE_PRIVATE) }

    // 读取保存的数据
    var totalPlan by remember { mutableStateOf(prefs.getString("totalPlan", "400") ?: "400") }
    var currentRemaining by remember { mutableStateOf(prefs.getString("currentRemaining", "240") ?: "240") }

    // 读取保存的规则列表
    val initialRules = remember {
        val savedRules = prefs.getString("rulesData", "") ?: ""
        if (savedRules.isNotEmpty()) {
            try {
                savedRules.split("|").map {
                    val parts = it.split(":")
                    UsageRule(parts[0], parts[1])
                }.toMutableList()
            } catch (e: Exception) {
                mutableListOf(UsageRule("24", "6"), UsageRule("6", "16"))
            }
        } else {
            mutableListOf(UsageRule("24", "6"), UsageRule("6", "16"))
        }
    }

    val rules = remember { mutableStateListOf<UsageRule>().apply { addAll(initialRules) } }

    // 自动保存逻辑
    LaunchedEffect(totalPlan, currentRemaining, rules.toList()) {
        val rulesDataStr = rules.joinToString("|") { "${it.days}:${it.gbPerDay}" }
        prefs.edit()
            .putString("totalPlan", totalPlan)
            .putString("currentRemaining", currentRemaining)
            .putString("rulesData", rulesDataStr)
            .apply()
    }

    // --- 完整核心计算逻辑 ---
    val totalPlanNum = totalPlan.toDoubleOrNull() ?: 0.0
    val currentRemainingNum = currentRemaining.toDoubleOrNull() ?: 0.0
    val totalPlannedUsage = rules.sumOf { (it.days.toDoubleOrNull() ?: 0.0) * (it.gbPerDay.toDoubleOrNull() ?: 0.0) }

    // 1. 过去已用流量 = 套餐总量 - 当前剩余
    val alreadyUsed = (totalPlanNum - currentRemainingNum).coerceAtLeast(0.0)
    // 2. 全月预估用量 = 过去已用 + 后续规划
    val estimatedTotalUsed = alreadyUsed + totalPlannedUsage
    // 3. 月底结余 = 当前剩余 - 后续规划
    val finalBalance = currentRemainingNum - totalPlannedUsage
    // 4. 整体进度 (全月预估总用量 / 月套餐总量)
    val progress = if (totalPlanNum > 0) (estimatedTotalUsed / totalPlanNum).toFloat() else 1f

    Scaffold(
        topBar = { TopAppBar(title = { Text("流量精算师", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 结果概览卡片 ( Dashboard )
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (finalBalance >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "全月预估用量: ${String.format("%.1f", estimatedTotalUsed)} GB / 套餐: ${String.format("%.1f", totalPlanNum)} GB", 
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "(已用 ${String.format("%.1f", alreadyUsed)} GB + 后续规划 ${String.format("%.1f", totalPlannedUsage)} GB)", 
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))

                        // 全月百分比进度条
                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (finalBalance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (finalBalance >= 0) {
                            Text(
                                text = "月底预估还剩: ${String.format("%.1f", finalBalance)} GB", 
                                color = Color(0xFF2E7D32), 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 20.sp
                            )
                        } else {
                            Text(
                                text = "月底预估超额: ${String.format("%.1f", -finalBalance)} GB", 
                                color = Color(0xFFC62828), 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            // 2. 基础数据设置
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("基础数据设置", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = totalPlan, 
                                onValueChange = { totalPlan = it },
                                label = { Text("月套餐总量(GB)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currentRemaining, 
                                onValueChange = { currentRemaining = it },
                                label = { Text("当前剩余流量(GB)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. 动态规划规则
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("后续使用规划 (天数 x 每天GB)", fontWeight = FontWeight.Bold)
                    Button(onClick = { rules.add(UsageRule("1", "5")) }) { Text("添加规则") }
                }
            }

            itemsIndexed(rules) { index, rule ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rule.days, 
                            onValueChange = { rules[index] = rule.copy(days = it) },
                            label = { Text("天数") }, 
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text("天, 每天")
                        OutlinedTextField(
                            value = rule.gbPerDay, 
                            onValueChange = { rules[index] = rule.copy(gbPerDay = it) },
                            label = { Text("GB") }, 
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        IconButton(onClick = { if (rules.size > 1) rules.removeAt(index) }) {
                            Text("❌")
                        }
                    }
                }
            }
        }
    }
}
