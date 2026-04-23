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
import androidx.compose.runtime.LaunchedEffect
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
import com.cocode.claudeemailapp.data.Conversation
import com.cocode.claudeemailapp.ui.theme.SignalCyan
import com.cocode.claudeemailapp.ui.theme.SignalGreen

/**
 * Wraps [ConversationCard] with a one-direction swipe gesture.
 * Swipe left commits an archive toggle: in the active/waiting view it
 * archives, in the archived view it unarchives.
 */
@Composable
internal fun SwipeableConversationCard(
    conversation: Conversation,
    inArchivedView: Boolean,
    onOpen: () -> Unit,
    onArchiveToggle: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                onArchiveToggle(); true
            } else false
        }
    )
    LaunchedEffect(conversation.id) { state.reset() }

    SwipeToDismissBox(
        state = state,
        modifier = Modifier.testTag("swipeable_conversation_card"),
        backgroundContent = { SwipeBackground(label = if (inArchivedView) "Unarchive" else "Archive") },
        enableDismissFromStartToEnd = false
    ) {
        ConversationCard(conversation = conversation, onClick = onOpen)
    }
}

@Composable
private fun SwipeBackground(label: String) {
    val color = if (label == "Unarchive") SignalCyan else SignalGreen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(text = label.uppercase(), color = color, style = SwipeLabelStyle)
    }
}

private val SwipeLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 2.sp
)
