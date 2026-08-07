# Liftly 1.3.3 — Correção de inicialização

## Corrigido

- Corrigido o crash que acontecia antes da abertura da primeira tela.
- O relatório do aparelho mostrou que o Android não conseguia carregar
  `LiftlyApplication` durante a criação do processo.
- A inspeção da versão 1.3.2 confirmou que ela continha referências a essa classe,
  mas não a definição da classe em nenhum DEX.
- O APK foi reconstruído integralmente a partir de uma pasta de build limpa e sem
  reutilizar os artefatos incrementais que produziram o pacote incompleto.

## Validação

- A verificação final confere diretamente se `LiftlyApplication` e
  `MainActivity` estão definidas nos DEX do APK.
- Manifesto, versão, assinatura e testes unitários também são validados.

## Mantido

- Nova interface contida da versão 1.3.2.
- Correção da ordem e da numeração dos exercícios.
- Reparação automática de índices antigos duplicados ou incompletos.

## Versão

- `1.3.3` (`versionCode 27`).
