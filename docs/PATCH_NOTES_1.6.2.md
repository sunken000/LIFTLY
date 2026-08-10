# Liftly 1.6.2

## Bi-set direto na ficha

- Cada exercício na tela **Treinos** ganha um botão **Bi-set**.
- Ao tocar, o app entra em modo de pareamento e pede o segundo exercício.
- O segundo exercício escolhido é movido automaticamente para junto do primeiro.
- Os dois passam a aparecer como **BI-SET A ↔ parceiro** e **BI-SET B ↔ parceiro**.
- O mesmo local oferece **Desfazer** para voltar os dois exercícios ao tipo normal.
- Se um dos exercícios já pertencer a outro bi-set, o pareamento anterior é removido antes do novo.

## Execução

O par usa a sequência `A1 → B1 → descanso → A2 → B2 → descanso`, preservando o descanso após o exercício B.

## Compatibilidade

A implementação continua usando a ordem da ficha e o campo de tipo de série existentes, portanto não exige migração do banco de dados.

## Versão

- `versionName`: 1.6.2
- `versionCode`: 40
- `applicationId`: com.liftly.app
