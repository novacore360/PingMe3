package com.messagingapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.messagingapp.service.MessageNotificationService
import com.messagingapp.ui.explore.ExploreScreen
import com.messagingapp.ui.explore.ExploreViewModel
import com.messagingapp.ui.messages.*
import com.messagingapp.ui.profile.ProfileScreen
import com.messagingapp.ui.profile.ProfileViewModel
import com.messagingapp.ui.theme.*
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

sealed class Screen(val route: String) {
    object Messages : Screen("messages")
    object Chat : Screen("chat/{conversationId}/{userId}") {
        fun go(convId: String, userId: String) = "chat/$convId/$userId"
    }
    object Explore : Screen("explore")
    object Profile : Screen("profile")
}

data class NavItem(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val messagesVm: MessagesViewModel = viewModel()
    val exploreVm: ExploreViewModel = viewModel()
    val profileVm: ProfileViewModel = viewModel()

    // Request notification permission
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val svc = Intent(context, MessageNotificationService::class.java)
            ContextCompat.startForegroundService(context, svc)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val svc = Intent(context, MessageNotificationService::class.java)
                ContextCompat.startForegroundService(context, svc)
            }
        } else {
            val svc = Intent(context, MessageNotificationService::class.java)
            ContextCompat.startForegroundService(context, svc)
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
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                GlassBottomBar(
                    items = navItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Messages.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackgroundGradient)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Messages.route,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                composable(Screen.Messages.route) {
                    MessagesListScreen(
                        viewModel = messagesVm,
                        onOpenChat = { convId, userId ->
                            navController.navigate(Screen.Chat.go(convId, userId))
                        }
                    )
                }

                composable(
                    route = Screen.Chat.route,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("userId") { type = NavType.StringType }
                    )
                ) { backStack ->
                    val convId = backStack.arguments?.getString("conversationId") ?: return@composable
                    val userId = backStack.arguments?.getString("userId") ?: return@composable
                    ChatScreen(
                        conversationId = convId,
                        otherUserId = userId,
                        viewModel = messagesVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Explore.route) {
                    ExploreScreen(
                        viewModel = exploreVm,
                        onOpenChat = { convId, userId ->
                            navController.navigate(Screen.Chat.go(convId, userId))
                        }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = profileVm,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
fun GlassBottomBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x26FFFFFF), Color(0x10FFFFFF))
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavBarItem(
                    item = item,
                    selected = selected,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
fun NavBarItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) GlassColors.primaryGlass else Color.Transparent
    val iconColor = if (selected) GlassColors.primary else GlassColors.textTertiary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .then(
                if (selected) Modifier.border(0.5.dp, GlassColors.primary.copy(0.3f), RoundedCornerShape(16.dp))
                else Modifier
            )
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (selected) item.iconSelected else item.iconUnselected,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        AnimatedVisibility(visible = selected) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}
