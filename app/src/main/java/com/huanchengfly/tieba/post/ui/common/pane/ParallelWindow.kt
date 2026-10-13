package com.huanchengfly.tieba.post.ui.common.pane

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigate
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.page.NavGraphs
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPage
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.spec.NavHostEngine
import com.ramcosta.composedestinations.utils.toDestinationsNavigator

/**
 * 平行视窗（大屏双栏）容器。
 *
 * FluxTie 是单 Activity + Compose 导航架构，系统级平行视窗（小米 Magic Window / 华为平行视界）
 * 依赖多个 Activity 配对，在此架构下不会生效。因此这里在应用内自行实现「列表 - 详情」双栏：
 * 左侧沿用原有 DestinationsNavHost 作为列表栈，右侧用一个独立的 NavHost 承载帖子详情。
 *
 * 生效条件：宽度尺寸类 >= Expanded（>= 840dp）且用户在设置中开启（默认开启）。
 * 竖屏、窄屏、手机完全不受影响。
 */
private const val PANE_EMPTY_ROUTE = "pane_empty"
private const val PANE_THREAD_ROUTE = "pane_thread"
private const val PANE_THREAD_FULL_ROUTE =
    "$PANE_THREAD_ROUTE/{threadId}?forumId={forumId}&postId={postId}&seeLz={seeLz}&sortType={sortType}&from={from}"

private val prefParallelWindow = booleanPreferencesKey("parallel_window")

private const val DEFAULT_LIST_WEIGHT = 0.42f
private const val MIN_LIST_WEIGHT = 0.3f
private const val MAX_LIST_WEIGHT = 0.7f

@Composable
fun ParallelWindowNavHost(
    navController: NavHostController,
    engine: NavHostEngine,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val parallelWindowEnabled by rememberPreferenceAsState(
        key = prefParallelWindow,
        defaultValue = true
    )
    val twoPane = parallelWindowEnabled &&
        windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Expanded

    if (!twoPane) {
        DestinationsNavHost(
            navGraph = NavGraphs.root,
            navController = navController,
            engine = engine,
            modifier = modifier,
        )
        return
    }

    val detailNavController = rememberNavController()
    val threadBaseRoute = remember { ThreadPageDestination.route.substringBefore("?") }

    DisposableEffect(navController, detailNavController, threadBaseRoute) {
        val listener =
            NavController.OnDestinationChangedListener { _, destination, arguments ->
                val route = destination.route ?: return@OnDestinationChangedListener
                if (!route.startsWith(threadBaseRoute)) return@OnDestinationChangedListener
                val bundle = arguments ?: return@OnDestinationChangedListener
                val threadId = bundle.getLong("threadId", 0L).takeIf { it != 0L }
                    ?: return@OnDestinationChangedListener
                detailNavController.navigate(
                    buildPaneThreadRoute(
                        threadId = threadId,
                        forumId = bundle.getLong("forumId", 0L),
                        postId = bundle.getLong("postId", 0L),
                        seeLz = bundle.getBoolean("seeLz", false),
                        sortType = bundle.getInt("sortType", 0),
                        from = bundle.getString("from").orEmpty(),
                    )
                ) {
                    launchSingleTop = true
                }
                navController.popBackStack()
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val detailEntry by detailNavController.currentBackStackEntryAsState()
    val detailOpened = detailEntry?.destination?.route?.startsWith(PANE_THREAD_ROUTE) == true
    BackHandler(enabled = detailOpened) {
        detailNavController.popBackStack()
    }

    var listWeight by remember { mutableFloatStateOf(DEFAULT_LIST_WEIGHT) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }.takeIf { it > 0f } ?: 1f
    }

    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(listWeight)
        ) {
            DestinationsNavHost(
                navGraph = NavGraphs.root,
                navController = navController,
                engine = engine,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(10.dp)
                .pointerInput(windowWidthPx) {
                    detectDragGestures { _, dragAmount ->
                        listWeight =
                            (listWeight + dragAmount.x / windowWidthPx)
                                .coerceIn(MIN_LIST_WEIGHT, MAX_LIST_WEIGHT)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(ExtendedTheme.colors.divider)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f - listWeight)
        ) {
            DetailPaneHost(
                navController = navController,
                detailNavController = detailNavController,
            )
        }
    }
}

@Composable
private fun DetailPaneHost(
    navController: NavHostController,
    detailNavController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val destinationsNavigator = remember(navController) { navController.toDestinationsNavigator() }
    NavHost(
        navController = detailNavController,
        startDestination = PANE_EMPTY_ROUTE,
        modifier = modifier,
    ) {
        composable(PANE_EMPTY_ROUTE) {
            EmptyDetailPane()
        }
        composable(
            route = PANE_THREAD_FULL_ROUTE,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("forumId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("postId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("seeLz") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("sortType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("from") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val args = entry.arguments ?: return@composable
            ThreadPage(
                threadId = args.getLong("threadId"),
                navigator = destinationsNavigator,
                forumId = args.getString("forumId")?.toLongOrNull()?.takeIf { it != 0L },
                postId = args.getString("postId")?.toLongOrNull() ?: 0L,
                seeLz = args.getString("seeLz")?.toBoolean() ?: false,
                sortType = args.getString("sortType")?.toIntOrNull() ?: 0,
                from = args.getString("from").orEmpty(),
            )
        }
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExtendedTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "从左侧打开一个帖子",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun buildPaneThreadRoute(
    threadId: Long,
    forumId: Long,
    postId: Long,
    seeLz: Boolean,
    sortType: Int,
    from: String,
): String {
    return "$PANE_THREAD_ROUTE/$threadId?forumId=$forumId&postId=$postId&seeLz=$seeLz" +
        "&sortType=$sortType&from=${Uri.encode(from)}"
}
