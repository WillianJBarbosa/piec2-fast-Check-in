# Serviço facial (M17)

API em FastAPI que recebe a foto de um documento e uma selfie e responde `compativel`, `revisar` ou `diferente`. Reaproveita a comparação e a decisão de `../facial-prototipo` (uma única cópia do código; o modelo padrão é o **ArcFace**, que foi o melhor na M10). A API Java chama este serviço (veja o README da raiz, seção "Comparação facial (M17)").

Rosto é dado biométrico (LGPD). O serviço lê as imagens **só na memória**, não grava nada em disco e **não registra no log** nenhuma imagem, só o modelo e o resultado. Use apenas fotos suas ou de pessoas que autorizaram.

## Instalação (PowerShell)

Use Python 3.9 a 3.11 (o mesmo do protótipo facial, por causa do TensorFlow/DeepFace):

```powershell
cd facial-servico
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Na primeira comparação o DeepFace baixa os modelos (algumas centenas de MB) e carrega na memória, então essa chamada demora. As seguintes são rápidas.

## Rodar

```powershell
$env:FACIAL_API_KEY = "uma-chave-sua"      # opcional em localhost, recomendada
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8002
```

Variáveis opcionais: `FACIAL_MODELO` (padrão `ArcFace`), `FACIAL_DETECTOR` (padrão `retinaface`), `FACIAL_MAX_BYTES` (por imagem, padrão 8 MB), `FACIAL_MAX_CONCURRENT` (padrão 1, o DeepFace é pesado), `FACIAL_DOCS=1` (liga o `/docs`).

## Contrato

`POST /comparar` com `Content-Type: application/json`:

```json
{"documento": "<imagem em Base64>", "selfie": "<imagem em Base64>"}
```

As imagens vão em Base64 dentro do JSON (JPEG, PNG, WEBP ou BMP) em vez de multipart, de propósito: o upload multipart pode gravar arquivo temporário no disco. Chave no cabeçalho `X-Api-Key` se `FACIAL_API_KEY` estiver definida.

Resposta **200** (sem cache; as imagens nunca voltam na resposta):

```json
{"resultado": "revisar", "precisa_revisao": true, "modelo": "ArcFace", "razao": 0.93}
```

`razao` é a distância dividida pelo limiar do modelo (até 0,85 é `compativel`; de 0,85 a 1,15, `revisar`; acima, `diferente`). Se não achar rosto em uma das imagens, o resultado é `revisar`, vem um `motivo` (por exemplo, "rosto não detectado em selfie") e não há `razao`. O serviço **nunca reprova sozinho**. `precisa_revisao` é `true` para tudo que não for `compativel`.

Erros, sempre `{"codigo", "mensagem"}`:

| Status | Código | Quando |
| --- | --- | --- |
| 401 | `NAO_AUTORIZADO` | `FACIAL_API_KEY` definida e `X-Api-Key` ausente ou errada |
| 413 | `IMAGEM_GRANDE_DEMAIS`, `CORPO_GRANDE_DEMAIS` | imagem acima do limite ou com pixels demais |
| 415 | `FORMATO_NAO_SUPORTADO` | corpo que não é JSON, ou imagem de formato desconhecido |
| 422 | `CORPO_INVALIDO`, `IMAGEM_VAZIA`, `IMAGEM_INVALIDA`, `IMAGEM_ILEGIVEL` | JSON quebrado, campo ausente, Base64 inválido ou arquivo corrompido |
| 429 | `SERVICO_OCUPADO` | já está comparando outro par |
| 500 | `ERRO_INTERNO` | falha inesperada (só o tipo do erro vai para o log) |

## Testes

```powershell
.\.venv\Scripts\python.exe -m unittest -v test_verificacao test_api
```

`test_verificacao` testa a validação das imagens e a resposta, sem DeepFace. `test_api` testa a API (chave, limites, 429, privacidade do log) com a comparação simulada. Nenhum dos dois precisa baixar modelos. Para ver o DeepFace de verdade, use o passo manual do README da raiz.

## Fora do escopo desta meta

A **prova de vida** (impedir foto de foto) e o **ajuste dos limites** com mais pares são da M19.
