package vn.delfi.xcloudwms.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.delfi.xcloudwms.R

/**
 * Màn loading thương hiệu Delfi: logo + wordmark + spinner. Dùng khi mở app (splash) và
 * khi chuyển chức năng. Logo nhịp nhẹ (pulse) để báo app đang xử lý, tránh cảm giác đứng.
 */
@Composable
fun BrandLoading(
    message: String? = null,
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
) {
    val pulse = rememberInfiniteTransition(label = "brand-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brand-scale",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brand-glow",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.delfi_logo),
            contentDescription = "Delfi",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(116.dp)
                .scale(scale)
                .alpha(glow),
        )
        if (showWordmark) {
            Image(
                painter = painterResource(id = R.drawable.delfi_logo_text),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(26.dp),
            )
        }
        CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 28.dp)
                .size(28.dp),
            strokeWidth = 3.dp,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp, start = 32.dp, end = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Lớp phủ loading thương hiệu che toàn màn (dùng cho hiệu ứng chuyển chức năng). Nền đặc
 * theo theme để không lộ màn trước trong lúc chuyển.
 */
@Composable
fun BrandLoadingOverlay(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BrandLoading(message = message, showWordmark = false)
    }
}
