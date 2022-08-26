package com.ivy.core.ui.modal

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.mediumBlur
import com.ivy.design.utils.*
import com.ivy.frp.view.navigation.Navigation
import com.ivy.frp.view.navigation.navigation
import kotlin.math.roundToInt

private const val DURATION_BACKGROUND_BLUR_ANIM = 400
const val DURATION_MODAL_ANIM = 200

@Composable
fun BoxScope.Modal(
    modalId: String,
    visible: Boolean,
    dismiss: () -> Unit,
    SecondaryActions: (@Composable () -> Unit)? = null,
    PrimaryAction: @Composable () -> Unit,
    scrollState: ScrollState? = rememberScrollState(),
    shiftIfKeyboardShown: Boolean = true,
    includeActionsRowPadding: Boolean = true,
    Content: @Composable ColumnScope.() -> Unit
) {
    val percentVisible by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(DURATION_MODAL_ANIM),
        visibilityThreshold = 0.01f
    )

    val keyboardShown = keyboardShown(modalId = modalId)
    val keyboardShownInset = keyboardInset()
    val keyboardInsetAnimated by animateDpAsState(
        targetValue = if (keyboardShown) keyboardShownInset else 0.dp,
        animationSpec = tween(DURATION_MODAL_ANIM)
    )

    // used only to hide the keyboard
    val rootView = LocalView.current

    val blurAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(DURATION_BACKGROUND_BLUR_ANIM),
        visibilityThreshold = 0.01f
    )
    if (visible || blurAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(blurAlpha)
                .background(mediumBlur())
                .testTag("modal_outside_blur")
                .clickable(
                    onClick = {
                        hideKeyboard(rootView)
                        dismiss()
                    },
                    enabled = visible
                )
                .zIndex(1000f)
        )
    }

    if (visible || percentVisible > 0.01f) {
        var actionsRowHeight by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)

                    val height = placeable.height
                    val y = height * (1 - percentVisible)

                    layout(placeable.width, height) {
                        placeable.placeRelative(
                            0,
                            y.roundToInt()
                        )
                    }
                }
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 24.dp) // 24 dp from the status bar (top)
                .background(UI.colors.pure, UI.shapes.r2Top)
                .consumeClicks() // don't close the modal when clicking on the empty space inside
                .thenIf(scrollState != null) {
                    verticalScroll(scrollState!!)
                }
                .zIndex(1000f)
        ) {
            ModalBackHandling(
                modalId = modalId,
                visible = visible,
                dismiss = dismiss
            )

            Content()

            // Bottom padding
            if (includeActionsRowPadding) {
                Spacer(Modifier.height(densityScope { actionsRowHeight.toDp() }))
            }

            if (shiftIfKeyboardShown) {
                Spacer(Modifier.height(keyboardInsetAnimated))
            }
        }

        ModalActionsRow(
            visible = visible,
            modalPercentVisible = percentVisible,
            keyboardShownInsetDp = keyboardInsetAnimated,
            onHeightChanged = {
                actionsRowHeight = it
            },
            onClose = {
                hideKeyboard(rootView)
                dismiss()
            },
            SecondaryActions = SecondaryActions,
            PrimaryAction = PrimaryAction
        )
    }
}

@Composable
private fun keyboardShown(modalId: String): Boolean {
    var keyboardOpen by remember { mutableStateOf(false) }
    val rootView = LocalView.current

    DisposableEffect(modalId) {
        val keyboardListener = {
            // check keyboard state after this layout
            val isOpenNew = isKeyboardOpen(rootView)

            // since the observer is hit quite often, only callback when there is a change.
            if (isOpenNew != keyboardOpen) {
                keyboardOpen = isOpenNew
            }
        }

        rootView.doOnLayout {
            // get initial state of keyboard
            keyboardOpen = isKeyboardOpen(rootView)

            // whenever the layout resizes/changes, callback with the state of the keyboard.
            rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
        }

        onDispose {
            // stop keyboard updates
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(keyboardListener)
        }
    }

    return keyboardOpen
}

@Composable
private fun ModalBackHandling(
    modalId: String,
    visible: Boolean,
    dismiss: () -> Unit
) {
    AddModalBackHandling(
        modalId = modalId,
        visible = visible,
        action = {
            dismiss()
        }
    )
}

@Composable
fun AddModalBackHandling(
    modalId: String,
    visible: Boolean,
    action: () -> Unit
) {
    val nav = navigation()
    DisposableEffect(visible) {
        if (visible) {
            val lastModalBackHandlingId = nav.lastModalBackHandlerId()

            if (modalId != lastModalBackHandlingId?.toString()) {
                // TODO: Handle
//                nav.modalBackHandling.add(
//                    Navigation.ModalBackHandler(
//                        id = modalId,
//                        onBackPressed = {
//                            if (visible) {
//                                action()
//                                true
//                            } else {
//                                false
//                            }
//                        }
//                    )
//                )
            }
        }

        onDispose {
            val lastModalBackHandlingId = nav.lastModalBackHandlerId()
            if (lastModalBackHandlingId.toString() == modalId) {
                removeLastBackHandlerSafe(nav)
            }
        }
    }
}

private fun removeLastBackHandlerSafe(nav: Navigation) {
    if (nav.modalBackHandling.isNotEmpty()) {
        nav.modalBackHandling.pop()
    }
}

@Composable
fun ModalActionsRow(
    visible: Boolean,
    modalPercentVisible: Float,
    keyboardShownInsetDp: Dp,

    onHeightChanged: (Int) -> Unit,

    onClose: () -> Unit,
    SecondaryActions: (@Composable () -> Unit)? = null,
    PrimaryAction: @Composable () -> Unit
) {
    val systemInsetBottom = systemInsetBottom()
    val navBarPadding by remember(modalPercentVisible) {
        derivedStateOf {
            lerp(0.dp, systemInsetBottom, modalPercentVisible)
        }
    }

    if (visible || modalPercentVisible > 0.01f) {
        val ivyContext = com.ivy.core.ui.temp.ivyWalletCtx()
        ActionsRow(
            modifier = Modifier
                .onSizeChanged {
                    onHeightChanged(it.height)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)

                    val systemOffsetBottom = keyboardShownInsetDp.toPx() + navBarPadding.toPx()
                    val visibleHeight = placeable.height * modalPercentVisible
                    val y = ivyContext.screenHeight - visibleHeight - systemOffsetBottom

                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            0,
                            y.roundToInt()
                        )
                    }
                }
                .padding(top = 8.dp, bottom = 12.dp)
                .zIndex(1100f)
        ) {
            Spacer(Modifier.width(24.dp))

            CloseButton(
                modifier = Modifier.testTag("modal_close_button"),
                onClick = onClose
            )

            SecondaryActions?.invoke()

            Spacer(Modifier.weight(1f))

            PrimaryAction()

            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
fun ActionsRow(
    modifier: Modifier = Modifier,
    lineColor: Color = UI.colors.medium,
    Content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val height = this.size.height
                val width = this.size.width

                drawLine(
                    color = lineColor,
                    strokeWidth = 2.dp.toPx(),
                    start = Offset(
                        x = 0f,
                        y = height / 2
                    ),
                    end = Offset(
                        x = width,
                        y = height / 2
                    )
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Content()
    }
}

@Composable
fun CloseButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // TODO: Implement
}

private fun hideKeyboard(view: View) {
    try {
        val imm: InputMethodManager =
            view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    } catch (ignore: Exception) {
    }
}

/**
 * @return system's bottom inset (nav buttons or bottom nav)
 */
@Composable
private fun systemInsetBottom(): Dp {
    val rootView = LocalView.current
    val densityScope = LocalDensity.current
    return remember(rootView) {
        val insetPx =
            WindowInsetsCompat.toWindowInsetsCompat(rootView.rootWindowInsets, rootView)
                .getInsets(WindowInsetsCompat.Type.navigationBars())
                .bottom
        with(densityScope) { insetPx.toDp() }
    }
}

@Composable
private fun keyboardInset(): Dp {
    val rootView = LocalView.current
    val densityScope = LocalDensity.current
    return remember(rootView) {
        val insetPx =
            WindowInsetsCompat.toWindowInsetsCompat(rootView.rootWindowInsets, rootView)
                .getInsets(WindowInsetsCompat.Type.ime())
                .bottom
        with(densityScope) { insetPx.toDp() }
    }
}