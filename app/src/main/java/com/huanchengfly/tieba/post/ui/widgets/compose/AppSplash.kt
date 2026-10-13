package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.R
import androidx.core.graphics.drawable.toBitmap
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动屏就绪信号：首页数据加载完成后置位，启动屏随之淡出。
 */
object SplashState {
    val ready = MutableStateFlow(false)

    /** 启动屏是否已结束（退场触发）——供启动静默检查等待，避免弹窗被遮挡。 */
    val finished = MutableStateFlow(false)

    fun markReady() {
        ready.value = true
    }

    fun reset() {
        ready.value = false
        finished.value = false
    }
}

/** 启动屏字体:Roboto(Google Material 官方字体) */
private val SplashFontFamily = FontFamily(
    Font(R.font.roboto_regular),
    Font(R.font.roboto_medium, FontWeight.Medium),
)

/**
 * 应用内启动屏（一镜到底流体编排）：
 * 1. 初始阶段（0~420ms）：Logo 严格处于屏幕物理中心（160dp 满态），零偏差承接系统启动屏动效。
 * 2. 一镜到底升维（420~1000ms）：Logo 平滑上浮（0dp -> -96dp）并等比缩放（160dp -> 96dp），成为 Loading 加载页的头部图标。
 * 3. 内容梯次随动（540~1000ms）：随着 Logo 腾出空间，标语（见闻生活新知）、FluxTie 应用名与液态 Blob Loading 顺势跟进滑入展现。
 * 4. 退场交接：首页数据就绪后，整体品牌面板轻柔向上浮升淡出，底栏与 Feed 流迎面浮现。
 */
@Composable
fun AppSplashOverlay() {
    var visible by remember { mutableStateOf(true) }
    var isHeaderStage by remember { mutableStateOf(false) }

    // 1. 一镜到底核心动效：Logo 充分向上浮升（-120dp）并在空间上等比缩放至 88dp 精致头部徽标
    val logoTranslateY by animateDpAsState(
        targetValue = if (isHeaderStage) (-120).dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "logoTranslateY"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (isHeaderStage) 0.55f else 1.0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    // 2. 标语、应用名与 Loading 随动滑入与淡入
    val contentRevealAlpha by animateFloatAsState(
        targetValue = if (isHeaderStage) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 440, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "contentRevealAlpha"
    )
    val contentRevealSlide by animateDpAsState(
        targetValue = if (isHeaderStage) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 480, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "contentRevealSlide"
    )

    // 3. 整体退场过渡（开门交接）
    val transition = updateTransition(targetState = visible, label = "splashExit")
    val bgAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = LinearEasing) },
        label = "splashBgAlpha"
    ) { if (it) 1f else 0f }
    val exitAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 220, easing = EaseInCubic) },
        label = "splashExitAlpha"
    ) { if (it) 1f else 0f }
    val exitScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
        label = "splashExitScale"
    ) { if (it) 1f else 0.96f }
    val exitTranslateY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
        label = "splashExitTranslateY"
    ) { if (it) 0.dp else (-18).dp }

    LaunchedEffect(Unit) {
        SplashState.reset()
        val start = System.currentTimeMillis()
        // 约 420ms 后无缝承接系统启动屏动效终态，启动一镜到底上浮
        delay(420)
        isHeaderStage = true

        // 循环播放直到：加载完成且达到最短展示时长（1500ms 保证一镜到底完整呈现）；3.5s 超时兜底
        val minDuration = 1500L
        val timeout = 3500L
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            if ((SplashState.ready.value && elapsed >= minDuration) || elapsed >= timeout) break
            delay(100)
        }
        visible = false
        SplashState.finished.value = true
    }

    if (bgAlpha > 0.01f) {
        val splashBg = colorResource(id = R.color.colorSplashBg)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha }
                .background(splashBg)
        ) {
            // 核心应用图标：初始 160dp 绝对居中，随着一镜到底上移至头部（-120dp）并等比缩放至 88dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationY = (logoTranslateY + exitTranslateY).toPx()
                        scaleX = logoScale * exitScale
                        scaleY = logoScale * exitScale
                        alpha = exitAlpha
                    }
            ) {
                SplashBrandIcon(size = 160.dp)
            }

            // 品牌文案与 Loading：在 Logo 上浮过程中顺势跟随滑入展开，与 Logo 底部保持舒适通透间距
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 36.dp)
                    .graphicsLayer {
                        translationY = (contentRevealSlide + exitTranslateY).toPx()
                        alpha = contentRevealAlpha * exitAlpha
                        scaleX = exitScale
                        scaleY = exitScale
                    }
            ) {
                Text(
                    text = stringResource(id = R.string.splash_tagline),
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    fontFamily = SplashFontFamily,
                    color = ExtendedTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "FluxTie",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    fontFamily = SplashFontFamily,
                    color = ExtendedTheme.colors.text
                )
                Spacer(modifier = Modifier.height(30.dp))
                BlobLoading()
            }

            // 底部版本号
            Text(
                text = stringResource(id = R.string.summary_about_version, BuildConfig.VERSION_NAME),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SplashFontFamily,
                color = ExtendedTheme.colors.textSecondary.copy(alpha = exitAlpha),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

/**
 * 启动屏品牌图标：严格保持与系统 SplashScreen 规范（160dp 核心圆底）像素级一致。
 * 强制圆形裁剪（CircleShape）+ ic_launcher_new_background 品牌底色 + ic_launcher_new_foreground 矢量前景。
 * 彻底消除在 HyperOS / OneUI 等自适应桌面图标下，从系统启动屏圆形跳变为桌面圆角矩形（Squircle）的「圆角跳闪」。
 */
@Composable
private fun SplashBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorResource(id = R.color.ic_launcher_new_background)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_new_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}


/**
 * 液态 blob Loading：8 点谐波半径驱动闭合曲线持续流动形变（液滴/水球效果），
 * 叠加光泽渐变。零额外依赖，Canvas 手绘。
 */
@Composable
private fun BlobLoading() {
    val transition = rememberInfiniteTransition(label = "blob")
    // 相位 0..2π 线性推进,双频谐波叠加产生非重复的流动形变
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing)
        ),
        label = "blobPhase"
    )
    val sheen by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blobSheen"
    )
    val base = ExtendedTheme.colors.primary
    Canvas(modifier = Modifier.size(36.dp)) {
        drawLiquidBlob(
            phase = phase,
            brush = Brush.linearGradient(
                colors = listOf(base, lerpColor(base, Color.White, 0.22f * sheen))
            )
        )
    }
}

private fun DrawScope.drawLiquidBlob(phase: Float, brush: Brush) {
    val points = 8
    val radius = size.minDimension / 2f * 0.84f
    val vertices = (0 until points).map { i ->
        val angle = 2.0 * PI * i / points
        val wobble = 0.10f * sin(phase * 1.0f + i * 0.9f) +
                0.06f * sin(phase * 1.7f + i * 1.9f)
        val r = radius * (1f + wobble)
        Offset(
            center.x + (cos(angle) * r).toFloat(),
            center.y + (sin(angle) * r).toFloat()
        )
    }
    // 平滑闭合曲线:相邻点中点作为锚,顶点作为控制点
    val path = Path()
    val mid = { a: Offset, b: Offset -> Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f) }
    val firstMid = mid(vertices[0], vertices[1])
    path.moveTo(firstMid.x, firstMid.y)
    for (i in 1..points) {
        val control = vertices[i % points]
        val anchor = mid(control, vertices[(i + 1) % points])
        path.quadraticTo(control.x, control.y, anchor.x, anchor.y)
    }
    path.close()
    drawPath(path, brush)
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color =
    Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
