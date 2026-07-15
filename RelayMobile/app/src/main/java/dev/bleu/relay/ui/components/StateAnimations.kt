package dev.bleu.relay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*

@Composable
fun LottieStateAnimation(
    modifier: Modifier = Modifier,
    animationUrl: String? = null,
    rawRes: Int? = null,
    iterations: Int = LottieConstants.IterateForever,
    fallback: @Composable () -> Unit = {}
) {
    if (animationUrl != null || rawRes != null) {
        val compositionSpec = if (rawRes != null) {
            LottieCompositionSpec.RawRes(rawRes)
        } else {
            LottieCompositionSpec.Url(animationUrl!!)
        }
        
        val composition by rememberLottieComposition(compositionSpec)
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = iterations,
        )

        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = modifier
            )
        } else {
            fallback()
        }
    } else {
        fallback()
    }
}

@Composable
fun AnimatedEmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.8f, animationSpec = tween(500)),
        exit = fadeOut() + scaleOut()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "bounce")
            val yOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce"
            )

            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .offset(y = yOffset.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnimatedLoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieStateAnimation(
            modifier = Modifier.size(100.dp),
            animationUrl = "https://assets3.lottiefiles.com/packages/lf20_p8bfn5to.json", // Example loading
            fallback = {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AnimatedSuccessState(
    message: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.5f, animationSpec = tween(600, easing = LinearOutSlowInEasing)),
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LottieStateAnimation(
                modifier = Modifier.size(120.dp),
                animationUrl = "https://assets9.lottiefiles.com/packages/lf20_touohxv0.json", // Example success
                fallback = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnimatedErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LottieStateAnimation(
                modifier = Modifier.size(100.dp),
                animationUrl = "https://assets10.lottiefiles.com/packages/lf20_pnx65td0.json", // Example error
                iterations = 1,
                fallback = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
