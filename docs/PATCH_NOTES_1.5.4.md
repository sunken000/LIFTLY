# Liftly 1.5.4

**Data:** 10/08/2026  
**versionName:** `1.5.4`  
**versionCode:** `36`

## Refinamento visual

A versão 1.5.4 concentra as mudanças na identidade visual do aplicativo. O objetivo é manter o Liftly reconhecível sem depender de efeitos que deixam a interface com aparência genérica de template.

### Paleta principal

- violeta principal menos saturado;
- contraste mais controlado entre cor de marca e conteúdo;
- tons secundários puxados para neutros em vez de rosa/lilás concorrente;
- fundo escuro menos avermelhado e superfícies mais próximas de carvão;
- tema claro mais neutro, com menos contaminação lilás;
- tema Preto OLED com superfícies discretas e destaque violeta moderado.

### Superfícies

- `glassSurface` ficou significativamente mais opaco nos três temas;
- cards continuam reconhecíveis como componentes Liftly, mas o conteúdo passa a ter prioridade sobre o efeito de vidro;
- o gradiente de borda existente recebe cores menos saturadas por meio dos novos tokens do tema;
- o brilho de marca foi reduzido nos tokens estendidos usados por componentes e personalização.

### Fundo e cores ambientais

- tons de aurora foram deslocados para violeta e malva dessaturados;
- a mudança reduz a sensação de fundo promocional sem remover a identidade animada já existente;
- o tema Preto OLED permanece sem brilho ambiental.

## Por que essa mudança

A auditoria visual encontrou repetição excessiva de sinais de “interface premium”: glassmorphism, brilho neon, roxo muito saturado, gradiente e arredondamento apareciam simultaneamente em muitas superfícies. Quando todos os elementos recebem alta ênfase, a hierarquia visual enfraquece e o produto pode parecer montado a partir de um template.

A 1.5.4 começa a corrigir isso pelo nível mais sistêmico: os tokens de cor usados pelo `MaterialTheme`. Isso melhora Hoje, Treinos, Progresso, Perfil, Rewards, navegação e componentes compartilhados sem alterar os fluxos do usuário.

## Compatibilidade

- nenhuma alteração no banco Room;
- nenhum dado do usuário é migrado ou apagado;
- `applicationId` continua `com.liftly.app`;
- Android mínimo continua API 26;
- recursos funcionais da série 1.5 permanecem disponíveis.
