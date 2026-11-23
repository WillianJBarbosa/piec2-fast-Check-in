"""Testes do núcleo do serviço facial, sem FastAPI e sem DeepFace. Usam imagens sintéticas (quadrados coloridos).

Rode com:  python -m unittest -v test_verificacao
"""
import base64
import io
import unittest

from PIL import Image

import verificacao  # coloca facial-prototipo no caminho de import
import comparar_rosto  # noqa: E402
from verificacao import ErroDeVerificacao

LIMITE = 1024 * 1024


def imagem_b64(formato="PNG", tamanho=(40, 40), cor=(120, 80, 60)):
    saida = io.BytesIO()
    Image.new("RGB", tamanho, cor).save(saida, format=formato)
    return base64.b64encode(saida.getvalue()).decode()


def corpo():
    return {"documento": imagem_b64(), "selfie": imagem_b64("JPEG")}


def comparador_fixo(resultado, **extra):
    def comparar(documento, selfie, modelo=None, detector=None):
        assert isinstance(documento, bytes) and isinstance(selfie, bytes)
        return {"modelo": modelo, "detector": detector, "metrica": "cosine", "resultado": resultado, **extra}

    return comparar


class DecodificacaoTest(unittest.TestCase):
    def erro(self, campo, valor, limite=LIMITE):
        with self.assertRaises(ErroDeVerificacao) as contexto:
            verificacao.decodificar_imagem(campo, valor, limite)
        return contexto.exception

    def test_decodifica_imagem_valida(self):
        dados = verificacao.decodificar_imagem("selfie", imagem_b64(), LIMITE)
        self.assertEqual(verificacao.formato_da_imagem(dados), "png")

    def test_campo_ausente_ou_vazio(self):
        for valor in (None, "", "   ", 123):
            e = self.erro("selfie", valor)
            self.assertEqual((e.status, e.codigo), (422, "IMAGEM_VAZIA"))

    def test_base64_invalido(self):
        e = self.erro("documento", "isto não é base64!!")
        self.assertEqual((e.status, e.codigo), (422, "IMAGEM_INVALIDA"))
        self.assertIn("documento", e.mensagem)

    def test_formato_que_nao_e_imagem(self):
        e = self.erro("selfie", base64.b64encode(b"%PDF-1.4 nao e imagem").decode())
        self.assertEqual((e.status, e.codigo), (415, "FORMATO_NAO_SUPORTADO"))

    def test_imagem_corrompida(self):
        e = self.erro("selfie", base64.b64encode(b"\x89PNG\r\n\x1a\nlixo").decode())
        self.assertEqual((e.status, e.codigo), (422, "IMAGEM_ILEGIVEL"))

    def test_imagem_maior_que_o_limite(self):
        e = self.erro("selfie", imagem_b64(tamanho=(200, 200)), limite=100)
        self.assertEqual((e.status, e.codigo), (413, "IMAGEM_GRANDE_DEMAIS"))

    def test_imagem_com_pixels_demais(self):
        antigo = verificacao.MAX_PIXELS
        verificacao.MAX_PIXELS = 100
        self.addCleanup(setattr, verificacao, "MAX_PIXELS", antigo)
        e = self.erro("selfie", imagem_b64(tamanho=(40, 40)))
        self.assertEqual((e.status, e.codigo), (413, "IMAGEM_GRANDE_DEMAIS"))

    def test_mensagens_de_erro_nao_trazem_o_conteudo_enviado(self):
        segredo = "SEGREDO-DO-HOSPEDE"
        e = self.erro("selfie", base64.b64encode(segredo.encode()).decode())
        self.assertNotIn(segredo, e.mensagem)


class VerificarTest(unittest.TestCase):
    def test_compativel_nao_precisa_de_revisao(self):
        r = verificacao.verificar(corpo(), LIMITE, comparador=comparador_fixo("compativel", razao=0.5))
        self.assertEqual(r["resultado"], "compativel")
        self.assertFalse(r["precisa_revisao"])
        self.assertEqual(r["razao"], 0.5)
        self.assertEqual(r["modelo"], verificacao.MODELO)

    def test_revisar_e_diferente_precisam_de_revisao(self):
        for resultado in ("revisar", "diferente"):
            r = verificacao.verificar(corpo(), LIMITE, comparador=comparador_fixo(resultado, razao=1.3))
            self.assertTrue(r["precisa_revisao"], resultado)

    def test_sem_rosto_vai_para_revisao_com_motivo(self):
        r = verificacao.verificar(corpo(), LIMITE,
                                  comparador=comparador_fixo("revisar", motivo="rosto não detectado em selfie"))
        self.assertEqual(r["resultado"], "revisar")
        self.assertIn("selfie", r["motivo"])
        self.assertNotIn("razao", r)

    def test_a_resposta_nao_devolve_as_imagens(self):
        r = verificacao.verificar(corpo(), LIMITE, comparador=comparador_fixo("compativel", razao=0.4))
        self.assertEqual(set(r), {"resultado", "precisa_revisao", "modelo", "razao"})

    def test_modelo_e_detector_configuraveis(self):
        r = verificacao.verificar(corpo(), LIMITE, modelo="Facenet512", detector="opencv",
                                  comparador=comparador_fixo("compativel"))
        self.assertEqual(r["modelo"], "Facenet512")

    def test_corpo_que_nao_e_objeto(self):
        with self.assertRaises(ErroDeVerificacao) as contexto:
            verificacao.verificar(["x"], LIMITE)
        self.assertEqual(contexto.exception.codigo, "CORPO_INVALIDO")

    def test_falta_de_um_campo(self):
        with self.assertRaises(ErroDeVerificacao) as contexto:
            verificacao.verificar({"documento": imagem_b64()}, LIMITE)
        self.assertEqual(contexto.exception.codigo, "IMAGEM_VAZIA")
        self.assertIn("selfie", contexto.exception.mensagem)

    def test_imagem_que_o_opencv_nao_abre_vira_422(self):
        def comparador(*args, **kwargs):
            raise ValueError("Não consegui abrir a imagem enviada.")

        with self.assertRaises(ErroDeVerificacao) as contexto:
            verificacao.verificar(corpo(), LIMITE, comparador=comparador)
        self.assertEqual((contexto.exception.status, contexto.exception.codigo), (422, "IMAGEM_ILEGIVEL"))


class CarregarBytesTest(unittest.TestCase):
    """O comparador do protótipo aceita bytes, para a imagem nunca passar por disco."""

    def test_abre_bytes(self):
        saida = io.BytesIO()
        Image.new("RGB", (30, 20), (10, 20, 30)).save(saida, format="PNG")
        imagem = comparar_rosto._carregar(saida.getvalue())
        self.assertEqual(imagem.shape[:2], (20, 30))

    def test_bytes_invalidos(self):
        with self.assertRaises(ValueError):
            comparar_rosto._carregar(b"nao e imagem")

    def test_comparar_repassa_os_bytes_ao_verificador(self):
        recebidos = []

        def verificador(documento, selfie, modelo, detector, metrica):
            recebidos.append((documento, selfie))
            return 0.10, 0.40

        r = comparar_rosto.comparar(b"doc", b"self", verificador=verificador)
        self.assertEqual(recebidos, [(b"doc", b"self")])
        self.assertEqual(r["resultado"], "compativel")


if __name__ == "__main__":
    unittest.main()
