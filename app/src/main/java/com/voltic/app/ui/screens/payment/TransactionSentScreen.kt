package com.voltic.app.ui.screens.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SuccessGreen = Color(0xFF15A04A)
private val PendingIndigo = Color(0xFF4F5BD5)
private val FailedRed = Color(0xFFC62828)

enum class SentScreenStyle { Success, Pending, Failed }

/**
 * Full-screen "it worked" screen. Used for:
 *  - sender: transaction sent (QR / manual path)
 *  - sender: payment handed to the merchant (NFC path)
 *  - receiver: payment received (NFC broadcast done, or incoming balance detected)
 *
 * [style] lets the same screen say "not done yet" (Pending) or "didn't happen" (Failed) instead
 * of always claiming success, e.g. an NFC payment that is signed but not yet broadcast.
 */
@Composable
fun TransactionSentScreen(
    style: SentScreenStyle = SentScreenStyle.Success,
    title: String = "Transaction sent!",
    amountText: String? = null,
    usdText: String? = null,
    detail: String? = null,
    footnote: String? = null,
    doneLabel: String = "Done",
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)

    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(style) {
        progress.snapTo(0f)
        if (style != SentScreenStyle.Pending) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        progress.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
    }

    val background by animateColorAsState(
        targetValue = when (style) {
            SentScreenStyle.Success -> SuccessGreen
            SentScreenStyle.Pending -> PendingIndigo
            SentScreenStyle.Failed -> FailedRed
        },
        animationSpec = tween(durationMillis = 400),
        label = "sentScreenBackground",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        val s = 0.6f + (0.4f * progress.value)
                        scaleX = s
                        scaleY = s
                        alpha = progress.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color.White.copy(alpha = 0.22f))
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    when (style) {
                        SentScreenStyle.Success -> drawPath(
                            path = Path().apply {
                                moveTo(w * 0.28f, h * 0.52f)
                                lineTo(w * 0.44f, h * 0.67f)
                                lineTo(w * 0.73f, h * 0.36f)
                            },
                            color = Color.White,
                            style = stroke,
                        )
                        SentScreenStyle.Failed -> {
                            drawPath(
                                path = Path().apply {
                                    moveTo(w * 0.32f, h * 0.32f)
                                    lineTo(w * 0.68f, h * 0.68f)
                                },
                                color = Color.White,
                                style = stroke,
                            )
                            drawPath(
                                path = Path().apply {
                                    moveTo(w * 0.68f, h * 0.32f)
                                    lineTo(w * 0.32f, h * 0.68f)
                                },
                                color = Color.White,
                                style = stroke,
                            )
                        }
                        SentScreenStyle.Pending -> Unit
                    }
                }
                if (style == SentScreenStyle.Pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        color = Color.White,
                        strokeWidth = 6.dp,
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            amountText?.let {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )

                    usdText?.let { usd ->
                        Text(
                            text = usd,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }

            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
            }

            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = background,
            ),
        ) {
            Text(doneLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
