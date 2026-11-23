"""Núcleo do serviço facial (M17): recebe duas imagens e devolve compativel, revisar ou diferente.

Não depende do FastAPI, então pode ser testado sozinho. As imagens existem só na
memória: nada é gravado em disco e nada do que foi comparado vai para o log.
O sistema nunca reprova sozinho: sem rosto ou na faixa do meio, o resultado é
"revisar" e o recepcionista confere.
"""
import base64
import binascii
import io
import os
import sys
from pathlib import Path

from PIL import Image

# A comparação e a decisão vivem em facial-prototipo (uma única cópia do código).
_NUCLEO = Path(os.environ.get("FACIAL_NUCLEO_DIR") or Path(__file__).resolve().parent.parent / "facial-prototipo")
if str(_NUCLEO) not in sys.path:
    sys.path.insert(0, str(_NUCLEO))

import comparar_rosto  # noqa: E402

MODELO = os.environ.get("FACIAL_MODELO", "ArcFace")
DETECTOR = os.environ.get("FACIAL_DETECTOR", "retinaface")
MAX_PIXELS = int(os.environ.get("FACIAL_MAX_PIXELS", 40_000_000))


class ErroDeVerificacao(Exception):
    """Erro devolvido ao cliente com um código estável e uma mensagem clara, sem dado pessoal."""

    def __init__(self, status, codigo, mensagem):
        super().__init__(mensagem)
        self.status = status
        self.codigo = codigo
        self.mensagem = mensagem


def formato_da_imagem(dados):
    """Reconhece o formato pelos primeiros bytes (não confia no que o cliente declarou)."""
    if dados.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if dados.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if dados.startswith(b"BM"):
        return "bmp"
    if dados[:4] == b"RIFF" and dados[8:12] == b"WEBP":
        return "webp"
    return None


def decodificar_imagem(campo, valor, max_bytes):
    """Converte o Base64 de um campo em bytes e confere tamanho, formato e dimensões."""
    if not isinstance(valor, str) or not valor.strip():
        raise ErroDeVerificacao(422, "IMAGEM_VAZIA", f"A imagem '{campo}' não foi enviada.")
    # Recusa antes de decodificar: o Base64 tem cerca de 4/3 do tamanho original.
    if len(valor) > (max_bytes * 4) // 3 + 8:
        raise ErroDeVerificacao(413, "IMAGEM_GRANDE_DEMAIS",
                                f"A imagem '{campo}' passa de {max_bytes // (1024 * 1024)} MB.")
    try:
        dados = base64.b64decode(valor, validate=True)
    except (binascii.Error, ValueError):
        raise ErroDeVerificacao(422, "IMAGEM_INVALIDA", f"A imagem '{campo}' não está em Base64 válido.") from None
    if not dados:
        raise ErroDeVerificacao(422, "IMAGEM_VAZIA", f"A imagem '{campo}' está vazia.")
    if len(dados) > max_bytes:
        raise ErroDeVerificacao(413, "IMAGEM_GRANDE_DEMAIS",
                                f"A imagem '{campo}' passa de {max_bytes // (1024 * 1024)} MB.")
    if formato_da_imagem(dados) is None:
        raise ErroDeVerificacao(415, "FORMATO_NAO_SUPORTADO",
                                f"A imagem '{campo}' tem formato não suportado. Envie JPEG, PNG, WEBP ou BMP.")
    try:
        with Image.open(io.BytesIO(dados)) as imagem:
            largura, altura = imagem.size
    except Exception:
        raise ErroDeVerificacao(422, "IMAGEM_ILEGIVEL",
                                f"Não consegui abrir a imagem '{campo}': o arquivo está corrompido ou incompleto.") from None
    if largura * altura > MAX_PIXELS:
        raise ErroDeVerificacao(413, "IMAGEM_GRANDE_DEMAIS",
                                f"A imagem '{campo}' tem {largura}x{altura} pixels, mais do que o aceito.")
    return dados


def verificar(corpo, max_bytes, modelo=None, detector=None, comparador=None):
    """Compara o rosto do documento com o da selfie. `corpo` é o JSON já lido: {"documento", "selfie"}."""
    if not isinstance(corpo, dict):
        raise ErroDeVerificacao(422, "CORPO_INVALIDO", "Envie um JSON com os campos 'documento' e 'selfie' em Base64.")
    documento = decodificar_imagem("documento", corpo.get("documento"), max_bytes)
    selfie = decodificar_imagem("selfie", corpo.get("selfie"), max_bytes)

    comparador = comparador or comparar_rosto.comparar
    try:
        r = comparador(documento, selfie, modelo=modelo or MODELO, detector=detector or DETECTOR)
    except ValueError:
        raise ErroDeVerificacao(422, "IMAGEM_ILEGIVEL",
                                "Não consegui abrir uma das imagens. Tire a foto de novo.") from None
    resposta = {
        "resultado": r["resultado"],
        "precisa_revisao": r["resultado"] != "compativel",
        "modelo": r["modelo"],
    }
    if "razao" in r:
        resposta["razao"] = r["razao"]
    if "motivo" in r:
        resposta["motivo"] = r["motivo"]
    return resposta
