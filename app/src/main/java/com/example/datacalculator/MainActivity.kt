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
import java.text.SimpleDateFormat
import java.util.*

// 规划规则数据类
data class UsageRule(var days: String, var gbPerDay: String, var label: String = "")

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
    val prefs = remember { context.getSharedPreferences("data_calc_prefs", Context.MODE_PRIVATE) }

    // 1. 读取保存的设置
    var totalPlan by remember { mutableStateOf(prefs.getString("totalPlan", "400") ?: "400") }
    var currentRemaining by remember { mutableStateOf(prefs.getString("currentRemaining", "240") ?: "240") }
    
    // 默认工作日/休息日流量设定
    var defaultWorkdayGb by remember { mutableStateOf(prefs.getString("defaultWorkdayGb", "6") ?: "6") }
    var defaultWeekendGb by remember { mutableStateOf(prefs.getString("defaultWeekendGb", "16") ?: "16") }

    // 读取保存的规则列表
    val initialRules = remember {
        val savedRules = prefs.getString("rulesData", "") ?: ""
        if (savedRules.isNotEmpty()) {
            try {
                savedRules.split("|").map {
                    val parts = it.split(":")
                    UsageRule(parts[0], parts[1], if (parts.size > 2) parts[2] else "")
                }.toMutableList()
            } catch (e: Exception) {
                mutableListOf(UsageRule("15", "6", "工作日"), UsageRule("6", "16", "周末/节假日"))
            }
        } else {
            mutableListOf(UsageRule("15", "6", "工作日"), UsageRule("6", "16", "周末/节假日"))
        }
    }

    val rules = remember { mutableStateListOf<UsageRule>().apply { addAll(initialRules) } }

    // 自动保存逻辑
    LaunchedEffect(totalPlan, currentRemaining, defaultWorkdayGb, defaultWeekendGb, rules.toList()) {
        val rulesDataStr = rules.joinToString("|") { "${it.days}:${it.gbPerDay}:${it.label}" }
        prefs.edit()
            .putString("totalPlan", totalPlan)
            .putString("currentRemaining", currentRemaining)
            .putString("defaultWorkdayGb", defaultWorkdayGb)
            .putString("defaultWeekendGb", defaultWeekendGb)
            .putString("rulesData", rulesDataStr)
            .apply()
    }

    // --- 日期与天数智能计算逻辑 ---
    val todayCal = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    val todayStr = dateFormat.format(todayCal.time)
    
    // 计算当月剩余的工作日和周末天数
    fun calculateRemainingDays(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        var workdays = 0
        var weekends = 0
        
        for (day in todayDay..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                weekends++
            } else {
                workdays++
            }
        }
        return Pair(workdays, weekends)
    }

    val (remainingWorkdays, remainingWeekends) = remember(todayStr) { calculateRemainingDays() }

    // 一键生成智能规划函数
    fun applySmartPlanning() {
        rules.clear()
        if (remainingWorkdays > 0) {
            rules.add(UsageRule(remainingWorkdays.toString(), defaultWorkdayGb, "工作日"))
        }
        if (remainingWeekends > 0) {
            rules.add(UsageRule(remainingWeekends.toString(), defaultWeekendGb, "周末/节假日"))
        }
    }

    // --- 核心流量计算 ---
    val totalPlanNum = totalPlan.toDoubleOrNull() ?: 0.0
    val currentRemainingNum = currentRemaining.toDoubleOrNull() ?: 0.0
    val totalPlannedUsage = rules.sumOf { (it.days.toDoubleOrNull() ?: 0.0) * (it.gbPerDay.toDoubleOrNull() ?: 0.0) }

    val alreadyUsed = (totalPlanNum - currentRemainingNum).coerceAtLeast(0.0)
    val estimatedTotalUsed = alreadyUsed + totalPlannedUsage
    val finalBalance = currentRemainingNum - totalPlannedUsage
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

            // 2. 基础数据与智能配置
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("基础数据与每日额度设定", fontWeight = FontWeight.Bold)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = totalPlan, 
                                onValueChange = { totalPlan = it },
                                label = { Text("月套餐(GB)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currentRemaining, 
                                onValueChange = { currentRemaining = it },
                                label = { Text("当前剩余(GB)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = defaultWorkdayGb, 
                                onValueChange = { defaultWorkdayGb = it },
                                label = { Text("工作日用量(GB/天)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = defaultWeekendGb, 
                                onValueChange = { defaultWeekendGb = it },
                                label = { Text("周末用量(GB/天)") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // 智能计算信息展示
                        Text(
                            text = "今天是 $todayStr\n本月包含今天还剩: $remainingWorkdays 个工作日，$remainingWeekends 个周末",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Button(
                            onClick = { applySmartPlanning() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("⚡ 一键根据本月剩余天数生成规划")
                        }
                    }
                }
            }

            // 3. 动态规划规则列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("后续使用规划规则", fontWeight = FontWeight.Bold)
                    Button(onClick = { rules.add(UsageRule("1", "5", "自定义")) }) { Text("手动加一条") }
                }
            }

            itemsIndexed(rules) { index, rule ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = rule.days, 
                                onValueChange = { rules[index] = rule.copy(days = it) },
                                label = { Text(if (rule.label.isNotEmpty()) "天数(${rule.label})" else "天数") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Text("天, 每天")
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = rule.gbPerDay, 
                                onValueChange = { rules[index] = rule.copy(gbPerDay = it) },
                                label = { Text("GB") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        IconButton(onClick = { if (rules.size > 1) rules.removeAt(index) }) {
                            Text("❌")
                        }
                    }
                }
            }
        }
    }
}
