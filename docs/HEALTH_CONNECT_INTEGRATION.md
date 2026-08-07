# Health Connect no Liftly

## O que a camada já entrega

- Detecta se o Health Connect está disponível, ausente ou precisa ser atualizado.
- Trata cada permissão como opcional: peso, sono e exportação de exercícios são independentes.
- Lê o peso mais recente dos últimos 30 dias quando autorizado.
- Lê a sessão de sono mais recente e diferencia tempo na cama de tempo dormindo quando há estágios.
- Exporta um treino Liftly concluído como `ExerciseSessionRecord` de musculação.
- Usa `clientRecordId` estável e `clientRecordVersion` para que uma nova sincronização atualize o
  treino em vez de criar duplicatas.
- Nunca exporta treinos em modo de teste.

## Ligações obrigatórias antes de habilitar a interface

### Gradle

Usar a versão estável para a edição comercial:

```kotlin
implementation("androidx.health.connect:connect-client:1.1.0")
```

### AndroidManifest.xml

Adicionar dentro de `<manifest>`:

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />

<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>
```

Também é necessário criar uma tela real de política de privacidade/racional de permissões e
registrá-la para `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13 ou inferior) e
`android.intent.action.VIEW_PERMISSION_USAGE` (Android 14 ou superior). A política mostrada deve ser
a mesma cadastrada no Google Play Console.

A Activity já fornecida é:

```text
com.liftly.app.integration.healthconnect.PermissionsRationaleActivity
```

Exemplo de registro:

```xml
<activity
    android:name=".integration.healthconnect.PermissionsRationaleActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity>

<activity-alias
    android:name=".HealthConnectPermissionUsageActivity"
    android:exported="true"
    android:targetActivity=".integration.healthconnect.PermissionsRationaleActivity"
    android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
    <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```

### Interface

1. Criar `AndroidHealthConnectRepository(context)`.
2. Registrar
   `AndroidHealthConnectRepository.permissionRequestContract()` com
   `rememberLauncherForActivityResult`.
3. Pedir `LiftlyHealthPermissions.all` somente após uma explicação clara e uma ação do usuário.
4. Oferecer controles independentes para importar peso, consultar sono e exportar treinos.
5. Oferecer “Gerenciar no Health Connect” usando
   `AndroidHealthConnectRepository.manageHealthConnectIntent(context)`.
6. Antes de cada operação, verificar novamente as permissões; o usuário pode revogá-las a qualquer
   momento.
7. Ao finalizar uma sessão, chamar `WorkoutHealthExportMapper.prepare(session, sets)` e exportar
   apenas quando o resultado for `Ready`.

## Publicação comercial

- Declarar no Play Console exatamente os três tipos de acesso usados:
  leitura de peso, leitura de sono e escrita de exercício.
- Publicar política de privacidade antes de solicitar revisão.
- Explicar que os dados ficam no dispositivo/Health Connect e não entram em cobrança, anúncios,
  ranking ou perfil de academia sem consentimento separado.
- A integração deve continuar funcional quando o usuário negar somente uma das permissões.
- Não solicitar leitura em segundo plano ou histórico além de 30 dias: essas permissões não são
  necessárias para as funções atuais.
