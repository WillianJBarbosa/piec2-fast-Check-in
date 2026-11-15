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


def decodificar(dados):
    """Abre uma imagem que já está na memória (bytes), sem passar pelo disco."""
    if not dados:
        raise ValueError("A imagem está vazia.")
    img = cv2.imdecode(np.frombuffer(dados, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Não consegui abrir a imagem: o arquivo está corrompido ou não é uma imagem aceita.")
    return img


def _angulo_do_texto(cinza, limite=10.0, passo=0.5):
    """Estima a inclinação do texto, em graus, entre -limite e +limite.

    Gira uma versão pequena e binária da imagem e escolhe o ângulo em que as
    linhas de texto ficam mais "alinhadas" (maior variação entre linhas com
    muita tinta e linhas vazias). Devolve 0 quando o ganho é pequeno, para não
    mexer em imagens que já estão retas.
    """
    altura, largura = cinza.shape[:2]
    fator = 600.0 / largura
    pequena = cv2.resize(cinza, (600, max(1, int(altura * fator))), interpolation=cv2.INTER_AREA)
    _, binaria = cv2.threshold(pequena, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    h, w = binaria.shape
    centro = (w / 2, h / 2)

    def pontuacao(angulo):
        matriz = cv2.getRotationMatrix2D(centro, angulo, 1.0)
        girada = cv2.warpAffine(binaria, matriz, (w, h), flags=cv2.INTER_NEAREST)
        return float(np.var(girada.sum(axis=1, dtype=np.float64)))

    base = pontuacao(0.0)
    melhor_angulo, melhor = 0.0, base
    for angulo in np.arange(-limite, limite + passo, passo):
        nota = pontuacao(float(angulo))
        if nota > melhor:
            melhor_angulo, melhor = float(angulo), nota
    if abs(melhor_angulo) < 1.0 or melhor < base * 1.05:
        return 0.0
    return melhor_angulo


def endireitar(img):
    """Corrige pequenas inclinações (até 10 graus) comuns em fotos tiradas à mão."""
    cinza = img if img.ndim == 2 else cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    angulo = _angulo_do_texto(cinza)
    if angulo == 0.0:
        return img
    altura, largura = img.shape[:2]
    matriz = cv2.getRotationMatrix2D((largura / 2, altura / 2), angulo, 1.0)
    return cv2.warpAffine(img, matriz, (largura, altura), flags=cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_REPLICATE)


def preparar(img, largura_alvo=1800, largura_max=3200):
    """Ajusta o tamanho, endireita, passa para cinza e melhora o contraste.

    Fotos de celular grandes demais são reduzidas (menos memória e tempo); as
    pequenas são ampliadas. Ajuda em fotos de celular.
    """
    altura, largura = img.shape[:2]
    if largura > largura_max:
        fator = largura_max / largura
        img = cv2.resize(img, (largura_max, max(1, int(altura * fator))), interpolation=cv2.INTER_AREA)
    elif largura < largura_alvo:
        fator = largura_alvo / largura
        img = cv2.resize(img, (int(largura * fator), int(altura * fator)), interpolation=cv2.INTER_CUBIC)
    img = endireitar(img)
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    cinza = cv2.fastNlMeansDenoising(cinza, None, 10, 7, 21)
    return cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(cinza)


def _pytesseract():
    """Importa o pytesseract já apontando para TESSERACT_CMD, se estiver definida."""
    import pytesseract

    comando = os.environ.get("TESSERACT_CMD")
    if comando:
        pytesseract.pytesseract.tesseract_cmd = comando
    return pytesseract


def _linhas_tesseract(img):
    pytesseract = _pytesseract()
    idioma = os.environ.get("OCR_LANG", "por")
    texto = pytesseract.image_to_string(img, lang=idioma, config="--psm 6")
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
            _pytesseract().get_tesseract_version()
        return True
    except Exception:
        return False


def escolher_motor(motor="auto"):
    if motor != "auto":
        return motor
    return "easyocr" if disponivel("easyocr") else "tesseract"


def ler_linhas_de_imagem(img, motor="auto"):
    """Devolve (linhas, motor_usado) a partir de uma imagem já aberta (BGR)."""
    motor = escolher_motor(motor)
    img = preparar(img)
    linhas = _linhas_easyocr(img) if motor == "easyocr" else _linhas_tesseract(img)
    return linhas, motor


def ler_linhas(caminho, motor="auto"):
    """Devolve (linhas, motor_usado)."""
    return ler_linhas_de_imagem(carregar_imagem(caminho), motor)
