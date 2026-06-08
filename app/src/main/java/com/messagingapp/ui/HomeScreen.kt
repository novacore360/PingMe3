package com.messagingapp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.messagingapp.service.ChatBubbleService
import com.messagingapp.service.MessageNotificationService
import com.messagingapp.ui.explore.ExploreScreen
import com.messagingapp.ui.explore.ExploreViewModel
import com.messagingapp.ui.messages.*
import com.messagingapp.ui.profile.ProfileScreen
import com.messagingapp.ui.profile.ProfileViewModel
import com.messagingapp.ui.theme.*

sealed class Screen(val route: String) {
    object Messages : Screen("messages")
    object Chat : Screen("chat/{conversationId}/{userId}") {
        fun go(convId: String, userId: String) = "chat/$convId/$userId"
    }
    object Explore : Screen("explore")
    object Profile : Screen("profile")
}

data class NavItem(
    val route: String, val label: String,
    val iconSelected: ImageVector, val iconUnselected: ImageVector
)

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val messagesVm: MessagesViewModel = viewModel()
    val exploreVm: ExploreViewModel = viewModel()
    val profileVm: ProfileViewModel = viewModel()

    val messagesListState by messagesVm.listState.collectAsState()
    val totalUnread = messagesListState.totalUnread

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        ContextCompat.startForegroundService(context, Intent(context, MessageNotificationService::class.java))
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(context, Intent(context, ChatBubbleService::class.java))
        }
    }

    LaunchedEffect(Unit) {
        // Start notification service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ContextCompat.startForegroundService(context, Intent(context, MessageNotificationService::class.java))
        }

        // Start bubble service (request overlay permission if needed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            overlayLauncher.launch(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ))
        } else {
            ContextCompat.startForegroundService(context, Intent(context, ChatBubbleService::class.java))
        }
    }

    val navItems = listOf(
        NavItem(Screen.Messages.route, "Messages", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubble),
        NavItem(Screen.Explore.route, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
        NavItem(Screen.Profile.route, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in navItems.map { it.route }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it }, exit = slideOutVertically { it }
            ) {
                GlassBottomBar(
                    items = navItems,
                    currentRoute = currentRoute,
                    totalUnread = totalUnread,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Messages.route) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize().background(AppBackgroundGradient)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Messages.route,
                modifier = Modifier.fillMaxSize().systemBarsPadding()
            ) {
                composable(Screen.Messages.route) {
                    MessagesListScreen(viewModel = messagesVm, onOpenChat = { convId, userId ->
                        navController.navigate(Screen.Chat.go(convId, userId))
                    })
                }
                composable(
                    Screen.Chat.route,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("userId") { type = NavType.StringType }
                    )
                ) { back ->
                    val convId = back.arguments?.getString("conversationId") ?: return@composable
                    val userId = back.arguments?.getString("userId") ?: return@composable
                    ChatScreen(conversationId = convId, otherUserId = userId,
                        viewModel = messagesVm, onBack = { navController.popBackStack() })
                }
                composable(Screen.Explore.route) {
                    ExploreScreen(viewModel = exploreVm, onOpenChat = { convId, userId ->
                        navController.navigate(Screen.Chat.go(convId, userId))
                    })
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(viewModel = profileVm, onLogout = {
                        runCatching {
                            context.stopService(Intent(context, ChatBubbleService::class.java))
                            context.stopService(Intent(context, MessageNotificationService::class.java))
                        }
                        onLogout()
                    })
                }
            }
        }
    }
}

@Composable
fun GlassBottomBar(
    items: List<NavItem>,
    currentRoute: String?,
    totalUnread: Int,
    onNavigate: (String) -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth().height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xEA060A14))
                .border(1.dp, Brush.linearGradient(listOf(Color(0x50FFFFFF), Color(0x0AFFFFFF))), RoundedCornerShape(32.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                // Badge only on Messages tab
                val badge = if (item.route == Screen.Messages.route && totalUnread > 0) totalUnread else 0
                NavBarItem(item = item, selected = selected, badge = badge, onClick = { onNavigate(item.route) })
            }
        }
    }
}

@Composable
fun NavBarItem(item: NavItem, selected: Boolean, badge: Int, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier.height(40.dp).wrapContentWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(GlassColors.primary.copy(0.25f), GlassColors.accent.copy(0.15f))))
                    .border(0.5.dp, GlassColors.primary.copy(0.4f), RoundedCornerShape(20.dp))
            )
        }
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon with badge
            Box {
                Icon(
                    if (selected) item.iconSelected else item.iconUnselected,
                    item.label,
                    tint = if (selected) GlassColors.primary else GlassColors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
                if (badge > 0) {
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 14.dp).height(14.dp)
                            .align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                            .background(GlassColors.error, RoundedCornerShape(7.dp))
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Color.White
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = GlassColors.primary, maxLines = 1)
            }
        }
    }
}
