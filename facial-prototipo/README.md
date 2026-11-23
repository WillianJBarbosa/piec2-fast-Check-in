# Protótipo facial: comparar rosto com a foto do documento (M10)

Compara o rosto da foto de um documento com o de uma selfie e responde **compativel**, **revisar** ou **diferente**. O protótipo roda local; a API Java usa a mesma lógica pelo serviço `../facial-servico` (M17).

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
| Facenet512 | 3 | 3 | 0 | 0 | Os 3 pares da mesma pessoa saíram `diferente` (razão 1,46 a 2,19) e os 3 de pessoas diferentes também (2,19 a 2,61): as razões se sobrepõem. 3 acertos e 3 falsos rejeitos. Em fotos originais, sem o documento fictício, também errou. |
| ArcFace | 3 | 3 | 0 | 1 | 4 acertos, 1 para revisão (razão 0,93) e 1 falso rejeito, no par com a selfie 2, tirada em condição diferente (razão 1,22). Pares iguais: 0,81 a 1,22; pares diferentes: 1,26 a 1,43. |

Pares do teste com o **ArcFace** (distância dividida pelo limiar do modelo; todos usam o documento `cnh_nova_limpo.png` de cada pessoa):

| Par | Esperado | Resultado | Distância / limiar |
| --- | --- | --- | --- |
| pessoa 1: documento x selfie 1 | mesma | `revisar` | 0,930 |
| pessoa 1: documento x selfie 2 | mesma | `diferente` (falso rejeito) | 1,217 |
| pessoa 2: documento x selfie 1 | mesma | `compativel` | 0,814 |
| documento da pessoa 1 x selfie 1 da pessoa 2 | diferente | `diferente` | 1,256 |
| documento da pessoa 2 x selfie 1 da pessoa 1 | diferente | `diferente` | 1,280 |
| documento da pessoa 1 x selfie 2 da pessoa 2 | diferente | `diferente` | 1,425 |

**Observações:**

- Foram 6 pares com 2 pessoas que autorizaram o uso das fotos, em 15/11/2026. Amostra pequena demais para concluir sobre o desempenho dos modelos.
- Nenhum falso aceite (o erro mais grave) em nenhum dos dois modelos.
- O ArcFace separou melhor os pares iguais dos diferentes que o Facenet512, mas a escolha foi feita com os mesmos 6 pares usados no teste. Isso pode favorecê-lo e deve ser reconfirmado com pares novos na M19.
- Quando o `retinaface` não achou rosto em uma das fotos originais, o resultado foi `revisar`, como previsto: o sistema não reprova sozinho.
- O documento fictício não foi a causa principal dos erros do Facenet512: ele também errou em fotos originais, sem o documento.
- Luz, óculos e foto antiga ainda não foram avaliados de forma sistemática; isso entra na M19.
- A margem do ArcFace é curta: o par igual mais distante (1,217) e o par diferente mais próximo (1,256) ficam a 0,04 um do outro. O falso rejeito mostra que, quando o resultado é `diferente`, a tela do recepcionista (M17) precisa deixar conferir e seguir, para o hóspede verdadeiro não ficar barrado.

**Status:** concluído em 15/11/2026 para a M10. A lógica de decisão tem testes automáticos e a comparação com o DeepFace foi executada com fotos autorizadas (Facenet512 e ArcFace). O ajuste dos limites e a prova de vida são da M19.
