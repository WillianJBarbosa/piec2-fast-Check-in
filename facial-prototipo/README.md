# Protótipo facial: comparar rosto com a foto do documento (M10)

Compara o rosto da foto de um documento com o de uma selfie e responde **compativel**, **revisar** ou **diferente**. É um protótipo local, ainda sem ligação com a API Java (isso é a M17).

## Como decide

O DeepFace calcula a distância entre os dois rostos. O `decisao.py` compara essa distância com o limiar do modelo:

| Distância / limiar | Resultado |
| --- | --- |
| até 85% | `compativel` |
| de 85% a 115% | `revisar` |
| acima de 115% | `diferente` |

O sistema **nunca reprova sozinho**: se não achar rosto, ou se a distância ficar na faixa do meio, a resposta é `revisar` e o recepcionista confere. Os limites 85% e 115% são um ponto de partida e serão ajustados com pares reais na M19. O erro mais grave é o **falso aceite** (aceitar uma pessoa diferente), por isso a faixa de revisão é larga.

## Instalação (PowerShell)

Use Python 3.9 a 3.11 (confira com `python --version`).

```powershell
cd facial-prototipo
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Na primeira execução o DeepFace baixa os modelos (algumas centenas de MB).

## Uso

```powershell
python comparar_rosto.py amostras\documento.png amostras\selfie.jpg
python comparar_rosto.py amostras\documento.png amostras\selfie.jpg --modelo ArcFace
python avaliar_pares.py          # testa vários pares do pares.csv e resume
python -m unittest -v            # testes da decisão (não precisam do DeepFace)
```

Para montar o documento de teste, use o gerador do protótipo de OCR com uma foto sua: `python ..\ocr-prototipo\gerar_documentos_ficticios.py --foto minha_selfie.jpg --saida amostras`. Depois tire outra selfie, em outra luz, para comparar.

## Teste com pares

Copie `pares.exemplo.csv` para `pares.csv` e troque pelos seus arquivos. Use **3 pares da mesma pessoa e 3 de pessoas diferentes**, no mínimo, e anote no relatório: acertos, falsos aceites, falsos rejeitos, casos enviados para revisão e o efeito de luz, óculos e foto antiga.

## Privacidade

Rosto é dado biométrico, considerado sensível pela LGPD. Use **só fotos suas ou de pessoas que autorizaram**. Nenhuma imagem é gravada pelo script, e a pasta `amostras/` e o `pares.csv` ficam fora do GitHub (`.gitignore`).

## Resultados

| Modelo | Pares iguais | Pares diferentes | Falsos aceites | Para revisão | Observações |
| --- | --- | --- | --- | --- | --- |
| Facenet512 | | | | | |

**Status:** a lógica de decisão está testada com testes automáticos; a comparação com o DeepFace ainda precisa ser executada com fotos reais autorizadas.
