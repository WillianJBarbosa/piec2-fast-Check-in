"""Núcleo do serviço: recebe os bytes de uma imagem e devolve os campos do documento.

Não depende do FastAPI, então pode ser testado sozinho. A imagem existe só na
memória: nada é gravado em disco e nada do que foi lido vai para o log.
"""
import io
import os
import sys
from functools import lru_cache
from pathlib import Path

from PIL import Image

# O OCR e o extrator vivem em ocr-prototipo (uma única cópia do código).
_NUCLEO = Path(os.environ.get("OCR_NUCLEO_DIR") or Path(__file__).resolve().parent.parent / "ocr-prototipo")
if str(_NUCLEO) not in sys.path:
    sys.path.insert(0, str(_NUCLEO))

import extrator  # noqa: E402
import ocr_motor  # noqa: E402

MOTORES = ("auto", "easyocr", "tesseract")
MAX_PIXELS = int(os.environ.get("OCR_MAX_PIXELS", 40_000_000))
MIN_LETRAS = 15  # abaixo disso o OCR praticamente não leu texto


class ErroDeLeitura(Exception):
    """Erro que o serviço devolve ao cliente com um código estável e uma mensagem clara."""

    def __init__(self, status, codigo, mensagem):
        super().__init__(mensagem)
        self.status = status
        self.codigo = codigo
        self.mensagem = mensagem


def formato_da_imagem(dados):
    """Reconhece o formato pelos primeiros bytes (não confia no Content-Type)."""
    if dados.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if dados.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if dados.startswith(b"BM"):
        return "bmp"
    if dados[:4] == b"RIFF" and dados[8:12] == b"WEBP":
        return "webp"
    return None


@lru_cache(maxsize=1)
def motor_padrao():
    return ocr_motor.escolher_motor("auto")


def _conferir_tamanho_em_pixels(dados):
    """Lê só o cabeçalho para recusar imagens gigantes antes de decodificá-las."""
    try:
        with Image.open(io.BytesIO(dados)) as imagem:
            largura, altura = imagem.size
    except Exception:
        raise ErroDeLeitura(
            422, "IMAGEM_ILEGIVEL",
            "Não consegui abrir a imagem: o arquivo está corrompido ou incompleto. Envie a foto de novo.",
        )
    if largura * altura > MAX_PIXELS:
        raise ErroDeLeitura(
            413, "IMAGEM_GRANDE_DEMAIS",
            f"A imagem tem {largura}x{altura} pixels, mais do que o aceito. Reduza a resolução da foto.",
        )


def processar(dados, motor="auto"):
    """Devolve o resultado da leitura ou levanta ErroDeLeitura."""
    if motor not in MOTORES:
        raise ErroDeLeitura(400, "MOTOR_INVALIDO", f"Motor desconhecido. Use um de: {', '.join(MOTORES)}.")
    if not dados:
        raise ErroDeLeitura(422, "IMAGEM_VAZIA", "A requisição não trouxe nenhuma imagem.")
    if formato_da_imagem(dados) is None:
        raise ErroDeLeitura(415, "FORMATO_NAO_SUPORTADO", "Formato não suportado. Envie JPEG, PNG, WEBP ou BMP.")

    _conferir_tamanho_em_pixels(dados)
    try:
        imagem = ocr_motor.decodificar(dados)
    except ValueError:
        raise ErroDeLeitura(
            422, "IMAGEM_ILEGIVEL",
            "Não consegui abrir a imagem: o arquivo está corrompido ou incompleto. Envie a foto de novo.",
        )

    escolhido = ocr_motor.escolher_motor(motor)
    if not ocr_motor.disponivel(escolhido):
        raise ErroDeLeitura(503, "MOTOR_INDISPONIVEL", f"O motor de OCR '{escolhido}' não está instalado no servidor.")

    try:
        linhas, usado = ocr_motor.ler_linhas_de_imagem(imagem, escolhido)
    except Exception as erro:
        # Não repassa a mensagem original: ela pode trazer pedaços do conteúdo.
        raise ErroDeLeitura(500, "FALHA_NO_OCR", "O OCR falhou ao processar a imagem.") from None

    if sum(c.isalpha() for linha in linhas for c in linha) < MIN_LETRAS:
        raise ErroDeLeitura(
            422, "TEXTO_NAO_ENCONTRADO",
            "Não encontrei texto legível. Fotografe o documento inteiro, reto, com boa luz e sem reflexo.",
        )

    resultado = extrator.extrair(linhas)
    return {**resultado, "motor": usado, "corpo_guest": extrator.para_api(resultado)}
