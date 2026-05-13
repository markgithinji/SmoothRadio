package com.smoothradio.radio.feature.radiolist.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smoothradio.radio.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioTopBar(
    onViewToggleClick: () -> Unit,
    isGridView: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onAboutClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme

    // Get screen width
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val showInfoIcon = screenWidthDp >= 360  // Only show info icon on 360dp and above

    BackHandler(enabled = isSearchActive) {
        keyboardController?.hide()
        onSearchQueryChange("")
        onSearchActiveChange(false)
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(200)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val viewRotation by animateFloatAsState(
        targetValue = if (isGridView) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "viewRotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
        color = colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        ) + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { -it / 3 },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        ) + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(200)))
                    }
                },
                label = "topBarMode"
            ) { searchMode ->
                if (searchMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            onSearchQueryChange("")
                            onSearchActiveChange(false)
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_toolbar_back),
                                contentDescription = stringResource(R.string.top_bar_back),
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            textStyle = TextStyle(fontSize = 16.sp, color = colorScheme.onSurface),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(stringResource(R.string.top_bar_search_hint), color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                    }
                                    innerTextField()
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                        )

                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(200)),
                            exit = scaleOut(targetScale = 0.5f, animationSpec = tween(200)) + fadeOut(tween(200))
                        ) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_toolbar_close),
                                    contentDescription = stringResource(R.string.top_bar_clear),
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.app_name_caps),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onViewToggleClick) {
                                Icon(
                                    painter = painterResource(id = if (isGridView) R.drawable.ic_toolbar_list else R.drawable.ic_toolbar_grid),
                                    contentDescription = if (isGridView) stringResource(R.string.top_bar_switch_to_list) else stringResource(R.string.top_bar_switch_to_grid),
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp).graphicsLayer { rotationY = viewRotation }
                                )
                            }
                            IconButton(onClick = { onSearchActiveChange(true) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_toolbar_search),
                                    contentDescription = stringResource(R.string.top_bar_search),
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Conditionally show info icon based on screen width
                            if (showInfoIcon) {
                                IconButton(onClick = onAboutClick) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_toolbar_info),
                                        contentDescription = stringResource(R.string.top_bar_about),
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = colorScheme.outline.copy(alpha = 0.4f)
            )
        }
    }
}