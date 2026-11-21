# Aura Store

E-commerce demonstrativo em **Java 21 + Spring Boot 3.5.7**, com PostgreSQL 17 e frontend em HTML, CSS e JavaScript. Interface em português, responsiva e sem dependências de frontend ou imagens externas.

## Rodar com Docker

Pré-requisito: Docker com Compose e acesso à internet no primeiro build para baixar as imagens e dependências.

```bash
docker compose up --build -d
```

Abra **http://localhost:8080**. O primeiro build executa os testes antes de gerar a imagem. O Compose aguarda o banco ficar saudável antes de iniciar a aplicação.

```bash
docker compose logs -f app          # acompanhar a inicialização
docker compose ps                   # conferir a saúde dos serviços
bash scripts/smoke-test.sh          # conferir aplicação e banco
docker compose down                # parar, preservando os dados
```

O volume `postgres_data` mantém produtos, estoque e pedidos entre reinicializações. Para apagar os dados e reiniciar o catálogo de demonstração, execute `docker compose down -v` (isso exclui os pedidos).

Configuração opcional:

```bash
cp .env.example .env
# Edite APP_PORT e DB_PASSWORD antes da primeira inicialização.
docker compose up --build -d
```

A senha padrão é exclusivamente de desenvolvimento local. Alterar `DB_PASSWORD` depois de criar o volume também exige alterar a senha do usuário no PostgreSQL existente.

## Perfis de acesso

| Perfil | O que pode fazer | Onde |
| --- | --- | --- |
| **Visitante** | Navegar, buscar, montar a sacola, criar conta e entrar | `/`, `/entrar`, `/cadastro` |
| **Cliente** | Tudo do visitante + finalizar compras, editar o perfil, trocar a senha e ver **Meus pedidos** | `/conta` |
| **Administrador** | Painel com resumo da loja, cadastro/edição/desativação de produtos e lista de todos os pedidos | `/admin` |

Contas de demonstração (somente desenvolvimento):

- **Administrador:** `admin@aura.dev` / `AuraAdmin2026`. É criado na primeira inicialização. Em outro ambiente, defina `ADMIN_EMAIL` e `ADMIN_PASSWORD` no `.env` antes de subir.
- **Cliente:** crie a sua em **Entrar → Criar conta**. Todo cadastro público gera um cliente; ninguém vira administrador pela loja.

Regras de navegação:

- Visitante que abre `/conta` ou `/admin` é redirecionado para `/entrar?next=...` e volta para a página depois de entrar.
- Visitante que tenta finalizar a compra vai ao login e retorna com a sacola aberta.
- Cliente que abre `/admin` recebe a página **403** da loja, com caminho de volta.
- Rotas inexistentes mostram a página **404** da loja.

## Funcionalidades

- Oito produtos iniciais, detalhes, busca sem distinção de acentos, categorias e ordenação.
- Sacola com quantidade, remoção e persistência em `localStorage`.
- Frete de R$ 19,90; grátis em pedidos a partir de R$ 399,00.
- Checkout exclusivo para clientes logados, preenchido com os dados do perfil e validado no servidor (nome, endereço, cidade, CEP e itens). O e-mail do pedido vem da conta.
- Autenticação com Spring Security: sessão no servidor, senhas com bcrypt, proteção CSRF, nova sessão após o login e mensagem de erro que não revela se o e-mail existe.
- Exclusão lógica de produtos pelo administrador: o produto sai da loja, mas os pedidos antigos continuam íntegros.
- Valores calculados no servidor com `BigDecimal`; o cliente envia apenas IDs e quantidades dos produtos.
- Pedido e itens persistidos com cópia do nome e preço no momento da compra.
- Baixa de estoque transacional com `SELECT … FOR UPDATE`, evitando venda acima do estoque em compras simultâneas. Uma falha desfaz o pedido inteiro.
- Migrações Flyway, healthchecks, imagem em duas etapas e execução como usuário sem privilégios.

**É uma demonstração:** não há pagamento, envio, recuperação de senha, limite de tentativas de login, cálculo real de frete ou integração com transportadoras. Use dados fictícios. O checkout registra o pedido e reduz o estoque, mas não cobra nem envia nada. Não há endpoint público de consulta a dados pessoais dos pedidos. Em caso de perda de conexão durante a confirmação, confira o banco antes de reenviar: o endpoint não implementa idempotência.

## API

| Método | Endpoint | Resultado |
| --- | --- | --- |
| GET | `/api/products` | Catálogo (produtos ativos) com preços e estoque |
| GET | `/api/auth/me` | Sessão atual (`authenticated: false` para visitante) |
| POST | `/api/auth/register` | Cria conta de cliente e já entra |
| POST | `/api/auth/login` · `/api/auth/logout` | Entra · sai |
| PUT | `/api/account` · `/api/account/password` | Cliente: edita o perfil · troca a senha |
| GET | `/api/account/orders` | Cliente: seus pedidos |
| POST | `/api/orders` | Cliente: cria pedido demonstrativo e baixa o estoque |
| GET | `/api/admin/summary` · `/api/admin/orders` | Admin: resumo da loja · todos os pedidos |
| GET/POST | `/api/admin/products` | Admin: lista (inclui desativados) · cadastra |
| PUT/DELETE | `/api/admin/products/{id}` | Admin: edita · desativa |
| GET | `/actuator/health` | Saúde da aplicação e conexão com o banco |

Requisições que alteram dados (POST, PUT, DELETE) exigem sessão e o cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`. O frontend já faz isso. Sem login, a API responde `401`; sem permissão, `403`.

O pedido retorna `201` com ID UUID, status `DEMO_CONFIRMED`, subtotal, frete, total e itens. Dados inválidos retornam `400`, produto inexistente `404`, estoque insuficiente `409`. Campos desconhecidos, como preço enviado pelo cliente, são rejeitados. Limite: 50 linhas no pedido e 99 unidades por produto, incluindo linhas repetidas.

## Desenvolvimento e testes

Com JDK 21+ e Maven 3.6.3+ instalados:

```bash
mvn verify
```

Os testes de integração usam H2 em modo PostgreSQL e cobrem preços, frete, validação, persistência, rollback, concorrência e o controle de acesso dos três perfis (`AccessControlTest`). Não dependem de um PostgreSQL externo. A CI também sobe o Compose com PostgreSQL e executa o smoke test.

Com Node.js 22+, os testes do carrinho, cálculo de frete, busca e tratamento do armazenamento podem ser executados sem instalar dependências:

```bash
node --test scripts/test-frontend.cjs
```

### Testes de navegação (interface) — Playwright

Testes E2E que abrem um navegador real e percorrem menus, links, botões, sacola, checkout, login, cadastro, área do cliente, painel do administrador, páginas 403/404 e versão mobile. Ficam fora do `mvn verify` padrão (marcados com `@Tag("e2e")`) e rodam com o perfil `e2e`:

```bash
mvn verify -Pe2e                              # Chromium
mvn verify -Pe2e -Dbrowser=firefox            # Firefox
mvn verify -Pe2e -Dheaded=true -DslowMo=500   # janela visível, para demonstração
```

Screenshots de cada teste ficam em `target/evidencias/navegacao/`. Relatório completo do tema em [docs/tema-14-navegacao/RELATORIO.md](docs/tema-14-navegacao/RELATORIO.md). A CI executa esses testes em Chromium e Firefox a cada push.

Para desenvolver sem colocar o Java em um container:

```bash
docker compose run --rm --service-ports -p 5432:5432 db
```

Em outro terminal:

```bash
mvn spring-boot:run
```

As configurações locais padrão se conectam ao PostgreSQL em `localhost:5432`, banco/usuário `aura`. Para personalizar, use `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`; no Compose, as propriedades `SPRING_DATASOURCE_*` já estão configuradas.

## Estrutura

```text
src/main/java/br/com/aura/store/   API, validação e transação de compra
  security/  account/  admin/       Spring Security e perfis · conta do cliente · painel admin
src/main/resources/db/migration/ Schema e catálogo inicial
src/main/resources/static/       Loja, entrar, cadastro, conta, admin, páginas 403/404 e SVGs
src/test/                        Testes de integração e de navegação (Playwright)
docs/tema-14-navegacao/          Relatório e evidências do teste de navegação
scripts/smoke-test.sh            Verificação da aplicação em execução
Dockerfile                      Build com testes + runtime Java 21
compose.yaml                    Aplicação e PostgreSQL persistente
```

A versão Java escolhida é compatível com a linha do framework: [requisitos oficiais do Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

### Validação no ambiente de criação

A configuração do Compose, a sintaxe do JavaScript e os testes de frontend foram verificados localmente. O ambiente de criação não permite acesso ao daemon Docker, resolução DNS para baixar dependências Maven ou abertura de portas. Por isso, o build Java, os testes de integração Java e a inspeção visual no navegador precisam ser executados em um ambiente com esses recursos. O Dockerfile executa `mvn verify` e a CI cobre a inicialização com PostgreSQL real.


## TESTES:

```bash
mvn verify -Pe2e                              # testes de navegação no Chromium (sem janela)
mvn verify -Pe2e -Dbrowser=firefox            # mesmos testes no Firefox
mvn verify -Pe2e -Dheaded=true -DslowMo=500   # janela aberta em câmera lenta, bom para a apresentação
mvn verify                                    # testes de backend (catálogo, pedidos e perfis)
```
