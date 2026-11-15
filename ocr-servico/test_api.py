"""Testes da API (FastAPI). Usam só dados fictícios.

Rode com:  python -m unittest -v test_api
Precisam de fastapi e httpx (veja requirements.txt). Os testes de leitura real
precisam também do Tesseract com o idioma português.
"""
import io
import os
import threading
import unittest
from unittest import mock

try:
    from fastapi.testclient import TestClient
except ImportError:  # pragma: no cover
    TestClient = None

import leitura
import ocr_motor
import gerar_documentos_ficticios as gerador
from PIL import Image

if TestClient:
    import app as servico

PNG = {"Content-Type": "image/png"}
RESULTADO = {
    "tipo_documento": "CNH",
    "campos": {"nome": {"valor": "PESSOA DE TESTE", "origem": "rotulo"}},
    "precisa_revisao": False,
    "motivos": [],
    "motor": "tesseract",
    "corpo_guest": {"fullName": "PESSOA DE TESTE"},
}


def png(imagem):
    saida = io.BytesIO()
    imagem.save(saida, format="PNG")
    return saida.getvalue()


@unittest.skipIf(TestClient is None, "instale fastapi e httpx (pip install -r requirements.txt)")
class ApiTest(unittest.TestCase):
    def setUp(self):
        self.cliente = TestClient(servico.app, raise_server_exceptions=False)
        self.sem_chave = mock.patch.dict(os.environ, {"OCR_API_KEY": ""})
        self.sem_chave.start()
        self.addCleanup(self.sem_chave.stop)

    def test_health(self):
        r = self.cliente.get("/health")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["status"], "UP")

    def test_devolve_o_resultado_sem_cache(self):
        with mock.patch.object(leitura, "processar", return_value=RESULTADO) as processar:
            r = self.cliente.post("/ocr?motor=tesseract", content=b"bytes-da-imagem", headers=PNG)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), RESULTADO)
        self.assertEqual(r.headers["cache-control"], "no-store")
        processar.assert_called_once_with(b"bytes-da-imagem", "tesseract")

    def test_exige_content_type_de_imagem(self):
        r = self.cliente.post("/ocr", content=b"x", headers={"Content-Type": "application/json"})
        self.assertEqual(r.status_code, 415)
        self.assertEqual(r.json()["codigo"], "FORMATO_NAO_SUPORTADO")

    def test_nao_aceita_upload_multipart(self):
        r = self.cliente.post("/ocr", files={"file": ("doc.png", b"x", "image/png")})
        self.assertEqual(r.status_code, 415)

    def test_recusa_imagem_maior_que_o_limite(self):
        with mock.patch.object(servico, "MAX_BYTES", 10):
            r = self.cliente.post("/ocr", content=b"x" * 11, headers=PNG)
        self.assertEqual(r.status_code, 413)
        self.assertEqual(r.json()["codigo"], "IMAGEM_GRANDE_DEMAIS")

    def test_repassa_erro_claro_de_leitura(self):
        erro = leitura.ErroDeLeitura(422, "TEXTO_NAO_ENCONTRADO", "Fotografe o documento inteiro.")
        with mock.patch.object(leitura, "processar", side_effect=erro):
            r = self.cliente.post("/ocr", content=b"x", headers=PNG)
        self.assertEqual(r.status_code, 422)
        self.assertEqual(r.json(), {"codigo": "TEXTO_NAO_ENCONTRADO", "mensagem": "Fotografe o documento inteiro."})

    def test_responde_429_quando_esta_ocupado(self):
        cheio = threading.BoundedSemaphore(1)
        cheio.acquire()
        with mock.patch.object(servico, "_vagas", cheio):
            r = self.cliente.post("/ocr", content=b"x", headers=PNG)
        self.assertEqual(r.status_code, 429)
        self.assertEqual(r.json()["codigo"], "SERVICO_OCUPADO")

    def test_libera_a_vaga_depois_de_um_erro(self):
        erro = leitura.ErroDeLeitura(422, "IMAGEM_ILEGIVEL", "x")
        with mock.patch.object(leitura, "processar", side_effect=erro):
            for _ in range(5):
                self.assertEqual(self.cliente.post("/ocr", content=b"x", headers=PNG).status_code, 422)


@unittest.skipIf(TestClient is None, "instale fastapi e httpx (pip install -r requirements.txt)")
class ChaveDeAcessoTest(unittest.TestCase):
    def setUp(self):
        self.cliente = TestClient(servico.app, raise_server_exceptions=False)
        self.chave = mock.patch.dict(os.environ, {"OCR_API_KEY": "chave-de-teste"})
        self.chave.start()
        self.addCleanup(self.chave.stop)

    def post(self, cabecalhos):
        with mock.patch.object(leitura, "processar", return_value=RESULTADO):
            return self.cliente.post("/ocr", content=b"x", headers={**PNG, **cabecalhos})

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
        self.sem_chave = mock.patch.dict(os.environ, {"OCR_API_KEY": ""})
        self.sem_chave.start()
        self.addCleanup(self.sem_chave.stop)

    @unittest.skipUnless(ocr_motor.disponivel("tesseract"), "precisa do Tesseract com o idioma português")
    def test_o_log_nao_tem_dados_do_documento(self):
        pessoa = gerador.PESSOAS[1]
        dados = png(gerador.cnh_nova(pessoa, None))
        with self.assertLogs("ocr", level="INFO") as registro:
            r = self.cliente.post("/ocr?motor=tesseract", content=dados, headers=PNG)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["campos"]["cpf"]["valor"], pessoa["cpf"])
        texto = "\n".join(registro.output)
        for dado in (pessoa["nome"], pessoa["cpf"], pessoa["registro"], pessoa["nasc"]):
            self.assertNotIn(dado, texto)

    def test_erro_inesperado_vira_500_generico_sem_vazar_nem_logar_a_mensagem(self):
        with mock.patch.object(leitura, "processar", side_effect=RuntimeError("CPF 52998224725")):
            with self.assertLogs("ocr", level="ERROR") as registro:
                r = self.cliente.post("/ocr", content=b"x", headers=PNG)
        self.assertEqual(r.status_code, 500)
        self.assertEqual(r.json()["codigo"], "ERRO_INTERNO")
        self.assertNotIn("52998224725", r.text)
        self.assertNotIn("52998224725", "\n".join(registro.output))


if __name__ == "__main__":
    unittest.main()
