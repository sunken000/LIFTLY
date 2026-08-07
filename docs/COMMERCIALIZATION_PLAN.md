# Plano de comercialização do Liftly

## Status desta entrega

O projeto passa a ter duas edições independentes:

| Edição | Nome | Application ID | Finalidade |
|---|---|---|---|
| Pessoal | Liftly | `com.liftly.app` | Uso particular, sem paywall e sem recursos comerciais |
| Comercial | Liftly Business | `com.liftly.app.business` | Base para assinaturas, academias e gestão de licenças |

O APK Business gerado localmente é uma **prévia comercial/fundação técnica**. Ele pode ser instalado e demonstrado, mas ainda não deve cobrar clientes reais. A monetização só fica pronta para produção depois da configuração dos produtos na Play Console, verificação segura das compras no servidor, domínio e política de privacidade publicados, declarações da Play e assinatura de release.

Os dois identificadores são diferentes, portanto as edições podem ser instaladas no mesmo aparelho e uma não substitui a outra.

## Oferta recomendada

### Usuário individual

| Plano | Preço inicial sugerido | Conteúdo |
|---|---:|---|
| Gratuito | R$ 0 | Treinos, histórico local e funções essenciais |
| Pro mensal | R$ 14,90/mês | Coach avançado, análises, metas, exportações e recursos premium |
| Pro anual | R$ 119,90/ano | Mesmos recursos do Pro, com desconto anual |

Preços são hipóteses iniciais e devem ser validados com teste A/B, conversão e retenção. O preço final exibido ao usuário deve vir da Google Play, já localizado e com os tributos aplicáveis.

Configuração sugerida na Play Console:

- Produto de assinatura: `liftly_pro`
- Plano-base mensal: `monthly`
- Plano-base anual: `annual`
- Oferta opcional: teste gratuito de 7 dias, somente se o fluxo de cancelamento e a comunicação estiverem claros

Os IDs dos produtos devem ser definidos com cuidado, pois não são bons candidatos a renomeação depois da publicação.

### Academias

| Plano | Preço inicial sugerido | Limite |
|---|---:|---:|
| Gym Start | R$ 199/mês | Até 50 alunos ativos |
| Gym Growth | R$ 399/mês | Até 150 alunos ativos |
| Enterprise | Sob consulta | Mais de 150 alunos, unidades e integrações |

O modelo B2B recomendado é venda consultiva por contrato, com cobrança por boleto, Pix recorrente ou cartão em um portal web. A academia recebe uma organização, convida profissionais e alunos e o aplicativo apenas consulta a licença no backend. Não deve haver botão ou link de compra externa dentro do aplicativo distribuído pela Play sem uma revisão específica das políticas aplicáveis.

Chaves internas sugeridas para o backend:

- `gym_start`
- `gym_growth`
- `gym_enterprise`

Recursos comerciais prioritários:

- Painel da academia com alunos ativos, adesão e retenção.
- Convite de treinadores e alunos por organização.
- Prescrição e revisão de treinos com trilha de auditoria.
- Identidade visual da academia nos planos superiores.
- Relatórios agregados com consentimento e acesso por função.
- Limites de assentos e permissões por unidade.

## Arquitetura de produção

```text
Liftly / Liftly Business
        |
        +-- Firebase Authentication
        |
        +-- API no Cloud Run
              |
              +-- Cloud SQL PostgreSQL
              +-- Cloud Storage para fotos autorizadas
              +-- Google Play Developer API
              +-- Pub/Sub para Real-time Developer Notifications
```

Responsabilidades mínimas:

- O aplicativo inicia a compra com Google Play Billing.
- O backend recebe o token da compra e o valida na Google Play Developer API.
- A licença só é liberada após validação no servidor.
- O backend confirma ou reconhece a compra, registra validade, cancelamento e reembolso.
- As Real-time Developer Notifications mantêm as licenças atualizadas.
- O aplicativo consulta uma autorização assinada pelo backend e nunca confia apenas em uma preferência local.
- Segredos, credenciais de serviço e chaves privadas nunca entram no APK.
- Logs não devem conter tokens, fotos, peso, sono ou outros dados sensíveis.

Para a camada Android, usar a versão estável vigente da Play Billing Library. Na data deste plano, a documentação oficial lista a linha 9.1.0; a versão deve ser confirmada novamente antes do lançamento.

## Health Connect, privacidade e segurança

Permissões mínimas propostas:

- `android.permission.health.READ_WEIGHT`
- `android.permission.health.READ_SLEEP`
- `android.permission.health.WRITE_EXERCISE`

Princípios obrigatórios:

- Health Connect é opcional e continua utilizável sem conceder todas as permissões.
- Solicitar somente os dados necessários à função visível para o usuário.
- Explicar o uso antes da tela de permissões.
- Não usar dados de saúde para publicidade, perfilamento comercial ou venda.
- Não apresentar o coach como diagnóstico, tratamento ou substituto de profissional de saúde.
- Permitir revogação de acesso, exportação e exclusão da conta e dos dados.
- Fotos corporais exigem consentimento explícito, armazenamento privado, exclusão individual e política de retenção.
- Integrações como Discord ou armazenamento externo precisam aparecer na ficha de segurança de dados quando transferirem informações.

Antes da publicação:

- Publicar política de privacidade em URL HTTPS acessível e dentro do aplicativo.
- Preencher a seção **Data safety** conforme os fluxos reais, não conforme a intenção futura.
- Preencher a declaração de aplicativos de saúde na Play Console.
- Justificar cada permissão do Health Connect e demonstrar a função correspondente.
- Informar coleta, compartilhamento, criptografia em trânsito, retenção e exclusão.
- Disponibilizar canal de suporte e procedimento para exclusão da conta.

## Checklist da Play Console

### Contas e identidade

- [ ] Criar conta de desenvolvedor adequada ao responsável legal; para operação empresarial, usar conta de organização.
- [ ] Providenciar dados da empresa e D-U-N-S quando solicitado.
- [ ] Configurar perfil de pagamentos/merchant e dados fiscais.
- [ ] Publicar domínio, suporte, termos de uso e política de privacidade.

### Aplicativos

- [ ] Criar duas fichas separadas com os IDs `com.liftly.app` e `com.liftly.app.business`.
- [ ] Confirmar nomes, ícones, screenshots, descrição curta e descrição completa.
- [ ] Preencher classificação indicativa, público-alvo, anúncios e acesso ao app.
- [ ] Declarar saúde, segurança de dados e exclusão de conta.

### Assinaturas

- [ ] Criar `liftly_pro` com os planos-base `monthly` e `annual`.
- [ ] Configurar preços por país, período de tolerância, pausa e restauração.
- [ ] Adicionar testadores de licença e testar compra, renovação, cancelamento, expiração e reembolso.
- [ ] Integrar Play Developer API, conta de serviço e RTDN no backend.
- [ ] Exibir termos, preço, recorrência e ação para gerenciar assinatura.

### Release

- [ ] Criar uma chave de upload protegida e ativar Play App Signing.
- [ ] Retirar segredos do projeto e configurar ambientes `dev`, `staging` e `production`.
- [ ] Habilitar minificação/otimização de release e guardar os arquivos de mapeamento.
- [ ] Gerar AAB assinado de cada edição; APK local serve apenas para instalação direta e testes.
- [ ] Testar migração de banco, atualização sobre versões anteriores e restauração de backup.
- [ ] Executar testes fechados em aparelhos e versões Android diferentes.
- [ ] Validar acessibilidade, comportamento offline, falhas de rede, compras pendentes e restauração de licença.
- [ ] Monitorar crashes, ANRs, falhas de compra e suporte antes da produção gradual.
- [ ] Publicar primeiro em teste interno/fechado e ampliar a distribuição por etapas.

Com variantes Gradle configuradas, os artefatos esperados serão gerados por tarefas equivalentes a `bundlePersonalRelease` e `bundleBusinessRelease`. Os nomes exatos devem ser confirmados no projeto após a configuração final das variantes.

## Critério de “pronto para vender”

O Liftly Business estará comercialmente pronto somente quando todos estes itens forem verdadeiros:

1. AAB de release assinado e aceito pela Play Console.
2. Produtos reais configurados e testados.
3. Backend validando compras e atualizando licenças por RTDN.
4. Autenticação, organizações, papéis e limites de assentos funcionando.
5. Política de privacidade, termos, exclusão e suporte publicados.
6. Declarações de Health Connect e Data safety aprovadas.
7. Monitoramento, backup, recuperação e processo de atendimento definidos.

Até lá, a edição Business deve ser apresentada como **Business Preview**, própria para demonstração comercial e validação com academias, sem cobrança real.

## Referências oficiais

- [Começar com Health Connect](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [Tipos de dados do Health Connect](https://developer.android.com/health-and-fitness/health-connect/data-types)
- [Notas de versão da Play Billing Library](https://developer.android.com/google/play/billing/release-notes)
- [Integração com Google Play Billing](https://developer.android.com/google/play/billing/integrate)
- [Backend seguro para Google Play Billing](https://developer.android.com/google/play/billing/backend)
- [Política de permissões e APIs de dados de saúde](https://support.google.com/googleplay/android-developer/answer/12991134)
- [Declaração de aplicativos de saúde](https://support.google.com/googleplay/android-developer/answer/14738291)
- [Requisitos para aplicativos de saúde](https://support.google.com/googleplay/android-developer/answer/16558241)
