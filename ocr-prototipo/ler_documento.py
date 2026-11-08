"""Lê uma CNH, RG ou CIN (imagem) e imprime os campos em JSON.

Exemplos:
    python ler_documento.py amostras/cnh_nova_limpo.png
    python ler_documento.py amostras/rg_b_foto.jpg --motor tesseract --texto
    python ler_documento.py amostras/cin_limpo.png --formato api
"""
import argparse
import json
import sys

import extrator
import ocr_motor


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("imagem")
    ap.add_argument("--motor", choices=["auto", "easyocr", "tesseract"], default="auto")
    ap.add_argument("--texto", action="store_true", help="mostra também as linhas lidas pelo OCR")
    ap.add_argument("--formato", choices=["completo", "api"], default="completo",
                    help="'api' devolve o corpo do POST /api/v1/guests")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    linhas, motor = ocr_motor.ler_linhas(args.imagem, args.motor)
    resultado = extrator.extrair(linhas)
    saida = extrator.para_api(resultado) if args.formato == "api" else {**resultado, "motor": motor}
    if args.texto:
        print("--- texto lido pelo OCR ---")
        print("\n".join(linhas))
        print("---------------------------")
    print(json.dumps(saida, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
