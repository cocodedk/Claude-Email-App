package com.cocode.claudeemailapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.ui.theme.SignalGreen
import com.cocode.claudeemailapp.ui.theme.SignalRed

/**
 * Wraps [MessageCard] in a Material 3 swipe-to-dismiss. Swipe left (end
 * → start) schedules a delete, swipe right (start → end) schedules an
 * archive. The parent is responsible for hiding the row after the
 * callback fires — this composable just commits the swipe and emits the
 * intent.
 */
@Composable
internal fun SwipeableMessageCard(
    message: FetchedMessage,
    onOpen: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeArchive: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete(); true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeArchive(); true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("swipeable_message_card"),
        backgroundContent = { SwipeBackground(direction = dismissState.dismissDirection) }
    ) {
        MessageCard(message = message, onClick = onOpen)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, label, alignment) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> Triple(SignalRed, "Delete", Alignment.CenterEnd)
        SwipeToDismissBoxValue.StartToEnd -> Triple(SignalGreen, "Archive", Alignment.CenterStart)
        SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, "", Alignment.Center)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label.uppercase(),
                color = color,
                style = SwipeLabelStyle
            )
        }
    }
}

private val SwipeLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 2.sp
)
