package com.kexu.campus.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.dns.DnsFilterService
import com.kexu.campus.dns.DomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AdBlockScreen(data: CampusData, repo: CampusRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(DnsFilterService.isRunning) }
    var blocked by remember { mutableStateOf(DnsFilterService.blockedCount.get()) }
    var queried by remember { mutableStateOf(DnsFilterService.queriedCount.get()) }
    var ruleCounts by remember { mutableStateOf(Triple(0, 0, 0)) }

    var blockText by rememberSaveable {
        mutableStateOf(data.settings.customBlockDomains.joinToString("\n"))
    }
    var allowText by rememberSaveable {
        mutableStateOf(data.settings.customAllowDomains.joinToString("\n"))
    }
    var observe by rememberSaveable { mutableStateOf(data.settings.blockObserveAds) }

    LaunchedEffect(Unit) {
        while (true) {
            running = DnsFilterService.isRunning
            blocked = DnsFilterService.blockedCount.get()
            queried = DnsFilterService.queriedCount.get()
            delay(1500)
        }
    }

    LaunchedEffect(data.settings.customBlockDomains, data.settings.customAllowDomains) {
        ruleCounts = withContext(Dispatchers.IO) {
            val rules = DomainRules.load(
                context,
                data.settings.customBlockDomains,
                data.settings.customAllowDomains
            )
            Triple(rules.blockCount(), rules.allowCount(), rules.observeCount())
        }
    }

    fun persistConfig() {
        repo.updateSettings {
            it.copy(
                blockObserveAds = observe,
                customBlockDomains = parseDomains(blockText),
                customAllowDomains = parseDomains(allowText)
            )
        }
    }

    fun startNow() {
        persistConfig()
        val intent = Intent(context, DnsFilterService::class.java).apply {
            action = DnsFilterService.ACTION_START
            putExtra(DnsFilterService.EXTRA_OBSERVE, observe)
            putStringArrayListExtra(
                DnsFilterService.EXTRA_BLOCK_DOMAINS,
                ArrayList(parseDomains(blockText))
            )
            putStringArrayListExtra(
                DnsFilterService.EXTRA_ALLOW_DOMAINS,
                ArrayList(parseDomains(allowText))
            )
        }
        ContextCompat.startForegroundService(context, intent)
        running = true
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startNow()
    }

    fun ensureStart() {
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            vpnPermissionLauncher.launch(prepare)
        } else {
            startNow()
        }
    }

    fun stopNow() {
        runCatching {
            context.startService(
                Intent(context, DnsFilterService::class.java).apply {
                    action = DnsFilterService.ACTION_STOP
                }
            )
        }
        running = false
    }

    ScreenScaffold(
        title = "去广告",
        subtitle = "本地 DNS 过滤 · 不接管其他流量",
        onBack = onBack
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "状态") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (running) "去广告运行中" else "未开启",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "已查询 $queried 次 · 已拦截 $blocked 次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = running,
                            onCheckedChange = { checked ->
                                if (checked) ensureStart() else stopNow()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "开启后系统会显示 VPN 钥匙图标——这是本地过滤器，所有 DNS 查询都在手机内处理，" +
                            "不会把数据发到我们的服务器。与其它 VPN/加速器同时只能开一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "规则") {
                    Text(
                        "内置 anti-AD 规则：屏蔽 ${ruleCounts.first} 个域名 · 放行 ${ruleCounts.second} 个 · 观察 ${ruleCounts.third} 个",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("严格模式（含观察名单）", fontSize = 14.sp)
                            Text(
                                "观察名单是「疑似广告」域名，开启后拦截更彻底，但可能误伤个别功能。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = observe, onCheckedChange = { observe = it })
                    }
                }
            }

            item {
                SectionCard(title = "自定义域名") {
                    Text(
                        "每行一个域名，支持后缀匹配（例如 ad.example.com 会同时拦截 x.ad.example.com）。" +
                            "修改后请点下方按钮保存并重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = blockText,
                        onValueChange = { blockText = it },
                        label = { Text("额外屏蔽（每行一个）") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = allowText,
                        onValueChange = { allowText = it },
                        label = { Text("放行白名单（每行一个）") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            persistConfig()
                            if (running) {
                                stopNow()
                                startNow()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (running) "保存并重启拦截" else "保存")
                    }
                }
            }

            item {
                SectionCard(title = "说明与边界") {
                    val lines = listOf(
                        "• 只接管 DNS：其它流量走物理网卡，不影响定位、支付、校园网认证。",
                        "• 与其它 VPN 冲突：同一个时间只能运行一个 VPN。",
                        "• 如果已开启仍看到广告：先关闭系统「私人 DNS / Private DNS」，重启被拦截的 App 并清除它的缓存；很多开屏广告是 App 自己缓存或预加载的。",
                        "• DoH/私有 DNS/QUIC/IP 直连可绕过：部分 App 自带加密 DNS 或直接连 IP，DNS 拦截无法处理。",
                        "• 可把广告域名加进「自定义屏蔽」；开启「严格模式」可拦截更多疑似广告域名。",
                        "• 误杀处理：如果某个 App 因为广告加载失败而异常，把它的域名加到放行白名单。",
                        "• 仅本机、开源、非商业：请遵守各 App 的用户协议；不要用于商业分发。"
                    )
                    lines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("返回设置") }
            }
        }
    }
}

private fun parseDomains(text: String): List<String> =
    text.split('\n', ',', '，', ' ', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { raw ->
            var value = raw.lowercase()
            if (value.startsWith("http://")) value = value.substring(7)
            if (value.startsWith("https://")) value = value.substring(8)
            value.substringBefore('/')
        }
        .filter { it.contains('.') }
        .distinct()
