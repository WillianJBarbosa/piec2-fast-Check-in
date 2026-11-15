"""Serviço de OCR (M14): POST /ocr recebe a imagem de um documento e devolve os campos em JSON.

Como rodar:   uvicorn app:app --host 127.0.0.1 --port 8001

Privacidade: a imagem chega nos bytes do corpo da requisição e é lida na memória.
Não usa upload multipart (que pode gravar arquivo temporário no disco), não grava
nada e não registra no log nenhum dado do documento, só o resultado da operação.
"""
import hmac
import logging
import os
import threading
import time
from typing import Optional

from fastapi import Depends, FastAPI, Header, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

import leitura
from leitura import ErroDeLeitura

log = logging.getLogger("ocr")
if not logging.getLogger().handlers:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

MAX_BYTES = int(os.environ.get("OCR_MAX_BYTES", 8 * 1024 * 1024))
_vagas = threading.BoundedSemaphore(int(os.environ.get("OCR_MAX_CONCURRENT", 2)))

_docs = os.environ.get("OCR_DOCS") == "1"
app = FastAPI(
    title="Fast Check-in OCR",
    version="0.1.0",
    docs_url="/docs" if _docs else None,
    redoc_url=None,
    openapi_url="/openapi.json" if _docs else None,
)

if not os.environ.get("OCR_API_KEY"):
    log.warning("OCR_API_KEY não definida: o serviço está SEM autenticação. Use só em localhost.")


@app.exception_handler(ErroDeLeitura)
async def _erro_de_leitura(_request: Request, erro: ErroDeLeitura):
    log.info("ocr recusado codigo=%s", erro.codigo)
    return JSONResponse(
        status_code=erro.status,
        content={"codigo": erro.codigo, "mensagem": erro.mensagem},
        headers={"Cache-Control": "no-store"},
    )


@app.exception_handler(Exception)
async def _erro_inesperado(_request: Request, erro: Exception):
    # Só o tipo do erro vai para o log: a mensagem poderia conter dados do documento.
    log.error("ocr falha inesperada tipo=%s", type(erro).__name__)
    return JSONResponse(
        status_code=500,
        content={"codigo": "ERRO_INTERNO", "mensagem": "Erro interno ao processar a imagem."},
        headers={"Cache-Control": "no-store"},
    )


def exigir_chave(x_api_key: Optional[str] = Header(default=None)):
    esperada = os.environ.get("OCR_API_KEY", "")
    if not esperada:
        return
    if not x_api_key or not hmac.compare_digest(x_api_key.encode(), esperada.encode()):
        raise ErroDeLeitura(401, "NAO_AUTORIZADO", "Chave de acesso ausente ou inválida.")


async def _ler_corpo(request: Request):
    """Lê o corpo em memória, parando assim que passar do limite."""
    declarado = request.headers.get("content-length", "")
    if declarado.isdigit() and int(declarado) > MAX_BYTES:
        raise ErroDeLeitura(413, "IMAGEM_GRANDE_DEMAIS", f"A imagem passa de {MAX_BYTES // (1024 * 1024)} MB.")
    partes, total = [], 0
    async for parte in request.stream():
        total += len(parte)
        if total > MAX_BYTES:
            raise ErroDeLeitura(413, "IMAGEM_GRANDE_DEMAIS", f"A imagem passa de {MAX_BYTES // (1024 * 1024)} MB.")
        partes.append(parte)
    return b"".join(partes)


@app.get("/health")
def health():
    return {"status": "UP", "motor": leitura.motor_padrao()}


@app.post("/ocr", dependencies=[Depends(exigir_chave)])
async def ocr(request: Request, motor: str = "auto"):
    """Corpo: os bytes da imagem (JPEG, PNG, WEBP ou BMP), com Content-Type image/*."""
    tipo = request.headers.get("content-type", "").split(";")[0].strip().lower()
    if not tipo.startswith("image/"):
        raise ErroDeLeitura(415, "FORMATO_NAO_SUPORTADO", "Envie a imagem no corpo, com Content-Type image/jpeg ou image/png.")
    dados = await _ler_corpo(request)

    if not _vagas.acquire(blocking=False):
        raise ErroDeLeitura(429, "SERVICO_OCUPADO", "O serviço está processando outros documentos. Tente em instantes.")
    inicio = time.monotonic()
    try:
        resultado = await run_in_threadpool(leitura.processar, dados, motor)
    finally:
        _vagas.release()

    log.info(
        "ocr ok motor=%s tipo=%s revisao=%s bytes=%d ms=%d",
        resultado["motor"], resultado["tipo_documento"], resultado["precisa_revisao"],
        len(dados), (time.monotonic() - inicio) * 1000,
    )
    return JSONResponse(content=resultado, headers={"Cache-Control": "no-store"})
