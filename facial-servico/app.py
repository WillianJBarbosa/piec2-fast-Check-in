"""Serviço facial (M17): POST /comparar recebe a foto do documento e a selfie e devolve compativel, revisar ou diferente.

Como rodar:   uvicorn app:app --host 127.0.0.1 --port 8002

Privacidade: rosto é dado biométrico (LGPD). As imagens chegam em Base64 num JSON
(não há upload multipart, que pode gravar arquivo temporário no disco), são lidas
na memória e descartadas. Nada é gravado e o log traz só o resultado da operação.
Esta API não decide nada sozinha: "diferente" e "revisar" sempre passam pelo recepcionista.
"""
import hmac
import json
import logging
import os
import threading
import time
from typing import Optional

from fastapi import Depends, FastAPI, Header, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

import verificacao
from verificacao import ErroDeVerificacao

log = logging.getLogger("facial")
if not logging.getLogger().handlers:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

MAX_BYTES = int(os.environ.get("FACIAL_MAX_BYTES", 8 * 1024 * 1024))  # por imagem
# O DeepFace é pesado: por padrão compara uma dupla de fotos por vez.
_vagas = threading.BoundedSemaphore(int(os.environ.get("FACIAL_MAX_CONCURRENT", 1)))

_docs = os.environ.get("FACIAL_DOCS") == "1"
app = FastAPI(
    title="Fast Check-in Facial",
    version="0.1.0",
    docs_url="/docs" if _docs else None,
    redoc_url=None,
    openapi_url="/openapi.json" if _docs else None,
)

if not os.environ.get("FACIAL_API_KEY"):
    log.warning("FACIAL_API_KEY não definida: o serviço está SEM autenticação. Use só em localhost.")


@app.exception_handler(ErroDeVerificacao)
async def _erro_de_verificacao(_request: Request, erro: ErroDeVerificacao):
    log.info("facial recusado codigo=%s", erro.codigo)
    return JSONResponse(
        status_code=erro.status,
        content={"codigo": erro.codigo, "mensagem": erro.mensagem},
        headers={"Cache-Control": "no-store"},
    )


@app.exception_handler(Exception)
async def _erro_inesperado(_request: Request, erro: Exception):
    # Só o tipo do erro vai para o log: a mensagem poderia conter dados das imagens.
    log.error("facial falha inesperada tipo=%s", type(erro).__name__)
    return JSONResponse(
        status_code=500,
        content={"codigo": "ERRO_INTERNO", "mensagem": "Erro interno ao comparar as imagens."},
        headers={"Cache-Control": "no-store"},
    )


def exigir_chave(x_api_key: Optional[str] = Header(default=None)):
    esperada = os.environ.get("FACIAL_API_KEY", "")
    if not esperada:
        return
    if not x_api_key or not hmac.compare_digest(x_api_key.encode(), esperada.encode()):
        raise ErroDeVerificacao(401, "NAO_AUTORIZADO", "Chave de acesso ausente ou inválida.")


async def _ler_json(request: Request):
    """Lê o corpo em memória, parando assim que passar do limite (duas imagens em Base64)."""
    limite = 2 * ((MAX_BYTES * 4) // 3 + 8) + 1024
    declarado = request.headers.get("content-length", "")
    if declarado.isdigit() and int(declarado) > limite:
        raise ErroDeVerificacao(413, "CORPO_GRANDE_DEMAIS", "As imagens passam do tamanho aceito.")
    partes, total = [], 0
    async for parte in request.stream():
        total += len(parte)
        if total > limite:
            raise ErroDeVerificacao(413, "CORPO_GRANDE_DEMAIS", "As imagens passam do tamanho aceito.")
        partes.append(parte)
    try:
        return json.loads(b"".join(partes))
    except ValueError:
        raise ErroDeVerificacao(422, "CORPO_INVALIDO",
                                "Envie um JSON com os campos 'documento' e 'selfie' em Base64.") from None


@app.get("/health")
def health():
    return {"status": "UP", "modelo": verificacao.MODELO, "detector": verificacao.DETECTOR}


@app.post("/comparar", dependencies=[Depends(exigir_chave)])
async def comparar(request: Request):
    """Corpo JSON: {"documento": "<Base64>", "selfie": "<Base64>"} (JPEG, PNG, WEBP ou BMP)."""
    tipo = request.headers.get("content-type", "").split(";")[0].strip().lower()
    if tipo != "application/json":
        raise ErroDeVerificacao(415, "FORMATO_NAO_SUPORTADO", "Envie um JSON (Content-Type: application/json).")
    corpo = await _ler_json(request)

    if not _vagas.acquire(blocking=False):
        raise ErroDeVerificacao(429, "SERVICO_OCUPADO", "O serviço está comparando outras fotos. Tente em instantes.")
    inicio = time.monotonic()
    try:
        resultado = await run_in_threadpool(verificacao.verificar, corpo, MAX_BYTES)
    finally:
        _vagas.release()

    log.info("facial ok modelo=%s resultado=%s ms=%d",
             resultado["modelo"], resultado["resultado"], (time.monotonic() - inicio) * 1000)
    return JSONResponse(content=resultado, headers={"Cache-Control": "no-store"})
