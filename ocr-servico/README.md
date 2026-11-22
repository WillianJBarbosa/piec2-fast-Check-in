# Serviço OCR (M14)

API em FastAPI que recebe a foto de uma CNH, RG ou CIN e devolve nome, CPF, data de nascimento e número do documento em JSON. Reaproveita o OCR e o extrator de `../ocr-prototipo` (uma única cópia do código). A API Java chama este serviço desde a M15 (veja o README da raiz, seção "Check-in pela foto do documento").

## Instalação (PowerShell)

Use Python 3.9 a 3.12 (o mesmo do protótipo de OCR). Sem ativar o ambiente, chamando o Python do `.venv` direto:

```powershell
cd ocr-servico
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Para usar o Tesseract no lugar do EasyOCR, instale o programa (UB Mannheim, com *Portuguese*) e defina `$env:TESSERACT_CMD = "C:\Program Files\Tesseract-OCR\tesseract.exe"`. Se o OpenBLAS reclamar de memória: `$env:OPENBLAS_NUM_THREADS = "1"; $env:OMP_NUM_THREADS = "1"`.

## Rodar

```powershell
$env:OCR_API_KEY = "uma-chave-sua"          # opcional em localhost, recomendada
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```

Testar com uma imagem fictícia (gere com `python ..\ocr-prototipo\gerar_documentos_ficticios.py`):

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8001/ocr?motor=tesseract" `
  -ContentType "image/png" -InFile "..\ocr-prototipo\amostras\cnh_nova_limpo.png" `
  -Headers @{ "X-Api-Key" = $env:OCR_API_KEY }
```

## Contrato

`POST /ocr?motor=auto|easyocr|tesseract`: o corpo são os **bytes da imagem** (JPEG, PNG, WEBP ou BMP) com `Content-Type: image/*`. Não é multipart, de propósito: o upload multipart pode gravar arquivo temporário no disco.

Resposta **200**:

```json
{
  "tipo_documento": "CNH",
  "campos": {
    "nome": {"valor": "JOAO PEDRO ALMEIDA LIMA", "origem": "rotulo"},
    "cpf": {"valor": "11144477735", "origem": "rotulo", "valido": true},
    "data_nascimento": {"valor": "1985-09-03", "origem": "rotulo"},
    "numero_documento": {"valor": "98765432101", "origem": "rotulo", "tipo_numero": "REGISTRO_CNH"}
  },
  "precisa_revisao": false,
  "motivos": [],
  "motor": "tesseract",
  "corpo_guest": {"fullName": "...", "cpf": "...", "dateOfBirth": "...", "documentType": "CNH", "documentNumber": "..."}
}
```

`origem` é `rotulo` (achou o rótulo), `inferido` (regra de apoio) ou `ausente`. Qualquer campo ausente, inferido ou CPF inválido deixa `precisa_revisao: true`: o recepcionista confere antes de cadastrar. A resposta **não** traz o texto bruto do OCR (que poderia incluir filiação e outros dados que o sistema não precisa).

Erros, sempre `{"codigo", "mensagem"}`:

| Status | Código | Quando |
| --- | --- | --- |
| 401 | `NAO_AUTORIZADO` | `OCR_API_KEY` definida e o cabeçalho `X-Api-Key` ausente ou errado |
| 413 | `IMAGEM_GRANDE_DEMAIS` | acima de 8 MB ou de 40 milhões de pixels |
| 415 | `FORMATO_NAO_SUPORTADO` | `Content-Type` que não é imagem, ou arquivo que não é JPEG/PNG/WEBP/BMP |
| 422 | `IMAGEM_VAZIA`, `IMAGEM_ILEGIVEL` | corpo vazio, ou imagem corrompida |
| 422 | `TEXTO_NAO_ENCONTRADO` | o OCR não leu texto: pede para fotografar o documento inteiro, reto e com boa luz |
| 429 | `SERVICO_OCUPADO` | já há processamentos simultâneos demais |
| 503 | `MOTOR_INDISPONIVEL` | o motor pedido não está instalado |

`GET /health` devolve `{"status": "UP", "motor": "..."}` e não exige chave.

## Configuração (variáveis de ambiente)

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `OCR_API_KEY` | vazio | chave do cabeçalho `X-Api-Key`; vazia = sem autenticação (use só em localhost) |
| `OCR_MAX_BYTES` | 8388608 | tamanho máximo do corpo |
| `OCR_MAX_PIXELS` | 40000000 | área máxima da imagem |
| `OCR_MAX_CONCURRENT` | 2 | leituras simultâneas |
| `OCR_DOCS` | vazio | `1` liga `/docs` (desligado por padrão) |
| `OCR_NUCLEO_DIR` | `../ocr-prototipo` | onde estão `ocr_motor.py` e `extrator.py` |
| `TESSERACT_CMD`, `OCR_LANG` | | caminho do Tesseract e idioma (padrão `por`) |

## Privacidade

- A imagem é lida do corpo da requisição direto para a memória: **nada é gravado em disco**.
- O log registra só o resultado da operação (motor, tipo, se precisa de revisão, tamanho em bytes e tempo). Nunca nome, CPF, número, texto lido ou a imagem. Em erro inesperado, só o tipo da exceção.
- As respostas levam `Cache-Control: no-store`.
- Os dados do documento só voltam para quem chamou o serviço, que deve ser a API Java (M15), não o navegador do hóspede.
- Use **só documentos fictícios** nos testes.

## Testes

```powershell
.\.venv\Scripts\python.exe -m unittest -v
```

`test_leitura.py` testa o núcleo (precisa do Tesseract para os testes de leitura real); `test_api.py` testa as rotas, a chave de acesso e que o log não contém dados do documento (precisa de `fastapi` e `httpx`). Testes que dependem de algo não instalado são pulados.
