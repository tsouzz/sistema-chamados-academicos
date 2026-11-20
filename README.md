# Sistema de Gerenciamento de Chamados Acadêmicos

Sistema web de **Help Desk / Service Desk educacional** para centralizar, triar e acompanhar as solicitações feitas por alunos e professores a setores como Secretaria, TI, Coordenação e Infraestrutura.

> Trabalho da disciplina **SPOBDD2 – Banco de Dados**, do curso de Bacharelado em Sistemas de Informação do **IFSP – Campus São Paulo**, sob orientação do Prof. Dr. Francisco Veríssimo Luciano.

---

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Funcionalidades](#2-funcionalidades)
3. [Perfis de acesso](#3-perfis-de-acesso)
4. [Arquitetura e tecnologias](#4-arquitetura-e-tecnologias)
5. [Estrutura do projeto](#5-estrutura-do-projeto)
6. [Modelo de dados](#6-modelo-de-dados)
7. [Objetos programáveis do banco (PL/pgSQL)](#7-objetos-programáveis-do-banco-plpgsql)
8. [Pré-requisitos](#8-pré-requisitos)
9. [Como executar](#9-como-executar)
10. [Usuários de exemplo](#10-usuários-de-exemplo)
11. [Rotas da aplicação](#11-rotas-da-aplicação)
12. [Testes](#12-testes)
13. [Implantação em produção](#13-implantação-em-produção)
14. [Segurança](#14-segurança)
15. [Requisitos do projeto](#15-requisitos-do-projeto)
16. [Equipe](#16-equipe)

---

## 1. Visão geral

Nas instituições de ensino, as solicitações a secretarias e departamentos costumam ser tratadas de forma descentralizada (e-mails, formulários de papel, atendimento de balcão), o que gera perda de informação, lentidão e dificuldade de acompanhamento. Este sistema resolve o problema com uma plataforma única que:

- centraliza a abertura e o acompanhamento de chamados online;
- faz a **triagem automática** (setor responsável e prioridade padrão definidos pela categoria);
- mantém uma **trilha de auditoria imutável** das mudanças de status, garantida diretamente no banco de dados;
- encerra automaticamente chamados parados por inatividade do solicitante;
- oferece **indicadores gerenciais** por departamento e exportação de relatórios em **CSV e PDF**.

Uma parte importante das regras de negócio é implementada **no próprio PostgreSQL** (Triggers, Stored Procedure, Functions e View), em vez de apenas na aplicação.

## 2. Funcionalidades

| RF | Funcionalidade |
|----|----------------|
| RF01 | **Cadastrar chamado** – título (até 120 caracteres), descrição (até 500), categoria e anexos opcionais; o solicitante é o usuário logado |
| RF02 | **Triagem automática** – status inicial `ABERTO`, prioridade padrão e departamento definidos pela categoria |
| RF03 | **Consultar e filtrar** chamados por status e categoria, com isolamento por perfil |
| RF04 | **Visualizar detalhes** – ficha completa, dias de tramitação (calculados no banco), anexos e linha do tempo do histórico |
| RF05 | **Atualizar chamado** – status, prioridade, responsável e observações, com controle de concorrência otimista (`versao`) |
| RF06 | **Cancelar chamado** (Gestor) – exclusão lógica, sem remoção física |
| RF07 | **Histórico de alterações** gravado por trigger em cada transição de status |
| RF08 | **Encerramento automático** de chamados em `AGUARDANDO_SOLICITANTE` há 15 dias ou mais (Stored Procedure) |
| RF09 | **Indicadores e relatórios** – painel analítico, View consolidada e exportação CSV/PDF |
| RF10 | **Autenticação e controle de acesso** por e-mail institucional e senha, com painel específico por perfil |
| RF11 | **Gerenciamento de anexos** – envio, download e exclusão (até 10 MB por arquivo) |

**Ciclo de vida do chamado:** `ABERTO` → `EM_ANALISE` ⇄ `AGUARDANDO_SOLICITANTE` → `CONCLUIDO` ou `CANCELADO`.
Ao concluir ou cancelar, a observação de atendimento é obrigatória.

**Anexos:** extensões permitidas `pdf`, `png`, `jpg`, `jpeg`, `txt`, `log`, `doc`, `docx`, `xlsx`, `csv` e `zip`, com limite de 10 MB por arquivo (25 MB por requisição). Os arquivos são gravados em disco com nome UUID, e os metadados ficam no banco.

## 3. Perfis de acesso

Controle de acesso baseado em papéis (RBAC):

| Perfil | Papel no sistema | O que pode fazer |
|--------|------------------|------------------|
| **Solicitante** | `ALUNO` / `PROFESSOR` | Abrir chamados, acompanhar apenas os próprios, anexar arquivos |
| **Atendente** | `TECNICO` | Ver todos os chamados, atualizar status/prioridade/responsável/observações |
| **Administrador** | `GESTOR` | Tudo o que o Técnico faz, mais cancelamento administrativo, painel de indicadores e encerramento de inativos |

O Gestor herda as permissões operacionais do Técnico.

## 4. Arquitetura e tecnologias

Arquitetura cliente-servidor em camadas, baseada em **MVC**, com camadas adicionais de serviço, segurança, DTO e tratamento de exceções.

```
Navegador ──HTTP──► Controller ──► Service / Factory ──► Repository (JPA) ──JDBC/HikariCP──► PostgreSQL
   (Thymeleaf)        │                   │                                                  (Triggers, Procedure,
                      └─ Security         └─ RotinasBancoService (JdbcTemplate)               Functions, View)
```

| Camada | Tecnologia |
|--------|-----------|
| Front-end | Thymeleaf, HTML5, Bootstrap 5.3.3, JavaScript puro (`app.js`), Chart.js 4.4.4, `thymeleaf-extras-springsecurity6` |
| Back-end | Java 21, Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA/Hibernate, Spring Security (BCrypt), Bean Validation, Actuator, Lombok |
| Relatórios | OpenPDF 2.0.3 (PDF) e exportação nativa em CSV (UTF-8 com BOM) |
| Banco de dados | PostgreSQL 16+, extensões `pg_trgm` e `pgcrypto` |
| Migrações | Flyway (com `ddl-auto: validate` no Hibernate) |
| Pool de conexões | HikariCP |
| Testes | JUnit 5, Mockito, Spring Security Test, Testcontainers (PostgreSQL) |
| Build | Apache Maven (Maven Wrapper) |

**Padrões de projeto:** DTO (`ChamadoCadastroDTO`, `ChamadoAtualizacaoDTO`, `IndicadorDepartamento`) e Factory (`ChamadoFactory`, que define prioridade, status inicial e datas na triagem).

## 5. Estrutura do projeto

```
chamados/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/
    │   ├── java/br/edu/ifsp/chamados/
    │   │   ├── ChamadosApplication.java
    │   │   ├── controller/   # Auth, Chamado, Dashboard, Indicador
    │   │   ├── service/      # ChamadoService, AnexoService, RelatorioService, RotinasBancoService
    │   │   ├── factory/      # ChamadoFactory (triagem automática)
    │   │   ├── dto/          # ChamadoCadastroDTO, ChamadoAtualizacaoDTO, IndicadorDepartamento
    │   │   ├── model/        # Entidades JPA e enums (Papel, Status, Prioridade)
    │   │   ├── repository/   # Repositórios Spring Data
    │   │   ├── security/     # SecurityConfig, CustomUserDetailsService, UsuarioAutenticado
    │   │   └── exception/    # RegraNegocioException, GlobalExceptionHandler
    │   └── resources/
    │       ├── application.yaml          # Configuração padrão
    │       ├── application-dev.yaml      # Perfil de desenvolvimento (carga de dados)
    │       ├── db/migration/
    │       │   ├── V1__schema.sql            # DDL: tabelas, constraints e índices
    │       │   └── V2__objetos_plpgsql.sql   # Triggers, Procedure, Functions e View
    │       ├── db/dev/
    │       │   └── V100__dados_exemplo.sql   # Dados fictícios (apenas perfil dev)
    │       ├── static/css/app.css
    │       ├── static/js/app.js
    │       └── templates/
    │           ├── login.html
    │           ├── fragments/layout.html
    │           ├── dashboard/    # solicitante, tecnico, gestor
    │           ├── chamados/     # lista, novo, detalhe, editar
    │           ├── indicadores/  # painel
    │           └── error/        # 403, 404, 500
    └── test/java/br/edu/ifsp/chamados/
        ├── ChamadosApplicationTests.java
        ├── service/              # ChamadoServiceTest, AnexoServiceTest
        └── integration/          # BancoPostgreSqlIntegrationTest
```

## 6. Modelo de dados

Modelo relacional normalizado até a **3FN**, com seis tabelas:

| Tabela | Descrição |
|--------|-----------|
| `departamento` | Setores atendidos: TI, SEC, COORD e INFRA |
| `categoria` | Tipos de chamado; define o departamento e a prioridade padrão |
| `usuario` | Usuários autenticáveis (senha em hash BCrypt, papel e departamento opcional) |
| `chamado` | Entidade central: solicitação, status, prioridade, datas, responsável, último editor e `versao` |
| `historico_chamado` | Trilha de auditoria **imutável** das transições de status |
| `anexo_chamado` | Metadados dos arquivos anexados |

```
departamento 1──N categoria 1──N chamado N──1 usuario (solicitante)
departamento 0..1──N usuario              N──0..1 usuario (responsável)
                                          N──0..1 usuario (último editor)
chamado 1──N historico_chamado
chamado 1──N anexo_chamado
```

O departamento de um chamado **não é armazenado** na tabela `chamado`: é derivado de `categoria` (3FN).

**Integridade de domínio** (restrições `CHECK`):

- `papel`: `ALUNO`, `PROFESSOR`, `TECNICO`, `GESTOR` (e `TECNICO` exige departamento);
- `status`: `ABERTO`, `EM_ANALISE`, `AGUARDANDO_SOLICITANTE`, `CONCLUIDO`, `CANCELADO`;
- `prioridade`: `BAIXA`, `MEDIA`, `ALTA`, `URGENTE`;
- `tamanho_bytes > 0` nos anexos; `UNIQUE` em sigla de departamento, e-mail de usuário e nome armazenado de arquivo.

**Índices:** B-Tree em chaves estrangeiras, status e datas; GIN com `pg_trgm` em `chamado.titulo`.

## 7. Objetos programáveis do banco (PL/pgSQL)

Definidos em `V2__objetos_plpgsql.sql`:

| Objeto | Tipo | Função |
|--------|------|--------|
| `trg_data_conclusao_chamado` | Trigger (`BEFORE INSERT OR UPDATE`) | Preenche `data_conclusao` ao entrar em `CONCLUIDO`/`CANCELADO` e limpa ao voltar a um status ativo |
| `trg_auditoria_chamado` | Trigger (`AFTER INSERT OR UPDATE OF status`) | Registra abertura e cada mudança de status em `historico_chamado` |
| `trg_historico_imutavel` | Trigger (`BEFORE UPDATE OR DELETE`) | Bloqueia alteração e exclusão do histórico |
| `sp_encerrar_chamados_inativos(p_dias_limite)` | Stored Procedure | Cancela chamados em `AGUARDANDO_SOLICITANTE` sem atualização há N dias (padrão 15) |
| `fn_dias_resolucao_chamado(p_id_chamado)` | Function | Dias de tramitação de um chamado |
| `fn_indicadores()` | Function | Total, quantidade por status, tempo médio de conclusão e taxa de resolutividade por departamento |
| `vw_chamados_detalhados` | View | Consolida chamado, categoria, departamento, solicitante e responsável, com dias de tramitação |

Exemplos de uso direto no banco:

```sql
SELECT * FROM vw_chamados_detalhados ORDER BY id_chamado DESC;
SELECT * FROM fn_indicadores();
SELECT fn_dias_resolucao_chamado(1) AS dias_em_tramitacao;
CALL sp_encerrar_chamados_inativos(15);
```

## 8. Pré-requisitos

- **JDK 21**
- **PostgreSQL 16 ou superior** (o Maven Wrapper dispensa instalar o Maven)
- **Docker** em execução, necessário apenas para os testes de integração (Testcontainers)
- Permissão para criar as extensões `pg_trgm` e `pgcrypto` no banco (o usuário `postgres` já possui)

## 9. Como executar

### 9.1. Clonar o repositório

```bash
git clone <url-do-repositorio>
cd chamados
```

### 9.2. Criar o banco de dados

```sql
CREATE DATABASE chamados;
```

A aplicação espera o banco em `jdbc:postgresql://localhost:5432/chamados` (ajuste em `application.yaml` se necessário).

### 9.3. Configurar as credenciais

O `application.yaml` lê as credenciais das variáveis de ambiente `DB_USERNAME` e `DB_PASSWORD`.

**Linux / macOS**

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=sua_senha
```

**Windows (PowerShell)**

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "sua_senha"
```

No **IntelliJ IDEA**, as variáveis também podem ser definidas em *Run → Edit Configurations → Environment variables*.

> ⚠️ **Atenção:** o Spring Boot **não** lê automaticamente o arquivo `.env`, e o `.env` do projeto usa os nomes `USERNAME` e `PASSWORD` (diferentes de `DB_USERNAME` e `DB_PASSWORD`). Exporte as variáveis conforme acima ou ajuste o `.env` à sua forma de carregá-lo. Além disso, o `.env` **não** está no `.gitignore` — não envie credenciais reais ao repositório.

### 9.4. Executar em desenvolvimento (com dados de exemplo)

O perfil `dev` adiciona `db/dev` às migrações do Flyway, populando departamentos, categorias, usuários e chamados fictícios:

```bash
# Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Windows
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Na primeira inicialização, o Flyway cria automaticamente as tabelas, restrições, índices e rotinas PL/pgSQL. Depois, acesse **http://localhost:8080** (a aplicação redireciona para o login).

### 9.5. Gerar o artefato executável

```bash
./mvnw clean package
java -jar target/chamados-0.0.1-SNAPSHOT.jar
```

O `package` executa toda a suíte de testes (veja a [seção 12](#12-testes)). Para pular os testes: `./mvnw clean package -DskipTests`.

### 9.6. Configurações relevantes

| Propriedade | Valor padrão | Descrição |
|-------------|--------------|-----------|
| `server.port` | `8080` | Porta HTTP |
| `server.servlet.session.timeout` | `30m` | Expiração da sessão |
| `spring.servlet.multipart.max-file-size` | `10MB` | Limite por arquivo anexo |
| `spring.servlet.multipart.max-request-size` | `25MB` | Limite por requisição |
| `app.upload.dir` | `${user.home}/chamados-uploads` | Diretório físico dos anexos |

## 10. Usuários de exemplo

Disponíveis apenas com o perfil `dev` (carga `V100__dados_exemplo.sql`). **Senha de todos: `senha123`**

| Nome | E-mail | Papel | Departamento |
|------|--------|-------|--------------|
| Ewerton Godinho Monteiro | `ewerton.monteiro@aluno.ifsp.edu.br` | ALUNO | — |
| Professor de Exemplo | `professor.exemplo@ifsp.edu.br` | PROFESSOR | — |
| Dennis Daigo Olher | `dennis.olher@ifsp.edu.br` | TECNICO | TI |
| Lucas Eduardo de Araujo Silva | `lucas.silva@ifsp.edu.br` | TECNICO | SEC |
| Técnico de Infraestrutura | `tecnico.infra@ifsp.edu.br` | TECNICO | INFRA |
| Thúlio Souza Alves | `thulio.alves@ifsp.edu.br` | GESTOR | — |

**Categorias de exemplo:** Suporte técnico (TI, `ALTA`), Documentos (SEC, `MEDIA`), Matrícula (COORD, `ALTA`), Atendimento acadêmico (COORD, `BAIXA`) e Infraestrutura (INFRA, `MEDIA`).

A carga inclui chamados em todos os status do sistema para facilitar a validação funcional.

> Estas credenciais são **somente para desenvolvimento**. Nunca use o perfil `dev` em produção.

## 11. Rotas da aplicação

| Método | Rota | Acesso | Descrição |
|--------|------|--------|-----------|
| GET | `/login` | Público | Tela de login |
| GET | `/dashboard` | Autenticado | Painel inicial conforme o perfil |
| GET | `/chamados` | Autenticado | Listagem com filtros por status e categoria |
| GET | `/chamados/exportar/csv` | Autenticado | Exporta a listagem filtrada em CSV |
| GET | `/chamados/exportar/pdf` | Autenticado | Exporta a listagem filtrada em PDF |
| GET | `/chamados/novo` | Autenticado | Formulário de novo chamado |
| POST | `/chamados` | Autenticado | Cria o chamado |
| GET | `/chamados/{id}` | Autenticado | Detalhes, anexos e histórico |
| POST | `/chamados/{id}/anexos` | Autenticado | Envia anexos |
| GET | `/chamados/{id}/anexos/{anexoId}/download` | Autenticado | Baixa um anexo |
| POST | `/chamados/{id}/anexos/{anexoId}/excluir` | Autor do envio ou Gestor | Remove um anexo |
| GET/POST | `/chamados/{id}/editar` | Técnico, Gestor | Atualiza o atendimento |
| POST | `/chamados/{id}/excluir` | Gestor | Cancela o chamado (exclusão lógica) |
| GET | `/indicadores` | Gestor | Painel de indicadores por departamento |
| POST | `/indicadores/encerrar-inativos` | Gestor | Executa `sp_encerrar_chamados_inativos(15)` |

## 12. Testes

A suíte fica em `src/test/java/br/edu/ifsp/chamados/`:

| Classe | Tipo | O que valida |
|--------|------|--------------|
| `ChamadosApplicationTests` | Contexto | Inicialização do Spring, injeção de dependências e perfis |
| `ChamadoServiceTest` | Unitário | Triagem automática, transições de status, atualização técnica e bloqueio de operações inválidas (`RegraNegocioException`) |
| `AnexoServiceTest` | Unitário | Validações de anexos antes da gravação |
| `BancoPostgreSqlIntegrationTest` | Integração | Migrações V1/V2 em PostgreSQL real, chaves estrangeiras e `CHECK`, triggers de auditoria e rotinas de `RotinasBancoService` e `RelatorioService` |

O teste de integração usa **Testcontainers** com a imagem `postgres:16-alpine`, portanto o **Docker precisa estar em execução**.

```bash
./mvnw test
```

## 13. Implantação em produção

1. **Provisionar** servidor com JDK 21 e PostgreSQL; criar o banco e um usuário de banco com permissões restritas à aplicação.
2. **Parametrizar** a URL JDBC e as credenciais (`DB_USERNAME` / `DB_PASSWORD`) por variáveis de ambiente ou `application.yaml`.
3. **Manter o perfil `dev` desativado**, para que o Flyway execute somente `V1__schema.sql` e `V2__objetos_plpgsql.sql`, ignorando a carga fictícia.
4. **Compilar e testar:** `./mvnw clean package`.
5. **Executar:** `java -jar target/chamados-0.0.1-SNAPSHOT.jar`. Na primeira inicialização, o Flyway cria o esquema e as rotinas.
6. **Carga inicial institucional:** cadastrar departamentos, categorias (com prioridades padrão) e contas administrativas, já que o seed de exemplo não roda em produção.
7. Garantir que o diretório de anexos (`app.upload.dir`) exista, tenha permissão de escrita e esteja incluído na rotina de backup.

## 14. Segurança

- Senhas armazenadas apenas como **hash BCrypt**.
- Autenticação por formulário e autorização por papel com **Spring Security**.
- Cookies de sessão `HttpOnly` e `SameSite=Lax`.
- Validação de extensão e de caminho dos anexos contra **Path Traversal**; arquivos gravados com nome UUID.
- Erros tratados sem expor stack trace ao usuário (páginas dedicadas 403, 404 e 500).
- **Controle de concorrência otimista** (`@Version`) para evitar sobrescrita simultânea.
- Tabela `historico_chamado` **imutável no próprio SGBD**.
- Exclusão **lógica** de chamados (status `CANCELADO`), preservando integridade referencial e histórico.

## 15. Requisitos do projeto

Requisitos não funcionais atendidos:

| RNF | Tema | Resumo |
|-----|------|--------|
| RNF01 | Segurança e privacidade | BCrypt, sessões protegidas, RBAC, proteção contra Path Traversal |
| RNF02 | Usabilidade e acessibilidade | Interface responsiva (Mobile First), contadores de caracteres, link de salto, foco visível |
| RNF03 | Desempenho e concorrência | HikariCP, `open-in-view` desabilitado, índices B-Tree/GIN, `@Version` |
| RNF04 | Auditoria e integridade | Chaves estrangeiras, `CHECK`, triggers e histórico imutável |
| RNF05 | Portabilidade e confiabilidade | Flyway, `ddl-auto: validate`, Docker/Testcontainers, testes automatizados |
| RNF06 | Compatibilidade | Chrome, Firefox, Edge e Safari; CSV em UTF-8 com BOM |

O detalhamento completo (casos de uso UC01–UC09, diagramas de atividades e de classes, modelo ER e proposta comercial) está no documento do projeto, *Projeto de Banco de Dados 2*.

## 16. Equipe

| Integrante |
|-----------|
| Dennis Daigo Olher |
| Ewerton Godinho Monteiro |
| Lucas Eduardo de Araujo Silva |
| Thúlio Souza Alves |

**Instituição:** Instituto Federal de Educação, Ciência e Tecnologia de São Paulo – Campus São Paulo
**Curso:** Bacharelado em Sistemas de Informação
**Disciplina:** SPOBDD2 – Banco de Dados
**Orientador:** Prof. Dr. Francisco Veríssimo Luciano

---

*Projeto acadêmico desenvolvido em 2026.*
