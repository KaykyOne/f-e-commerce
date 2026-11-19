# Tema 14 — Teste de interface: Navegação

**Disciplina:** Integração e Entrega Contínua (DevOps) · **Atividade:** Qualidade e Testes de Software
**Integrante:** Kayky · **Projeto:** Aura Store (Java 21 + Spring Boot 3.5)
**Ferramenta:** Playwright for Java 1.65 + JUnit 5 + Spring Boot Test

---

## 1. Conceito

Um **teste de interface (UI)** verifica o sistema pela tela, do jeito que o usuário usa: abre o
navegador, clica em links e botões, digita, aperta teclas e confere o que aparece. O **teste de
navegação** foca nos caminhos entre as partes do sistema: menus, links, botões, redirecionamentos,
botões Voltar/Avançar e o que acontece quando um endereço não existe.

É um teste **caixa-preta** e de **ponta a ponta (E2E)**: atravessa frontend (HTML/CSS/JS), backend
(Spring Boot), API REST e banco de dados ao mesmo tempo.

### Finalidade e importância

- **Testes unitários não enxergam a tela.** Um link com `href` errado, um botão que não abre a janela
  ou uma seção que some no celular passam em todos os testes de backend.
- **Navegação quebrada = venda perdida.** Se o cliente não chega ao produto, à sacola ou ao checkout,
  o resto do sistema não importa.
- **Regressão.** Toda alteração em CSS ou JavaScript pode quebrar um caminho. O teste automatizado
  repete 22 verificações em ~20 segundos, a cada push, sem esforço manual.
- **Documentação viva.** Cada teste descreve, em português, um caminho que o usuário deve conseguir fazer.

## 2. Ferramenta escolhida: Playwright for Java

| Critério | Playwright | Selenium |
| --- | --- | --- |
| Espera automática pelos elementos | Sim (sem `sleep`/`WebDriverWait`) | Manual |
| Driver do navegador | Baixado pela própria biblioteca | ChromeDriver/GeckoDriver separados |
| Chromium, Firefox e WebKit na mesma API | Sim | Sim, com drivers diferentes |
| Asserções de UI prontas (`isInViewport`, `hasURL`, `isFocused`) | Sim | Não |
| Integração com JUnit 5 / Maven | Dependência Maven comum | Dependência Maven comum |

Escolhi o Playwright porque as **esperas automáticas** eliminam a principal causa de testes instáveis
(*flaky*) em interfaces assíncronas — e a loja carrega o catálogo de forma assíncrona. Ele roda em
Java, dentro do mesmo `mvn` do projeto.

Também foram usados: **JUnit 5** (organização, testes parametrizados, `@Tag`), **Spring Boot Test**
(`@SpringBootTest(webEnvironment = RANDOM_PORT)` sobe a aplicação real numa porta livre, com banco
**H2** de teste) e **GitHub Actions** (executa tudo a cada push).

## 3. Procedimentos realizados

1. **Mapeamento** de todos os elementos de navegação da loja: logo, menu principal (4 links),
   botões do banner, link "Pular para os produtos", botões de categoria, cartões de produto, sacola,
   checkout, janelas de confirmação, rodapé e versões desktop/celular.
2. **Dependência** `com.microsoft.playwright:playwright` (escopo `test`) no `pom.xml`.
3. **Classe de teste** [`NavigationE2ETest.java`](../../src/test/java/br/com/aura/store/e2e/NavigationE2ETest.java):
   - Sobe a aplicação real em porta aleatória e abre um navegador headless.
   - Um contexto novo por teste (sacola vazia, sem estado vazando entre testes).
   - Movimento reduzido (`reducedMotion`) para o scroll suave não atrapalhar as verificações.
   - Captura um *screenshot* ao final de cada teste como evidência (`target/evidencias/`).
   - Monitora erros de JavaScript no console e respostas HTTP ≥ 400.
4. **Separação do build:** os testes de interface têm `@Tag("e2e")` e só rodam com o perfil
   `-Pe2e`. Assim, o `mvn verify` do Dockerfile (sem navegador) continua funcionando.
5. **CI:** novo job `navegacao-e2e` no GitHub Actions, em matriz **Chromium × Firefox**, que publica
   os screenshots e o relatório do Surefire como artefatos.
6. **Execução, análise das falhas e correção** (seção 5).

## 4. Casos de teste (22 execuções)

| # | Caso de teste | Resultado esperado |
| --- | --- | --- |
| 1 | Página inicial carrega | HTTP 200, título correto, menu com 4 links, 8 produtos, sem erros de JS e sem recursos com falha |
| 2 | Nenhum link quebrado | Toda âncora `#...` aponta para um elemento existente; links `/` respondem 200 |
| 3 | Menu "Explorar" | URL termina em `#catalogo` e o catálogo fica visível |
| 4–5 | Menu "Áudio" / "Seu setup" | Leva ao catálogo, limpa a busca, ativa a categoria (3 e 2 produtos) |
| 6 | Menu "Sobre a Aura" → "Explore a coleção" | Vai para `#sobre` e volta para `#catalogo` |
| 7 | Botão e cartão do banner | Ambos levam ao catálogo |
| 8 | Rodapé "Voltar ao topo" | Volta ao título principal; rodapé sai da tela |
| 9 | Logo | Redireciona para `/` a partir de qualquer seção |
| 10 | Link direto `/#sobre` | A página abre já na seção "Sobre" |
| 11 | Voltar/Avançar do navegador | O histórico retorna às seções corretas |
| 12 | Teclado | `Tab` foca "Pular para os produtos"; `Enter` leva ao catálogo |
| 13–17 | 5 botões de categoria | Cada um filtra a quantidade certa; "Todos" restaura 8 |
| 18 | Detalhes do produto | Abre pelo nome e pela imagem; fecha pelo × e pela tecla Esc |
| 19 | Sacola vazia | Mostra o aviso e o botão "Explorar a coleção" fecha a sacola |
| 20 | Fluxo completo | Produto → sacola → checkout → voltar → checkout → confirmação → loja (contador volta a 0) |
| 21 | Rota inexistente | HTTP 404 com página da loja e link "Voltar para a loja" que leva a `/` |
| 22 | Celular (390×844) | Menu mantém "Explorar" e "Sobre"; sacola abre e cabe na tela |

## 5. Resultados

### 5.1 Primeira execução — 19 aprovados, 3 falhas

```
Tests run: 22, Failures: 3, Errors: 0, Skipped: 0
linkDiretoAbreNaSecao        → Locator expected to be in viewport — viewport ratio 0
logoRedirecionaParaInicio    → Locator expected to be in viewport — viewport ratio 0
rotaInexistente...           → Page title expected to match "Página não encontrada" — Received: ""
```

O teste encontrou **dois problemas reais de navegação**:

**Problema 1 — Link direto para uma seção cai no lugar errado.**
Ao abrir `/#sobre` (por exemplo, um link compartilhado), o navegador rola até "Sobre" assim que o HTML
carrega. Só depois o catálogo chega da API e insere 8 produtos **acima** da seção, empurrando-a para
baixo. O usuário fica parado no meio da lista de produtos
([evidência](evidencias/01-antes-deep-link-sobre.png)). O teste do logo falhou pela mesma causa,
porque começava em `/#sobre`.

**Problema 2 — Rota inexistente é um beco sem saída.**
Qualquer endereço errado mostrava a "Whitelabel Error Page" padrão do Spring Boot: em inglês, sem
identidade visual e **sem nenhum link de volta** para a loja
([evidência](evidencias/00-antes-rota-inexistente.png)).

### 5.2 Melhorias implementadas

| Problema | Correção | Arquivo |
| --- | --- | --- |
| Deep link fora da seção | Após a primeira renderização do catálogo, `restoreHashTarget()` rola de novo até o elemento indicado no `#` da URL (uma única vez) | `static/app.js` |
| 404 sem saída | Página `error/404.html` com a identidade da loja, mantendo o status HTTP 404, e links "Voltar para a loja" e "Ver produtos" | `static/error/404.html` |

### 5.3 Execução após as correções — 22 de 22 aprovados

| Navegador | Testes | Falhas | Tempo |
| --- | --- | --- | --- |
| Chromium | 22 | 0 | ~15 s |
| Firefox | 22 | 0 | ~27 s |

Os testes também foram executados 3 vezes seguidas, sem falhas intermitentes. Os 7 testes de
integração que já existiam continuam passando no `mvn verify` padrão.

Evidências: [`evidencias/`](evidencias/) (antes/depois e prints de cada fluxo),
[`execucao-antes.txt`](evidencias/execucao-antes.txt) e
[`execucao-depois.txt`](evidencias/execucao-depois.txt). Na CI, cada execução publica os
screenshots como artefato `evidencias-navegacao-<navegador>`.

## 6. Como executar

```bash
# Testes de navegação (o Playwright baixa o navegador na primeira execução)
mvn verify -Pe2e                         # Chromium, sem janela
mvn verify -Pe2e -Dbrowser=firefox       # Firefox
mvn verify -Pe2e -Dheaded=true -DslowMo=500   # com janela visível, em câmera lenta (apresentação)

# Testes de backend (sem navegador)
mvn verify
```

Screenshots: `target/evidencias/navegacao/<navegador>/`. Relatório: `target/surefire-reports/`.

## 7. Conclusão

Os testes de navegação cobrem os caminhos que o cliente usa para chegar ao produto e concluir a
compra. Na primeira execução eles encontraram dois defeitos que nenhum teste de backend detectaria:
uma falha de tempo entre o carregamento assíncrono e o scroll do navegador, e uma rota de erro sem
saída. Depois das correções, os 22 casos passam em Chromium e Firefox, e o GitHub Actions repete a
verificação a cada push, impedindo que esses caminhos quebrem de novo sem que ninguém perceba.
