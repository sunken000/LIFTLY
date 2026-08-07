# Testes e limitações da entrega

## Verificações executadas em 21/07/2026

| Verificação | Resultado |
|---|---|
| `testDebugUnitTest` | aprovado; 135 testes, 0 falhas |
| `assembleDebug` | aprovado; APK 1.4.0 gerado |
| `lintDebug` | não repetido nesta correção curta; a versão-base anterior estava aprovada |
| `assembleDebugAndroidTest` | não repetido nesta correção curta; a suíte instrumentada existente permanece no projeto |
| `connectedDebugAndroidTest` | não executado: nenhum dispositivo/emulador conectado |
| Instalação via ADB | não executada: nenhum dispositivo conectado |

Os testes JVM cobrem IMC adulto/idoso/por idade e sexo, parâmetros LMS, analisador de treino, catálogo, busca sem acentos, cronômetros/alarme, estimativa MET, coach de progressão, metas/sequências, Discord e Spotify: configuração pública/oEmbed e validação segura de links de playlists. Os testes instrumentados existentes cobrem persistência atômica, RIR/dor, snapshots planejado→realizado, backup, fotos, agenda, reset, modo teste e exclusões; sua execução exige dispositivo ou emulador Android.

## Checklist manual recomendado

- concluir e pular o onboarding, com e sem dados de demonstração;
- criar, editar, duplicar, arquivar e excluir treino;
- adicionar, configurar, reordenar e remover exercícios;
- pesquisar exercícios com e sem acentos no catálogo e no seletor do treino;
- criar, editar, favoritar e excluir exercício personalizado, incluindo imagem opcional;
- programar vários treinos, descanso, copiar semana e alterar status;
- iniciar, interromper, retomar e finalizar sessões completas/parciais;
- iniciar um treino em modo teste, fechar/reabrir o app, concluir séries e confirmar que finalizar descarta sessão, histórico, recordes e agenda;
- rolar uma sessão longa e confirmar que o descanso permanece visível, usa o tempo configurado, vibra uma vez e permite pular;
- durante um treino, bloquear a tela e alternar para outro app; conferir exercício/descanso na notificação e o som/vibração únicos ao zerar, inclusive com fones;
- com música tocando, testar os quatro alarmes e durações mínima/máxima; confirmar ducking durante o aviso e restauração do volume ao terminar/desligar;
- registrar repetições/carga e exercícios por tempo/distância;
- informar RIR e dor em séries, conferir atualização do coach e confirmar que dor forte bloqueia prescrição automática;
- iniciar, pausar, retomar e zerar o cronômetro geral, além de registrar voltas;
- abrir **Música** online e offline; salvar, selecionar e remover playlists pessoais; conferir Embed de playlist pública, fallback **Abrir no Spotify** para uma privada e configuração global;
- fechar e reabrir o app para verificar a persistência;
- conferir histórico detalhado das sessões, ranking de recordes/e1RM, meta semanal, sequência, notificações, peso corporal e limites do IMC por idade/sexo;
- com peso cadastrado, conferir calorias estimadas de treinos de musculação, cardio e mistos; sem peso, conferir a orientação para cadastrá-lo;
- em **Progresso > Gerenciar**, excluir um dos dois treinos do mesmo dia e confirmar que o outro permanece; depois testar a exclusão do dia inteiro;
- alternar Roxo Neon/Branco/Preto, conferindo parallax/animação, contraste dos títulos/ícones, preto sólido, barras do sistema, rotação e escala de fonte;
- adicionar, trocar, remover e reabrir a foto de perfil, inclusive em Android 8/9;
- adicionar fotos corporais, fechar/reabrir o app, conferir data, selecionar duas, comparar sem recorte e excluir uma sem apagar o arquivo original;
- adicionar o widget em launchers diferentes e conferir treino explícito, rotina semanal, descanso, vários treinos e atualização após editar a agenda;
- salvar e testar um webhook válido, finalizar um treino real e conferir o embed; confirmar que modo teste, fotos e webhook não são enviados e que desligar a opção impede novos envios;
- conferir o ícone normal/redondo/adaptativo em launchers com máscaras diferentes;
- exportar, apagar e reimportar um backup JSON;
- verificar navegação Voltar e estados vazios em todas as abas.

## Limitações conhecidas

- o APK entregue é uma compilação `debug`, assinada com a chave de desenvolvimento; publicação exige assinatura `release` própria;
- não há sincronização em nuvem, conta Liftly ou conexão de conta Spotify. Discord é somente uma exportação unidirecional e opcional;
- o APK entregue usa como padrão a playlist pública `7jOh9hQGVTDjtWyIfYe5OY`, fornecida pelo proprietário. Trocar as faixas dessa mesma playlist reflete pelo Spotify; trocar o ID em todos os aparelhos exige compilar uma vez com `LIFTLY_MUSIC_CONFIG_URL` apontando para o JSON público descrito em `docs/MUSICA_SPOTIFY.md`. Isso é a sincronização global e não altera bibliotecas pessoais;
- playlists pessoais são locais e o Embed oficial é mais confiável para playlists públicas/compartilháveis. Uma playlist privada pode não abrir na WebView; **Abrir no Spotify** é o fallback. O APK não contém OAuth, Web API ou App Remote;
- no Android 13 ou superior, a notificação na gaveta depende da autorização do usuário; se ela for negada, o Android ainda trata o acompanhamento como serviço ativo, mas pode mostrá-lo somente no gerenciador de tarefas do sistema;
- uma publicação na Play Store precisa declarar e justificar o tipo de serviço em primeiro plano `specialUse` no Play Console;
- imagens de exercícios personalizados e fotos corporais são URIs locais; os binários não são incorporados ao JSON e podem não abrir em outro dispositivo;
- os gráficos usam os dados locais e não fazem interpretação clínica;
- o IMC e as sugestões de treino são triagem/orientação geral, não avaliação profissional;
- as calorias são uma estimativa bruta por MET, peso e duração; intensidade real, pausas, condicionamento, idade e metabolismo individual podem produzir gasto diferente de um wearable ou calorimetria;
- não foi possível realizar teste físico de instalação porque o ambiente não tinha dispositivo ou emulador conectado.
