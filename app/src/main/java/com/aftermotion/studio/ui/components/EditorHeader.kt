package com.aftermotion.studio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.R
import com.aftermotion.studio.model.AspectRatio
import com.aftermotion.studio.ui.theme.AccentBlue
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.ui.theme.EditorDivider
import com.aftermotion.studio.ui.theme.EditorSurfaceVariant
import com.aftermotion.studio.ui.theme.IconActive
import com.aftermotion.studio.ui.theme.OnAccent
import com.aftermotion.studio.ui.theme.TextPrimary
import com.aftermotion.studio.ui.util.bouncyClickable

@Composable
fun EditorHeader(
    height: Dp,
    aspectRatio: AspectRatio,
    onBackClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            modifier = Modifier.testTag("header_back_button"),
            onClick = onBackClick,
            contentDescription = "Back"
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = IconActive, modifier = Modifier.size(20.dp))
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .bouncyClickable(pressedScale = 0.96f, onClick = onAspectRatioClick)
                .padding(horizontal = 13.dp, vertical = 7.dp)
                .testTag("header_aspect_ratio_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(17.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.4.dp, IconActive.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(aspectRatio.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = IconActive, modifier = Modifier.size(17.dp))
        }

        Image(
            painter = painterResource(id = R.drawable.export_button),
            contentDescription = "Export Video",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(34.dp)
                .aspectRatio(446f / 158f)
                .bouncyClickable(pressedScale = 0.94f, hoverScale = 1.02f, onClick = onExportClick)
                .testTag("header_export_button")
        )
    }
}

@Composable
private fun GlassIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
