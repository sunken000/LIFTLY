# Liftly Business 1.0.0 — Business Preview

## Duas edições independentes

- **Liftly Pessoal:** `com.liftly.app`, sem paywall e voltado ao uso particular.
- **Liftly Business:** `com.liftly.app.business`, base comercial separada para academias e assinaturas.
- As duas edições podem permanecer instaladas no mesmo aparelho.

## Base funcional

- Integração opcional com Health Connect.
- Leitura autorizada do peso e sono mais recentes.
- Exportação dos treinos reais concluídos para o Health Connect.
- Substituição inteligente de exercícios por músculo, padrão de movimento, equipamento, dificuldade e segurança.
- A carga é zerada ao substituir um exercício para evitar a reutilização acidental de um peso inadequado.

## Fundação comercial

- Identidade própria para a edição Business.
- Cliente Google Play Billing 9.1 integrado para consultar e iniciar o plano `liftly_pro`, com planos-base `monthly` e `annual`.
- Compras retornadas pela Play ficam pendentes até verificação segura do backend; o APK nunca libera assinatura apenas pelo estado local.
- Modelo de licenças de academia previsto para `gym_start`, `gym_growth` e `gym_enterprise`.
- Separação entre recursos pessoais e futuros recursos de organização, treinadores, alunos e assentos.
- Plano técnico documentado para validação de compras no backend e notificações da Google Play.

## Importante

Esta compilação é uma **prévia comercial**, adequada para demonstração e validação com academias. Ela consegue consultar produtos e abrir o fluxo oficial da Play quando instalada por uma faixa de teste que tenha os produtos cadastrados, mas ainda não libera assinaturas reais sem o backend de verificação.

Para liberar cobranças em produção ainda é necessário:

- Criar produtos e planos na Play Console.
- Configurar os produtos da Play Console e a validação segura no backend.
- Publicar domínio, termos e política de privacidade.
- Concluir as declarações de Data safety e Health Connect.
- Configurar assinatura de release e gerar os AABs de produção.

Nenhuma assinatura deve ser considerada ativa apenas por estado local no aparelho. A licença de produção precisa ser confirmada pelo servidor.

Consulte [COMMERCIALIZATION_PLAN.md](COMMERCIALIZATION_PLAN.md) para preços sugeridos, arquitetura e checklist de lançamento.
