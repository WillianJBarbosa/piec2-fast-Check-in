"""Roda o OCR em todas as imagens de amostras/ e compara com o gabarito.json.

Mostra quantos dos 4 campos (nome, CPF, nascimento, número) cada imagem acertou.
Use a tabela no relatório. Gere as imagens antes com gerar_documentos_ficticios.py.

    python avaliar_ocr.py
    python avaliar_ocr.py --motor tesseract --pasta amostras
"""
import argparse
import json
import re
import sys
import time
import unicodedata
from pathlib import Path

import extrator
import ocr_motor

CAMPOS = ["nome", "cpf", "data_nascimento", "numero_documento"]


def _normalizar(texto):
    texto = unicodedata.normalize("NFD", str(texto or ""))
    texto = "".join(c for c in texto if unicodedata.category(c) != "Mn")
    return re.sub(r"[^A-Z0-9]", "", texto.upper())


def comparar(esperado, resultado):
    """Devolve {campo: True/False} comparando sem acento, pontos e traços."""
    ok = {}
    for campo in CAMPOS:
        lido = resultado["campos"][campo]["valor"]
        ok[campo] = _normalizar(lido) == _normalizar(esperado[campo])
    return ok


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pasta", default="amostras")
    ap.add_argument("--motor", choices=["auto", "easyocr", "tesseract"], default="auto")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    pasta = Path(args.pasta)
    gabarito = json.loads((pasta / "gabarito.json").read_text(encoding="utf-8"))
    total_ok = total = 0
    print(f"{'imagem':<24}{'tipo':<7}{'nome':<6}{'cpf':<6}{'nasc':<6}{'num':<6}{'acertos':<9}{'revisão':<9}tempo")
    for arquivo, esperado in gabarito.items():
        inicio = time.time()
        linhas, _ = ocr_motor.ler_linhas(str(pasta / arquivo), args.motor)
        resultado = extrator.extrair(linhas)
        tempo = time.time() - inicio
        ok = comparar(esperado, resultado)
        acertos = sum(ok.values())
        total_ok += acertos
        total += len(CAMPOS)
        tipo_ok = "ok" if resultado["tipo_documento"] == esperado["tipo_documento"] else "ERRO"
        marca = lambda v: "ok" if v else "-"
        print(f"{arquivo:<24}{tipo_ok:<7}{marca(ok['nome']):<6}{marca(ok['cpf']):<6}"
              f"{marca(ok['data_nascimento']):<6}{marca(ok['numero_documento']):<6}"
              f"{acertos}/4{'':<6}{'sim' if resultado['precisa_revisao'] else 'não':<9}{tempo:.1f}s")
    print(f"\nTotal: {total_ok}/{total} campos corretos ({100 * total_ok / total:.0f}%)")


if __name__ == "__main__":
    main()
