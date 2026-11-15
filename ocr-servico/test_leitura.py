"""Testes do núcleo do serviço (sem FastAPI). Usam só documentos fictícios.

Rode com:  python -m unittest -v test_leitura
Os testes que leem de verdade precisam do Tesseract com o idioma português.
"""
import io
import re
import unicodedata
import unittest
from unittest import mock

from PIL import Image

import leitura  # primeiro: coloca ocr-prototipo no sys.path
import ocr_motor
import gerar_documentos_ficticios as gerador

TESSERACT = ocr_motor.disponivel("tesseract")


def normalizar(texto):
    texto = unicodedata.normalize("NFD", str(texto or ""))
    texto = "".join(c for c in texto if unicodedata.category(c) != "Mn")
    return re.sub(r"[^A-Z0-9]", "", texto.upper())


def em_bytes(imagem, formato="PNG"):
    saida = io.BytesIO()
    imagem.save(saida, format=formato)
    return saida.getvalue()


class FormatoTest(unittest.TestCase):
    def test_reconhece_pelo_conteudo(self):
        branca = Image.new("RGB", (40, 40), "white")
        self.assertEqual(leitura.formato_da_imagem(em_bytes(branca, "PNG")), "png")
        self.assertEqual(leitura.formato_da_imagem(em_bytes(branca, "JPEG")), "jpeg")
        self.assertEqual(leitura.formato_da_imagem(em_bytes(branca, "BMP")), "bmp")
        self.assertEqual(leitura.formato_da_imagem(em_bytes(branca, "WEBP")), "webp")
        self.assertIsNone(leitura.formato_da_imagem(b"%PDF-1.7 ..."))
        self.assertIsNone(leitura.formato_da_imagem(b"<html>"))


class RecusasTest(unittest.TestCase):
    def erro(self, dados, motor="tesseract"):
        with self.assertRaises(leitura.ErroDeLeitura) as contexto:
            leitura.processar(dados, motor)
        return contexto.exception

    def test_motor_invalido(self):
        e = self.erro(b"x", motor="inventado")
        self.assertEqual((e.status, e.codigo), (400, "MOTOR_INVALIDO"))

    def test_imagem_vazia(self):
        e = self.erro(b"")
        self.assertEqual((e.status, e.codigo), (422, "IMAGEM_VAZIA"))

    def test_arquivo_que_nao_e_imagem(self):
        e = self.erro(b"%PDF-1.7 isto e um pdf")
        self.assertEqual((e.status, e.codigo), (415, "FORMATO_NAO_SUPORTADO"))

    def test_imagem_corrompida(self):
        e = self.erro(b"\x89PNG\r\n\x1a\n" + b"lixo" * 20)
        self.assertEqual((e.status, e.codigo), (422, "IMAGEM_ILEGIVEL"))
        self.assertIn("Envie a foto de novo", e.mensagem)

    def test_imagem_com_pixels_demais(self):
        dados = em_bytes(Image.new("RGB", (200, 100), "white"))
        with mock.patch.object(leitura, "MAX_PIXELS", 1000):
            e = self.erro(dados)
        self.assertEqual((e.status, e.codigo), (413, "IMAGEM_GRANDE_DEMAIS"))

    def test_motor_nao_instalado(self):
        dados = em_bytes(Image.new("RGB", (200, 100), "white"))
        with mock.patch.object(ocr_motor, "disponivel", return_value=False):
            e = self.erro(dados)
        self.assertEqual((e.status, e.codigo), (503, "MOTOR_INDISPONIVEL"))

    def test_falha_do_ocr_nao_vaza_a_mensagem_original(self):
        dados = em_bytes(Image.new("RGB", (200, 100), "white"))
        with mock.patch.object(ocr_motor, "disponivel", return_value=True), \
                mock.patch.object(ocr_motor, "ler_linhas_de_imagem", side_effect=RuntimeError("CPF 52998224725")):
            e = self.erro(dados)
        self.assertEqual((e.status, e.codigo), (500, "FALHA_NO_OCR"))
        self.assertNotIn("52998224725", e.mensagem)


@unittest.skipUnless(TESSERACT, "precisa do Tesseract com o idioma português")
class LeituraRealTest(unittest.TestCase):
    def test_imagem_sem_texto_pede_nova_foto(self):
        dados = em_bytes(Image.new("RGB", (1000, 640), (240, 240, 240)))
        with self.assertRaises(leitura.ErroDeLeitura) as contexto:
            leitura.processar(dados, "tesseract")
        self.assertEqual((contexto.exception.status, contexto.exception.codigo), (422, "TEXTO_NAO_ENCONTRADO"))
        self.assertIn("boa luz", contexto.exception.mensagem)

    def test_le_cnh_ficticia_em_png_e_em_foto_jpeg(self):
        pessoa = gerador.PESSOAS[1]
        limpo = gerador.cnh_nova(pessoa, None)
        esperado_nasc = "-".join(reversed(pessoa["nasc"].split("/")))
        for dados in (em_bytes(limpo, "PNG"), em_bytes(gerador.como_foto_de_celular(limpo, 1), "JPEG")):
            r = leitura.processar(dados, "tesseract")
            c = r["campos"]
            self.assertEqual(r["tipo_documento"], "CNH")
            self.assertEqual(normalizar(c["nome"]["valor"]), normalizar(pessoa["nome"]))
            self.assertEqual(c["cpf"]["valor"], pessoa["cpf"])
            self.assertEqual(c["data_nascimento"]["valor"], esperado_nasc)
            self.assertEqual(c["numero_documento"]["valor"], pessoa["registro"])
            self.assertEqual(r["motor"], "tesseract")
            self.assertEqual(r["corpo_guest"]["documentType"], "CNH")

    def test_resposta_nao_traz_o_texto_bruto_do_ocr(self):
        pessoa = gerador.PESSOAS[1]
        r = leitura.processar(em_bytes(gerador.cnh_nova(pessoa, None)), "tesseract")
        self.assertNotIn("linhas", r)
        self.assertNotIn(normalizar(pessoa["pai"]), normalizar(str(r)))
        self.assertNotIn(normalizar(pessoa["mae"]), normalizar(str(r)))


if __name__ == "__main__":
    unittest.main()
