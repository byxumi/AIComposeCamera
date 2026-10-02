package com.aicamera.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicamera.core.design.CamButton
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamDialog
import com.aicamera.core.design.CamShapes
import com.aicamera.core.design.CamType

/** 「我的」页 — mola 风格: 头像 + 会员卡 + 功能入口 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onShare: () -> Unit
) {
    var showMemberDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CamColors.Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        // 头像 + 昵称
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CamColors.AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "AI",
                    color = CamColors.Accent,
                    style = CamType.Title,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("AI 相机用户", color = CamColors.White, style = CamType.Title)
                Spacer(Modifier.height(4.dp))
                Text(
                    "登录后可免费体验 AI 功能",
                    color = CamColors.SecondaryText,
                    style = CamType.Caption
                )
            }
            Spacer(Modifier.weight(1f))
            CamButton(
                text = "登录",
                onClick = { onShare() },
                modifier = Modifier.width(88.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // 会员卡(黑金)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CamShapes.Panel)
                .background(
                    Brush.linearGradient(
                        listOf(CamColors.SurfaceElevated, CamColors.AccentDim)
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = CamColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Mola 会员", color = CamColors.White, style = CamType.Body)
                Spacer(Modifier.weight(1f))
                Text(
                    "立即升级",
                    color = CamColors.Accent,
                    style = CamType.Body,
                    modifier = Modifier.clickable { showMemberDialog = true }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberTag("实况照片")
                MemberTag("满血像素")
                MemberTag("流光快门")
                MemberTag("无损画质")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 功能入口
        ProfileRow(Icons.Filled.PhotoLibrary, "我的作品", "查看已拍摄的照片") { onOpenGallery() }
        ProfileRow(Icons.Filled.Settings, "设置", "相机、构图、拍摄参数") { onOpenSettings() }
        ProfileRow(Icons.Filled.Favorite, "关于 Mola 相机", "设计灵感 · 意见反馈") { onShare() }
        ProfileRow(Icons.Filled.Share, "分享给好友", "分享本应用") { onShare() }

        Spacer(Modifier.weight(1f))

        Text(
            "AIComposeCamera v5.1.0 · mola 风格",
            color = CamColors.TertiaryText,
            style = CamType.Caption,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showMemberDialog) {
        CamDialog(
            title = "Mola 会员",
            onDismiss = { showMemberDialog = false },
            confirmText = "立即开通",
            onConfirm = { showMemberDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "解锁全部 AI 能力与无损画质",
                    color = CamColors.SecondaryText,
                    style = CamType.Body
                )
                MemberPlan("连续包月", "¥12/月", "AI 滤镜 · 流光快门 · 满血像素")
                MemberPlan("连续包年", "¥98/年", "省 ¥46 · 全部权益")
                MemberPlan("永久买断", "¥198 一次性", "一次购买 · 终身使用")
                Text(
                    "支付方式: 微信 / 支付宝 / 云闪付",
                    color = CamColors.TertiaryText,
                    style = CamType.Caption
                )
            }
        }
    }
}

@Composable
private fun MemberPlan(title: String, price: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CamShapes.Control)
            .background(CamColors.SurfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CamColors.White, style = CamType.Body)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = CamColors.TertiaryText, style = CamType.Caption)
        }
        Text(price, color = CamColors.Accent, style = CamType.Body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemberTag(text: String) {
    Box(
        modifier = Modifier
            .clip(CamShapes.Small)
            .background(CamColors.AccentDim)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = CamColors.AccentLight, style = CamType.Caption)
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CamShapes.Control)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CamColors.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CamColors.White, style = CamType.Body)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = CamColors.TertiaryText, style = CamType.Caption)
        }
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = CamColors.TertiaryText,
            modifier = Modifier.size(18.dp)
        )
    }
}