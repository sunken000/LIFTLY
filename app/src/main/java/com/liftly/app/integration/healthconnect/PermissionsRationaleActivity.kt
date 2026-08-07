package com.liftly.app.integration.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftly.app.ui.theme.LiftlyTheme

/**
 * Privacy rationale opened by Health Connect itself.
 *
 * The manifest must register this Activity for both the legacy rationale action and the Android
 * 14+ permission-usage alias. See docs/HEALTH_CONNECT_INTEGRATION.md.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiftlyTheme(themeMode = "Preto") {
                HealthPermissionsRationale(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun HealthPermissionsRationale(
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Privacidade e Health Connect",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "A conexão é opcional. O Liftly continua funcionando normalmente sem acesso aos seus dados de saúde.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RationaleSection(
                title = "Peso",
                body = "Com sua autorização, o Liftly lê somente o peso mais recente para manter o perfil e os cálculos atualizados. O app não altera medições de peso no Health Connect.",
            )
            RationaleSection(
                title = "Sono",
                body = "Com sua autorização, o Liftly lê a sessão de sono mais recente para contextualizar recuperação e prontidão. Nenhum registro de sono é criado ou alterado.",
            )
            RationaleSection(
                title = "Treinos",
                body = "Ao finalizar um treino real, o Liftly pode gravar a sessão de musculação no Health Connect. Treinos em modo de teste nunca são exportados.",
            )
            RationaleSection(
                title = "Controle dos seus dados",
                body = "As permissões podem ser concedidas separadamente e revogadas a qualquer momento nas configurações do Health Connect. O Liftly verifica novamente a autorização antes de cada operação.",
            )
            RationaleSection(
                title = "Uso responsável",
                body = "Dados obtidos do Health Connect não são vendidos nem usados para publicidade. Eles não são compartilhados com academias, treinadores ou outros usuários sem uma ação e um consentimento separados.",
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Entendi")
            }
        }
    }
}

@Composable
private fun RationaleSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
