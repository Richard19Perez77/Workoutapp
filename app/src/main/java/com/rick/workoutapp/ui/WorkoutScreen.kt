package com.rick.workoutapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rick.workoutapp.R
import com.rick.workoutapp.ui.theme.WorkoutappTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    var isRunning by remember { mutableStateOf(false) }
    var steps by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isActive) {
            delay(1_000.milliseconds)
            elapsedSeconds += 1
            steps += 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.machine_step),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = steps.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.steps_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formatElapsed(elapsedSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = {
                        steps = 0
                        elapsedSeconds = 0L
                        isRunning = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(text = stringResource(R.string.start_workout))
                }
            } else {
                OutlinedButton(
                    onClick = { isRunning = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(text = stringResource(R.string.stop_workout))
                }
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    WorkoutappTheme {
        WorkoutScreen()
    }
}
