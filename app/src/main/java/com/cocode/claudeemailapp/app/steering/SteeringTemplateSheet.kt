package com.cocode.claudeemailapp.app.steering

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.ui.theme.BorderSoft
import com.cocode.claudeemailapp.ui.theme.BorderStrong
import com.cocode.claudeemailapp.ui.theme.Graphite
import com.cocode.claudeemailapp.ui.theme.Snow
import com.cocode.claudeemailapp.ui.theme.SnowMuted

@Composable
fun SteeringTemplateSheet(
    onTemplateTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetShape = RoundedCornerShape(18.dp)
    val continueText = stringResource(R.string.template_continue)
    val abortText = stringResource(R.string.template_abort)
    val explainText = stringResource(R.string.template_explain)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(sheetShape)
            .background(Graphite, sheetShape)
            .border(BorderStroke(1.dp, BorderStrong), sheetShape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("steering_template_sheet"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.steering_templates_heading).uppercase(),
            color = SnowMuted,
            style = SheetHeadingStyle,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        TemplateButton(
            text = continueText,
            onClick = { onTemplateTap(continueText) },
            modifier = Modifier.testTag("steering_template_continue")
        )
        TemplateButton(
            text = abortText,
            onClick = { onTemplateTap(abortText) },
            modifier = Modifier.testTag("steering_template_abort")
        )
        TemplateButton(
            text = explainText,
            onClick = { onTemplateTap(explainText) },
            modifier = Modifier.testTag("steering_template_explain")
        )
    }
}

@Composable
private fun TemplateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = "\u201C $text \u201D",
        style = TemplateTextStyle,
        color = Snow,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, BorderSoft), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private val SheetHeadingStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 9.5.sp,
    letterSpacing = 1.8.sp
)

private val TemplateTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
)
