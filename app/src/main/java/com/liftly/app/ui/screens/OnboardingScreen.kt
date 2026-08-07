package com.liftly.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftly.app.R
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.components.GradientActionButton

private data class OnboardingPage(val title: String, val text: String, val icon: ImageVector)

@Composable
fun OnboardingScreen(onFinish: (Boolean) -> Unit) {
    val pages = listOf(
        OnboardingPage("Treine com clareza", "Monte seus treinos, registre cada série e mantenha o foco no que importa.", Icons.Default.FitnessCenter),
        OnboardingPage("Sua semana organizada", "Associe treinos aos dias, planeje descansos e ajuste a rotina em poucos toques.", Icons.Default.CalendarMonth),
        OnboardingPage("Evolução que você vê", "Acompanhe cargas, volume, frequência, recordes e peso corporal — tudo no dispositivo.", Icons.Default.Insights)
    )
    var page by remember { mutableIntStateOf(0) }
    var demo by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFinish(false) }) { Text("Pular") }
            }
            if (page == 0) {
                Image(
                    painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "Liftly",
                    modifier = Modifier.size(156.dp).clip(RoundedCornerShape(36.dp)),
                    contentScale = ContentScale.Fit
                )
            } else Spacer(Modifier.height(156.dp))
            Spacer(Modifier.height(32.dp))
            AnimatedContent(page, label = "onboarding") { index ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        NeonIcon(pages[index].icon, null, selected = true, intensity = 1.3f, size = 48.dp)
                    }
                    Text(
                        pages[index].title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(pages[index].text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index -> Box(Modifier.size(if (page == index) 24.dp else 8.dp, 8.dp).clip(CircleShape).background(if (page == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
            }
            if (page == pages.lastIndex) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(demo, { demo = it })
                    Text("Adicionar um treino de demonstração", Modifier.weight(1f))
                }
            } else Spacer(Modifier.height(56.dp))
        }
        GradientActionButton(
            onClick = { if (page < pages.lastIndex) page++ else onFinish(demo) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(54.dp),
            onClickLabel = if (page < pages.lastIndex) "Continuar introdução" else "Começar a usar o Liftly",
        ) { Text(if (page < pages.lastIndex) "Continuar" else "Começar") }
    }
}
