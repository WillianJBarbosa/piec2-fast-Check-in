"""Lê uma imagem de documento e devolve as linhas de texto.

Motores: EasyOCR (instala só com pip) e Tesseract (precisa do programa
instalado, com o idioma português). O modo "auto" usa o EasyOCR se ele estiver
instalado e, senão, o Tesseract.
"""
import os

import cv2
import numpy as np


def carregar_imagem(caminho):
    """Abre a imagem mesmo se o caminho tiver acento (comum no Windows)."""
    dados = np.fromfile(caminho, dtype=np.uint8)
    img = cv2.imdecode(dados, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError(f"Não consegui abrir a imagem: {caminho}")
    return img


def preparar(img, largura_alvo=1800):
    """Aumenta, passa para cinza e melhora o contraste. Ajuda em fotos de celular."""
    altura, largura = img.shape[:2]
    if largura < largura_alvo:
        fator = largura_alvo / largura
        img = cv2.resize(img, (int(largura * fator), int(altura * fator)), interpolation=cv2.INTER_CUBIC)
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    cinza = cv2.fastNlMeansDenoising(cinza, None, 10, 7, 21)
    return cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(cinza)


def _linhas_tesseract(img):
    import pytesseract

    comando = os.environ.get("TESSERACT_CMD")
    if comando:
        pytesseract.pytesseract.tesseract_cmd = comando
    texto = pytesseract.image_to_string(img, lang="por", config="--psm 6")
    return [l.strip() for l in texto.splitlines() if l.strip()]


_leitor_easyocr = None


def _linhas_easyocr(img):
    global _leitor_easyocr
    import easyocr

    if _leitor_easyocr is None:
        _leitor_easyocr = easyocr.Reader(["pt"], gpu=False, verbose=False)
    achados = _leitor_easyocr.readtext(img)
    caixas = []
    for pontos, texto, _confianca in achados:
        ys = [p[1] for p in pontos]
        xs = [p[0] for p in pontos]
        caixas.append({"texto": texto, "x": min(xs), "y": (min(ys) + max(ys)) / 2, "alt": max(ys) - min(ys)})
    caixas.sort(key=lambda c: c["y"])
    linhas, atual = [], []
    for c in caixas:
        if atual and abs(c["y"] - atual[-1]["y"]) > 0.6 * max(c["alt"], atual[-1]["alt"]):
            linhas.append(atual)
            atual = []
        atual.append(c)
    if atual:
        linhas.append(atual)
    return [" ".join(c["texto"] for c in sorted(l, key=lambda c: c["x"])) for l in linhas]


def disponivel(motor):
    try:
        if motor == "easyocr":
            import easyocr  # noqa: F401
        else:
            import pytesseract

            pytesseract.get_tesseract_version()
        return True
    except Exception:
        return False


def escolher_motor(motor="auto"):
    if motor != "auto":
        return motor
    return "easyocr" if disponivel("easyocr") else "tesseract"


def ler_linhas(caminho, motor="auto"):
    """Devolve (linhas, motor_usado)."""
    motor = escolher_motor(motor)
    img = preparar(carregar_imagem(caminho))
    linhas = _linhas_easyocr(img) if motor == "easyocr" else _linhas_tesseract(img)
    return linhas, motor
