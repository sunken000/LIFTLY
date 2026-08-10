# Liftly 1.6.0

## Training Intelligence

- O Coach continua local e determinístico, mas agora fecha o ciclo: após uma sessão válida, uma recomendação suportada pelo histórico pode atualizar a próxima carga/faixa da própria ficha.
- Dor com status de cautela nunca altera a prescrição automaticamente.
- A sessão mostra o desempenho anterior do exercício como contexto.

## Sessão focada

- A interface avança uma série por vez.
- Séries já concluídas permanecem visíveis como contexto; séries futuras deixam de competir por atenção.
- RIR, dor, anilhas, substituição, descanso e aquecimento continuam integrados.

## Aquecimento unificado

- Séries de aquecimento e séries normais compartilham a mesma superfície visual e o mesmo padrão de conclusão.
- O aquecimento também avança uma etapa por vez.
- Continua excluído de volume, histórico oficial, PRs e Rewards.

## Pós-treino

- Novo relatório com duração, séries, volume, recordes pessoais, comparação com a sessão anterior e leitura objetiva do Coach.
- Novo card PNG 1080×1350 gerado localmente e compartilhável pelo Android.

## Progresso

- Nova leitura interpretativa para aderência semanal, tendência de volume em janelas de quatro semanas, possível estagnação por exercício e distribuição de séries por grupo muscular.
- As leituras são heurísticas do próprio histórico e não são apresentadas como diagnóstico fisiológico.

## Rewards

- Migração retroativa idempotente: treinos históricos válidos passam a receber XP/Lift Coins segundo a política atual.
- O ledger `session:<id>` impede que um treino já recompensado seja pago novamente.
- Missões diárias foram retiradas da experiência principal; os marcos passam a ser semanais e mensais.
- Descanso e quebra de sequência não removem XP, moedas ou itens.

## Wear OS

- Novo módulo `:wear`.
- O celular publica a série ativa pelo Wearable Data Layer.
- No relógio é possível ver exercício, série, carga, reps e RIR, ajustar valores e concluir a série.
- Frequência cardíaca ao vivo usa Android Health Services quando o sensor/permissão estão disponíveis.

## Performance e qualidade

- ProfileInstaller 1.4.1 e perfil inicial embarcado.
- Módulo `:macrobenchmark` com BaselineProfileRule e benchmark de cold startup.
- Screenshot smoke test para a raiz Compose.
- CI da 1.6.0 compila app, androidTest, Wear OS e Macrobenchmark antes de publicar artefatos.

## Versão

- `versionName`: 1.6.0
- `versionCode`: 38
- `applicationId`: com.liftly.app
