"""Testes da API (FastAPI) do serviço facial, com a comparação simulada. Usam só imagens sintéticas.

Rode com:  python -m unittest -v test_api
Precisam de fastapi e httpx (veja requirements.txt). Não precisam do DeepFace.
"""
import base64
import io
import os
import threading
import unittest
from unittest import mock

try:
    from fastapi.testclient import TestClient
except ImportError:  # pragma: no cover
    TestClient = None

from PIL import Image

import verificacao

if TestClient:
    import app as servico

RESULTADO = {"resultado": "compativel", "precisa_revisao": False, "modelo": "ArcFace", "razao": 0.5}


def imagem_b64():
    saida = io.BytesIO()
    Image.new("RGB", (40, 40), (120, 80, 60)).save(saida, format="PNG")
    return base64.b64encode(saida.getvalue()).decode()


CORPO = {"documento": imagem_b64(), "selfie": imagem_b64()}


@unittest.skipIf(TestClient is None, "instale fastapi e httpx (pip install -r requirements.txt)")
class ApiTest(unittest.TestCase):
    def setUp(self):
        self.cliente = TestClient(servico.app, raise_server_exceptions=False)
        self.sem_chave = mock.patch.dict(os.environ, {"FACIAL_API_KEY": ""})
        self.sem_chave.start()
        self.addCleanup(self.sem_chave.stop)

    def test_health(self):
        r = self.cliente.get("/health")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["status"], "UP")

    def test_devolve_o_resultado_sem_cache(self):
        with mock.patch.object(verificacao, "verificar", return_value=RESULTADO) as verificar:
            r = self.cliente.post("/comparar", json=CORPO)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), RESULTADO)
        self.assertEqual(r.headers["cache-control"], "no-store")
        self.assertEqual(verificar.call_args.args[0], CORPO)

    def test_exige_json(self):
        r = self.cliente.post("/comparar", content=b"x", headers={"Content-Type": "image/png"})
        self.assertEqual(r.status_code, 415)
        self.assertEqual(r.json()["codigo"], "FORMATO_NAO_SUPORTADO")

    def test_nao_aceita_upload_multipart(self):
        r = self.cliente.post("/comparar", files={"selfie": ("s.png", b"x", "image/png")})
        self.assertEqual(r.status_code, 415)

    def test_json_quebrado(self):
        r = self.cliente.post("/comparar", content=b"{nao e json", headers={"Content-Type": "application/json"})
        self.assertEqual(r.status_code, 422)
        self.assertEqual(r.json()["codigo"], "CORPO_INVALIDO")

    def test_recusa_corpo_maior_que_o_limite(self):
        with mock.patch.object(servico, "MAX_BYTES", 10):
            r = self.cliente.post("/comparar", json={"documento": "A" * 5000, "selfie": "A" * 5000})
        self.assertEqual(r.status_code, 413)
        self.assertEqual(r.json()["codigo"], "CORPO_GRANDE_DEMAIS")

    def test_repassa_erro_claro_de_verificacao(self):
        erro = verificacao.ErroDeVerificacao(422, "IMAGEM_INVALIDA", "A imagem 'selfie' não está em Base64 válido.")
        with mock.patch.object(verificacao, "verificar", side_effect=erro):
            r = self.cliente.post("/comparar", json=CORPO)
        self.assertEqual(r.status_code, 422)
        self.assertEqual(r.json(), {"codigo": "IMAGEM_INVALIDA", "mensagem": "A imagem 'selfie' não está em Base64 válido."})

    def test_responde_429_quando_esta_ocupado(self):
        cheio = threading.BoundedSemaphore(1)
        cheio.acquire()
        with mock.patch.object(servico, "_vagas", cheio):
            r = self.cliente.post("/comparar", json=CORPO)
        self.assertEqual(r.status_code, 429)
        self.assertEqual(r.json()["codigo"], "SERVICO_OCUPADO")

    def test_libera_a_vaga_depois_de_um_erro(self):
        erro = verificacao.ErroDeVerificacao(422, "IMAGEM_ILEGIVEL", "x")
        with mock.patch.object(verificacao, "verificar", side_effect=erro):
            for _ in range(5):
                self.assertEqual(self.cliente.post("/comparar", json=CORPO).status_code, 422)

    def test_erro_inesperado_nao_vaza_detalhes(self):
        with mock.patch.object(verificacao, "verificar", side_effect=RuntimeError("SEGREDO")):
            r = self.cliente.post("/comparar", json=CORPO)
        self.assertEqual(r.status_code, 500)
        self.assertEqual(r.json()["codigo"], "ERRO_INTERNO")
        self.assertNotIn("SEGREDO", r.text)


@unittest.skipIf(TestClient is None, "instale fastapi e httpx (pip install -r requirements.txt)")
class ChaveDeAcessoTest(unittest.TestCase):
    def setUp(self):
        self.cliente = TestClient(servico.app, raise_server_exceptions=False)
        self.chave = mock.patch.dict(os.environ, {"FACIAL_API_KEY": "chave-de-teste"})
        self.chave.start()
        self.addCleanup(self.chave.stop)

    def post(self, cabecalhos):
        with mock.patch.object(verificacao, "verificar", return_value=RESULTADO):
            return self.cliente.post("/comparar", json=CORPO, headers=cabecalhos)

    def test_sem_chave_ou_com_chave_errada_responde_401(self):
        self.assertEqual(self.post({}).status_code, 401)
        r = self.post({"X-Api-Key": "errada"})
        self.assertEqual(r.status_code, 401)
        self.assertEqual(r.json()["codigo"], "NAO_AUTORIZADO")

    def test_com_a_chave_certa_responde_200(self):
        self.assertEqual(self.post({"X-Api-Key": "chave-de-teste"}).status_code, 200)

    def test_health_continua_publico(self):
        self.assertEqual(self.cliente.get("/health").status_code, 200)


@unittest.skipIf(TestClient is None, "instale fastapi e httpx (pip install -r requirements.txt)")
class PrivacidadeTest(unittest.TestCase):
    def setUp(self):
        self.cliente = TestClient(servico.app, raise_server_exceptions=False)
        self.sem_chave = mock.patch.dict(os.environ, {"FACIAL_API_KEY": ""})
        self.sem_chave.start()
        self.addCleanup(self.sem_chave.stop)

    def test_o_log_nao_tem_imagens(self):
        comparador = mock.Mock(return_value={"modelo": "ArcFace", "detector": "retinaface",
                                             "metrica": "cosine", "resultado": "compativel", "razao": 0.4})
        with mock.patch("comparar_rosto.comparar", comparador):
            with self.assertLogs("facial", level="INFO") as registro:
                r = self.cliente.post("/comparar", json=CORPO)
        self.assertEqual(r.status_code, 200)
        saida = "\n".join(registro.output)
        self.assertNotIn(CORPO["selfie"], saida)
        self.assertIn("resultado=compativel", saida)


if __name__ == "__main__":
    unittest.main()
