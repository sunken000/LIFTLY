# Backup e privacidade

## Política local de privacidade

O Liftly funciona sem conta e sem servidor próprio. Treinos, séries, cargas, programação, peso corporal, metas, perfil e referências das fotos são processados e armazenados localmente no dispositivo.

Na implementação atual:

- a permissão normal `INTERNET` é usada pela exportação opcional de resumos ao Discord e, somente quando a aba Música é aberta ou atualizada, para consultar a configuração pública da playlist e o endpoint oEmbed oficial do Spotify;
- `POST_NOTIFICATIONS` é solicitada no Android 13 ou superior quando o usuário inicia/retoma um treino, para mostrar exercício atual e descanso fora do app;
- `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE` mantêm esse acompanhamento explicitamente visível enquanto o treino está ativo;
- `MODIFY_AUDIO_SETTINGS`, uma permissão normal sem diálogo ao usuário, permite solicitar e devolver foco de áudio somente durante o teste/alarme opcional;
- a permissão normal `VIBRATE` é usada somente no dispositivo para avisar o fim do descanso, quando habilitada;
- o alarme opcional de fim do descanso usa o fluxo de áudio de mídia do Android, respeita o volume/rota ativa (incluindo fones) e solicita foco transitório com ducking somente durante seus 1–10 segundos, para reduzir temporariamente outra mídia;
- a permissão normal `WAKE_LOCK` mantém a contagem de um descanso ativo confiável com a tela apagada e, quando o som está ligado, o alerta pelos 1–10 segundos escolhidos; ambos os locks têm timeout e são liberados ao concluir, pular ou parar o som;
- as calorias estimadas são calculadas localmente a partir do peso, duração e tipos de exercício já registrados; o valor não é uma medição clínica;
- não há SDK de anúncios, telemetria ou analytics;
- a integração com Discord nasce desligada. Se o usuário salvar um webhook e ativá-la, cada treino real finalizado envia nome, duração, séries, repetições, cargas, RIR/dor, volume e calorias estimadas ao canal escolhido; modo teste e fotos não são enviados;
- o webhook fica no DataStore e, durante uma tentativa confiável, no banco privado do WorkManager. Não é logado, não entra no JSON e é apagado com os demais dados;
- a integração de música não pede login no Liftly nem recebe senha, token, e-mail, biblioteca pessoal ou histórico de reprodução. Ela recebe somente um JSON público com o ID da playlist e solicita ao oEmbed do Spotify o título e a URL temporária da capa; a imagem não é persistida em disco pelo Liftly;
- tocar em **OUVIR NO SPOTIFY** transfere o usuário para o aplicativo ou site oficial, que passa a operar segundo os termos e a política de privacidade do Spotify;
- a última configuração pública válida e seu `ETag` podem ficar no armazenamento privado para manter a tela funcional sem internet; esse cache não contém dados do usuário;
- o backup automático do Android está desativado por `android:allowBackup="false"` e `android:fullBackupContent="false"`;
- foto de perfil, fotos de evolução, exportação e importação usam somente arquivos escolhidos no seletor de documentos do Android, sem acesso amplo ao armazenamento;
- o usuário pode excluir os dados pessoais persistidos no Room pela tela Perfil e saúde.

Esta política descreve o comportamento do código desta versão. Qualquer distribuição que adicione serviços de rede, telemetria ou bibliotecas de terceiros deve revisar o manifesto, a tela de privacidade e este documento.

## Proteção local

O banco e o DataStore ficam no armazenamento privado da aplicação, protegido pelo sandbox do Android. O banco Room não usa criptografia adicional e o aplicativo não oferece PIN ou bloqueio biométrico próprio.

O arquivo JSON exportado também não é criptografado. Ele pode conter dados corporais e histórico de treino. O usuário deve armazená-lo em local confiável, evitar compartilhamento indevido e excluí-lo quando não for mais necessário.

## Exportar backup

Na aba **Perfil e saúde**:

1. abra **Exportar backup**;
2. escolha nome e destino no seletor de documentos;
3. confirme a criação do arquivo JSON.

O nome sugerido segue `liftly-backup-AAAA-MM-DD.json`. O app usa `ActivityResultContracts.CreateDocument("application/json")`, portanto não precisa de permissão ampla de armazenamento.

O objeto raiz contém:

```json
{
  "schemaVersion": 4,
  "exportedAt": 0,
  "app": "Liftly",
  "exercises": [],
  "workouts": [],
  "workoutExercises": [],
  "schedule": [],
  "sessions": [],
  "sessionSets": [],
  "weights": [],
  "bodyPhotos": [],
  "profile": null
}
```

Conteúdo exportado:

- exercícios personalizados;
- exercícios nativos marcados como favoritos;
- treinos e itens ordenados;
- programação por data;
- sessões e séries, incluindo ordem, cargas, comparações planejado/realizado, RIR e dor;
- histórico de peso;
- metadados das fotos de evolução (URI, data e nota), sem incorporar a imagem;
- perfil.

Exercícios nativos não favoritos são omitidos porque o catálogo é recriado pelo aplicativo. Preferências do DataStore — onboarding, tema, hápticos, cronômetro, alarme, metas/notificações, demo, filtros, foto de perfil e webhook/ativação do Discord — não são incluídas.

Sessões em modo teste e suas séries nunca são exportadas. Ao encerrar o teste, elas também são removidas do banco local em uma única transação.

Cada `imageUri` de `bodyPhotos` guarda somente uma referência URI; o arquivo de imagem não é incorporado ao JSON. A foto de perfil nem sequer entra no backup. Referências de imagem podem não funcionar em outro dispositivo ou depois da perda da permissão concedida ao documento.

## Importar backup

1. Em **Perfil e saúde**, toque em **Importar backup JSON**.
2. Escolha um arquivo `application/json` ou texto pelo seletor do Android.
3. Aguarde o feedback de sucesso ou erro.

A importação aceita `schemaVersion` de 1 a 4. Backups antigos são migrados de forma compatível; séries sem snapshots planejados/RIR continuam disponíveis, mas sem uma comparação retroativa inventada. A operação ocorre em uma transação Room e substitui os dados pessoais atuais:

1. limpa séries, sessões, programação, treinos, pesos, fotos, perfil, personalizados e favoritos;
2. reinsere o catálogo nativo;
3. recria os registros do JSON na ordem necessária para as chaves estrangeiras.

Não existe mesclagem, prévia de diferenças ou recuperação automática do estado anterior. Exporte um backup atual antes de importar outro arquivo. JSON malformado, incompleto, de versão diferente ou com referências/valores fora dos limites aceitos é rejeitado antes da substituição; a transação evita uma importação parcial. Registros nativos do catálogo não podem ser sobrescritos pelo arquivo.

## Apagar dados

O comando **Apagar todos os dados** exige confirmação e remove do Room:

- treinos e seus itens;
- programação;
- sessões, séries e cargas;
- histórico de peso e perfil;
- registros das fotos de evolução e respectivas concessões de leitura (o arquivo original não é apagado);
- exercícios personalizados;
- marcações de favorito.

O catálogo nativo é reinserido para o aplicativo continuar utilizável. As preferências do DataStore — incluindo onboarding, tema, metas, alertas, demonstração, filtros, foto de perfil e Discord — também são apagadas; concessões persistentes das fotos são liberadas, o acompanhamento em segundo plano é encerrado e, no próximo início, o onboarding volta a ser exibido.

## Retenção e compartilhamento

Não há prazo automático de retenção. Os dados permanecem no dispositivo até que o usuário os exclua, limpe o armazenamento ou desinstale o app. O único compartilhamento automático de dados pessoais possível é o resumo de treino para o canal do Discord explicitamente configurado; pode ser desligado a qualquer momento em Perfil. A aba Música faz apenas leitura de conteúdo público e, como qualquer conexão HTTPS, o servidor de configuração e o Spotify podem receber dados técnicos de rede como endereço IP e cabeçalhos da solicitação.

## Recomendações para uma versão pública

- avaliar criptografia adicional para dados de saúde e backups;
- permitir proteção por biometria/PIN se fizer sentido para o público;
- validar o backup integralmente e mostrar uma prévia antes de substituir dados;
- incluir checksum e estratégia de migração entre versões do JSON;
- declarar o subtipo `specialUse` e justificar o uso do serviço em primeiro plano no Play Console antes de uma publicação pública;
- atualizar a política caso sejam adicionados nuvem, login ou telemetria;
- revisar as obrigações legais aplicáveis ao local de distribuição.
