"""Testa vários pares de imagens e resume acertos e erros (para o relatório).

Crie o arquivo pares.csv (ele fica fora do GitHub). Veja pares.exemplo.csv:

    documento,selfie,esperado
    amostras/ana_doc.png,amostras/ana_selfie1.jpg,mesma
    amostras/ana_doc.png,amostras/bia_selfie1.jpg,diferente

    python avaliar_pares.py
    python avaliar_pares.py --pares pares.csv --modelo ArcFace
"""
import argparse
import csv
import sys

import comparar_rosto


def resumir(linhas):
    """Conta os resultados. 'falso_aceite' é o erro grave: aceitou uma pessoa diferente."""
    r = {"total": len(linhas), "acertos": 0, "revisar": 0, "falso_aceite": 0, "falso_rejeito": 0}
    for esperado, resultado in linhas:
        if resultado == "revisar":
            r["revisar"] += 1
        elif esperado == "mesma" and resultado == "compativel":
            r["acertos"] += 1
        elif esperado == "diferente" and resultado == "diferente":
            r["acertos"] += 1
        elif esperado == "diferente" and resultado == "compativel":
            r["falso_aceite"] += 1
        else:
            r["falso_rejeito"] += 1
    return r


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pares", default="pares.csv")
    ap.add_argument("--modelo", default="Facenet512")
    ap.add_argument("--detector", default="retinaface")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    linhas = []
    print(f"{'documento':<28}{'selfie':<28}{'esperado':<11}{'resultado':<12}razão")
    with open(args.pares, newline="", encoding="utf-8") as arquivo:
        for par in csv.DictReader(arquivo):
            r = comparar_rosto.comparar(par["documento"], par["selfie"], args.modelo, args.detector)
            linhas.append((par["esperado"], r["resultado"]))
            print(f"{par['documento']:<28}{par['selfie']:<28}{par['esperado']:<11}"
                  f"{r['resultado']:<12}{r.get('razao', r.get('motivo', ''))}")
    s = resumir(linhas)
    print(f"\nPares: {s['total']} | acertos: {s['acertos']} | para revisão: {s['revisar']} | "
          f"falsos aceites: {s['falso_aceite']} | falsos rejeitos: {s['falso_rejeito']}")


if __name__ == "__main__":
    main()
