# Protótipo OCR: ler CNH, RG e CIN (M09)

Script que lê a foto de um documento e devolve **nome, CPF, data de nascimento e número do documento** em JSON. É um protótipo local, ainda sem ligação com a API Java (isso é a M14 e a M15).

## Documentos de modelos diferentes

CNH antiga, CNH nova, RG de cada estado e a Carteira de Identidade Nacional (CIN) têm layouts diferentes. Por isso o `extrator.py` **não depende da posição dos campos**:

- procura rótulos conhecidos (`NOME`, `NOME E SOBRENOME`, `NOME / NAME`, `DATA NASC.`, `REGISTRO GERAL`, `Nº REGISTRO`...);
- aceita o valor na mesma linha ou na linha de baixo do rótulo;
- se o OCR perder o rótulo, usa regras de apoio (por exemplo, a data mais antiga é o nascimento) e marca o campo como `inferido`;
- valida os dígitos do CPF;
- qualquer campo ausente, inferido ou com CPF inválido faz o resultado sair com `"precisa_revisao": true`, para o recepcionista conferir.

Limitações conhecidas: o número do documento na CIN é o CPF quando não há RG impresso; documentos girados em 90 graus, muito escuros ou com reflexo não são tratados; nenhum modelo real foi testado ainda.

## Instalação (PowerShell)

```powershell
cd ocr-prototipo
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Se o PowerShell bloquear o `Activate.ps1`, rode antes `Set-ExecutionPolicy -Scope Process Bypass`.

**Alternativa com Tesseract:** instale o programa (versão para Windows da UB Mannheim) marcando o idioma *Portuguese*, depois `$env:TESSERACT_CMD = "C:\Program Files\Tesseract-OCR\tesseract.exe"` e use `--motor tesseract`.

## Uso

```powershell
python gerar_documentos_ficticios.py            # cria 10 imagens fictícias em amostras/
python ler_documento.py amostras\cnh_nova_limpo.png
python ler_documento.py amostras\rg_b_foto.jpg --texto     # mostra também o texto lido
python ler_documento.py amostras\cin_limpo.png --formato api   # corpo do POST /api/v1/guests
python avaliar_ocr.py                           # tabela de acertos por imagem
python -m unittest -v                           # testes do extrator
```

Para colar uma foto sua nos documentos fictícios (e usar na comparação facial, M10): `python gerar_documentos_ficticios.py --foto minha_selfie.jpg`.

## Resultados

Anote aqui, para o relatório (campos corretos de 4 por imagem):

| Motor | Imagens | Campos corretos | Observações |
| --- | --- | --- | --- |
| Tesseract | 10 fictícias | 40/40 | imagens geradas pelo próprio gerador, usadas para ajustar o extrator: mostra que o código funciona, não que o OCR é perfeito |
| EasyOCR | | | |

## Como adicionar um modelo novo de documento

1. Fotografe ou monte um documento **fictício** com o novo layout.
2. Rode `python ler_documento.py imagem --texto` e veja o que o OCR leu.
3. Copie as linhas para um teste novo em `test_extrator.py` (só com dados fictícios) e ajuste os rótulos no `extrator.py` até o teste passar.

## Privacidade

Use **só documentos fictícios**. Os CPFs gerados são calculados por algoritmo para teste e podem coincidir por acaso com CPFs reais: não use fora destes testes. As imagens ficam fora do GitHub (`.gitignore`).
