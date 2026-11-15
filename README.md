# Fast Check-in

Backend para apoiar o check-in hoteleiro, baseado no escopo do documento **Definição Ideia PIEC 2**.

O sistema deverá permitir a leitura assistida de RG/CNH, a confirmação dos dados da Ficha Nacional de Registro de Hóspedes (FNRH) e, em uma fase posterior, verificações faciais. O objetivo desta entrega inicial é estabelecer o backend em incrementos pequenos e revisáveis. Não há processamento de documentos, imagens ou biometria nesta etapa.

## Arquitetura planejada

- **Java 17 + Spring Boot:** API principal, regras de negócio, persistência e integrações hoteleiras.
- **Python + FastAPI:** serviço isolado de visão computacional/OCR, integrado à API Java somente quando suas interfaces e controles estiverem definidos.
- **Frontend web/totem:** fase posterior; não faz parte do escopo desta entrega.

O backend Java será a fonte de verdade dos registros de check-in. O serviço Python deverá receber apenas o necessário para a tarefa de visão, sem assumir a persistência do domínio hoteleiro.

## Etapas e pontos de commit

Cada etapa deve terminar com revisão e testes antes de ser commitada. As fases abaixo são sequenciais; a fase facial depende de uma revisão específica de privacidade, segurança e qualidade.

| Etapa | Escopo | Situação |
| --- | --- | --- |
| 1. Fundação da API | Projeto Spring Boot, endpoint de saúde versionado e teste HTTP | Validada: `mvn test` (1 teste) e endpoint respondendo `UP` |
| 2. Domínio e persistência | Modelos de hotel, hóspede, estadia/check-in e FNRH; banco relacional e migrações | Implementada; testes com H2 passaram |
| 3. Segurança e privacidade | Autenticação/autorização, consentimento/base legal, auditoria, retenção e proteção de dados | Em andamento: login JWT e perfis (M12) e consentimento, auditoria e retenção (M13) implementados |
| 4. Documentos e OCR | Recebimento controlado de RG/CNH, contrato com serviço FastAPI, OCR e revisão humana | Planejada |
| 5. Validação facial | Prova de vida e comparação facial, condicionadas à avaliação de risco, dados apropriados e validação de desempenho | Planejada |
| 6. Integração hoteleira | Integração REST idempotente com faturamento/gestão do hotel e tratamento de falhas | Planejada |
| 7. Operação e qualidade | Testes ponta a ponta, observabilidade, limites de carga, implantação e endurecimento operacional | Planejada |
| 8. Frontend | Fluxo simplificado do hóspede e painel de auditoria do recepcionista | Fora do backend atual |

### Critério para avançar

Implemente e valide uma etapa por vez. Revise o diff, atualize esta tabela e só então crie o commit correspondente. Evite misturar alterações de etapas diferentes no mesmo commit.

## Privacidade e dados de treinamento

RG, CNH, imagens faciais e templates biométricos são dados pessoais de alto impacto. Não use documentos ou rostos reais em testes, exemplos, logs ou commits. Até a etapa de visão, use dados sintéticos. Antes de qualquer coleta/treinamento real, defina finalidade, base legal e transparência com apoio responsável, minimização, controle de acesso, criptografia, retenção/exclusão e um processo de avaliação de riscos e vieses. Não armazene imagens ou templates por padrão.

## Etapa 1: executar

Requisitos: JDK 17 e Maven 3.9 ou superior.

```powershell
mvn test
mvn spring-boot:run
```

Com a aplicação em execução, consulte `GET http://localhost:8080/api/v1/health`. A resposta `{"status":"UP"}` indica somente que a aplicação web iniciou; não verifica banco de dados ou serviços externos, que ainda não existem nesta etapa.

## Etapa 2: persistência local

Requisitos: JDK 17 ou superior, Maven 3.9 ou superior e PostgreSQL disponível. Os testes usam H2 em memória; não precisam iniciar o PostgreSQL.

Configure as variáveis no PowerShell antes de iniciar a aplicação. O banco `fast_checkin` e o usuário informado precisam existir; o Flyway cria as tabelas e controla as versões do esquema.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/fast_checkin"
$env:DB_USERNAME = "fast_checkin"
$env:DB_PASSWORD = "sua-senha-local"

$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$env:PERSONAL_DATA_ENCRYPTION_KEY = [Convert]::ToBase64String($keyBytes)

mvn test
mvn spring-boot:run
```

Não versione a senha nem a chave. Guarde a chave gerada de forma segura e reutilize a mesma chave ao reiniciar: sem ela, os dados pessoais já cifrados não podem ser recuperados. Os testes de persistência verificam as migrações, as relações hotel/hóspede/estadia e a cifragem autenticada dos dados do hóspede. Os testes não substituem validação contra uma instância PostgreSQL real.

Os endpoints administrativos exigem login (veja a seção **Autenticação e perfis**). Use-os só localmente e com dados sintéticos.

### PostgreSQL com Docker (opcional)

Se você tem Docker, o arquivo `docker-compose.yml` sobe um PostgreSQL 16 já com o banco e o usuário `fast_checkin`:

```powershell
$env:DB_PASSWORD = "sua-senha-local"
docker compose up -d
```

Use a mesma senha em `DB_PASSWORD` ao iniciar a aplicação. As variáveis de ambiente estão listadas em `.env.example`.

A migração `V1` foi executada em um PostgreSQL 16 real e as regras do banco foram conferidas: nome de hotel único, saída depois da chegada, chaves estrangeiras e FNRH única por hóspede e estadia.

## Endpoints administrativos (CRUD)

Todos usam JSON e o prefixo `/api/v1`. Cada recurso tem `POST`, `GET` (lista), `GET /{id}`, `PUT /{id}` e `DELETE /{id}`.

| Recurso | Rota | Corpo para criar |
| --- | --- | --- |
| Hotel | `/hotels` | `{"name": "Hotel de Teste"}` |
| Hóspede | `/guests` | `{"fullName", "cpf" (11 dígitos), "dateOfBirth", "documentType" (RG ou CNH), "documentNumber", "consentAccepted": true}` (o consentimento é obrigatório só no cadastro) |
| Estadia | `/stays` (filtro `?hotelId=`) | `{"hotelId", "arrivalDate", "departureDate"}` |
| FNRH | `/fnrh-registrations` | `{"stayId", "guestId"}`; `PUT` altera só o `status` (`PENDING` ou `CONFIRMED`) |

Respostas de erro seguem `{"status", "error", "message", "details"}`: **400** dados inválidos, **404** não encontrado, **409** duplicado ou em uso (por exemplo, apagar um hotel que tem estadias), **422** falta consentimento do hóspede.

As respostas de hóspede nunca trazem o texto cifrado e mostram CPF e número do documento mascarados.

Exemplo no PowerShell, com a aplicação rodando e o `$headers` da seção **Autenticação e perfis**:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/hotels -Headers $headers `
  -ContentType "application/json" -Body '{"name":"Hotel de Teste"}'
Invoke-RestMethod http://localhost:8080/api/v1/hotels -Headers $headers
```

## Autenticação e perfis

A API usa login com token JWT. Só `GET /api/v1/health` e `POST /api/v1/auth/login` são públicos: sem token a resposta é **401**, e com o perfil errado é **403**.

| Perfil | Pode |
| --- | --- |
| `ADMIN` | tudo: hotéis, usuários, hóspedes, estadias e FNRH |
| `RECEPCIONISTA` | consultar hotéis e operar hóspedes, estadias e FNRH; não cria hotel nem gerencia usuários |

Variáveis novas (veja `.env.example`). Sem `JWT_SECRET` a aplicação **não inicia**.

```powershell
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($keyBytes)

$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "escolha-uma-senha-com-8-ou-mais-caracteres"
mvn spring-boot:run
```

O `ADMIN_USERNAME` e o `ADMIN_PASSWORD` criam o primeiro administrador na inicialização, apenas quando a tabela de usuários está vazia. Depois disso, o ADMIN cadastra os demais pelo `POST /api/v1/users`. A senha é guardada como hash BCrypt e nunca aparece nas respostas nem nos logs.

Entrar e usar o token:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType "application/json" -Body '{"username":"admin","password":"a-senha-do-admin"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

Invoke-RestMethod http://localhost:8080/api/v1/hotels -Headers $headers
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/users -Headers $headers `
  -ContentType "application/json" `
  -Body '{"username":"recepcao","password":"senha-da-recepcao-1","role":"RECEPCIONISTA"}'
```

`DELETE /api/v1/users/{id}` desativa o usuário (ele deixa de conseguir entrar). O token vale 60 minutos (`JWT_TTL_MINUTES`). Guarde o `JWT_SECRET` com o mesmo cuidado da chave de criptografia: se ele mudar, os tokens antigos deixam de valer.

## Privacidade (LGPD): consentimento, auditoria e retenção

**Consentimento.** Cadastrar um hóspede exige `"consentAccepted": true`; sem ele a resposta é **422** e nada é gravado. O cadastro registra o consentimento de `CHECKIN_FNRH` com data e usuário. Criar uma FNRH também exige consentimento ativo (**422** caso contrário). Outros consentimentos, por finalidade (`CHECKIN_FNRH`, `BIOMETRIA_FACIAL`):

| Ação | Rota |
| --- | --- |
| Conceder | `POST /api/v1/guests/{id}/consents` com `{"purpose": "BIOMETRIA_FACIAL"}` |
| Listar (com histórico) | `GET /api/v1/guests/{id}/consents` |
| Revogar | `DELETE /api/v1/guests/{id}/consents/BIOMETRIA_FACIAL` |

Revogar não apaga a linha: guarda a data da revogação como prova.

**Auditoria.** Toda leitura, criação, alteração e remoção de dados de hóspede, e toda concessão e revogação de consentimento, gera um registro com quem, o quê, qual hóspede e quando. O registro guarda só identificadores, nunca dados pessoais, e é gravado em transação própria: se a gravação falhar, a operação não entrega o dado. Consulta, só para ADMIN: `GET /api/v1/audit?guestId=<id>&limit=100`.

**Retenção.** Passado o prazo (`RETENTION_DAYS`, padrão 1825) desde o cadastro e sem estadia recente, o hóspede é anonimizado: os dados pessoais são apagados de forma irreversível, os consentimentos são revogados e o evento `ANONYMIZE` entra na auditoria. A rotina roda todo dia às 3h (`RETENTION_CRON`) e o ADMIN pode disparar com `POST /api/v1/privacy/retention/run`. O prazo padrão é só um ponto de partida do projeto: o valor real deve vir da política do hotel e de orientação jurídica.

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/audit -Headers $headers
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/privacy/retention/run -Headers $headers
```
