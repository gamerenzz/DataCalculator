package com.example.datacalculator

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
    // 基础状态
    var totalPlan by remember { mutableStateOf("400") }
    var monthDays by remember { mutableStateOf("30") }
    var alreadyUsed by remember { mutableStateOf("0") }

    // 动态规则列表
    val rules = remember { mutableStateListOf(
        UsageRule("24", "6"),
        UsageRule("6", "16")
    ) }

    // 计算逻辑
    val totalPlanNum = totalPlan.toDoubleOrNull() ?: 0.0
    val alreadyUsedNum = alreadyUsed.toDoubleOrNull() ?: 0.0
    val planedUsage = rules.sumOf { (it.days.toDoubleOrNull() ?: 0.0) * (it.gbPerDay.toDoubleOrNull() ?: 0.0) }
    val totalEstimated = alreadyUsedNum + planedUsage
    val remaining = totalPlanNum - totalEstimated

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
            // 1. 结果展示卡片 (美观的Dashboard)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (remaining >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("预计总使用: ${String.format("%.1f", totalEstimated)} GB / $totalPlanNum GB", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 进度条
                        val progress = if (totalPlanNum > 0) (totalEstimated / totalPlanNum).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (remaining >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        if (remaining >= 0) {
                            Text("剩余量: ${String.format("%.1f", remaining)} GB", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        } else {
                            Text("超出套餐: ${String.format("%.1f", -remaining)} GB", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }
            }

            // 2. 基础输入
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("基础设定", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = totalPlan, onValueChange = { totalPlan = it },
                                label = { Text("月套餐(GB)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = alreadyUsed, onValueChange = { alreadyUsed = it },
                                label = { Text("已用流量(GB)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. 动态规划规则
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("预估规划 (天数 x 每天GB)", fontWeight = FontWeight.Bold)
                    Button(onClick = { rules.add(UsageRule("1", "5")) }) { Text("加一条") }
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
                            value = rule.days, onValueChange = { rules[index] = rule.copy(days = it) },
                            label = { Text("天数") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text("天, 每天")
                        OutlinedTextField(
                            value = rule.gbPerDay, onValueChange = { rules[index] = rule.copy(gbPerDay = it) },
                            label = { Text("GB") }, modifier = Modifier.weight(1f),
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
