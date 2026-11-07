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
| 3. Segurança e privacidade | Autenticação/autorização, consentimento/base legal, auditoria, retenção e proteção de dados | Planejada |
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

Os endpoints administrativos da seção abaixo existem desde a semana de 09/11, mas **ainda sem autenticação** (ela entra na etapa 3). Use-os só localmente e com dados sintéticos.

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
| Hóspede | `/guests` | `{"fullName", "cpf" (11 dígitos), "dateOfBirth", "documentType" (RG ou CNH), "documentNumber"}` |
| Estadia | `/stays` (filtro `?hotelId=`) | `{"hotelId", "arrivalDate", "departureDate"}` |
| FNRH | `/fnrh-registrations` | `{"stayId", "guestId"}`; `PUT` altera só o `status` (`PENDING` ou `CONFIRMED`) |

Respostas de erro seguem `{"status", "error", "message", "details"}`: **400** dados inválidos, **404** não encontrado, **409** duplicado ou em uso (por exemplo, apagar um hotel que tem estadias).

As respostas de hóspede nunca trazem o texto cifrado e mostram CPF e número do documento mascarados.

Exemplo no PowerShell, com a aplicação rodando:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/hotels `
  -ContentType "application/json" -Body '{"name":"Hotel de Teste"}'
Invoke-RestMethod http://localhost:8080/api/v1/hotels
```

