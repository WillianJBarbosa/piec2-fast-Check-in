"""Testes do pré-processamento de imagem (não precisam de Tesseract nem de EasyOCR).

Rode com:  python -m unittest -v
"""
import io
import os
import unittest
from unittest import mock

import cv2
import numpy as np
from PIL import Image

import ocr_motor


def imagem_com_linhas_de_texto(largura=1000, altura=640):
    """Imagem clara com várias linhas de texto horizontais, para testar a inclinação."""
    img = np.full((altura, largura, 3), 245, dtype=np.uint8)
    for i in range(8):
        y = 70 + i * 65
        cv2.putText(img, "NOME DE TESTE DA PESSOA FICTICIA", (40, y), cv2.FONT_HERSHEY_SIMPLEX, 1.3,
                    (20, 20, 20), 3, cv2.LINE_AA)
    return img


def girar(img, graus):
    h, w = img.shape[:2]
    matriz = cv2.getRotationMatrix2D((w / 2, h / 2), graus, 1.0)
    return cv2.warpAffine(img, matriz, (w, h), borderMode=cv2.BORDER_CONSTANT, borderValue=(245, 245, 245))


def como_png(img_bgr):
    saida = io.BytesIO()
    Image.fromarray(cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)).save(saida, format="PNG")
    return saida.getvalue()


class DecodificarTest(unittest.TestCase):
    def test_abre_imagem_em_memoria(self):
        img = ocr_motor.decodificar(como_png(imagem_com_linhas_de_texto(300, 200)))
        self.assertEqual(img.shape, (200, 300, 3))

    def test_recusa_bytes_vazios_e_arquivo_que_nao_e_imagem(self):
        with self.assertRaises(ValueError):
            ocr_motor.decodificar(b"")
        with self.assertRaises(ValueError):
            ocr_motor.decodificar(b"isto nao e uma imagem")


class InclinacaoTest(unittest.TestCase):
    def test_detecta_inclinacao_e_endireita(self):
        torta = girar(imagem_com_linhas_de_texto(), 7.0)
        angulo = ocr_motor._angulo_do_texto(cv2.cvtColor(torta, cv2.COLOR_BGR2GRAY))
        self.assertGreater(abs(angulo), 5.0)
        reta = ocr_motor.endireitar(torta)
        sobra = ocr_motor._angulo_do_texto(cv2.cvtColor(reta, cv2.COLOR_BGR2GRAY))
        self.assertLess(abs(sobra), 1.5)

    def test_nao_mexe_em_imagem_que_ja_esta_reta(self):
        reta = imagem_com_linhas_de_texto()
        self.assertEqual(ocr_motor._angulo_do_texto(cv2.cvtColor(reta, cv2.COLOR_BGR2GRAY)), 0.0)
        self.assertIs(ocr_motor.endireitar(reta), reta)


class PrepararTest(unittest.TestCase):
    def test_devolve_imagem_em_cinza(self):
        pronta = ocr_motor.preparar(imagem_com_linhas_de_texto())
        self.assertEqual(pronta.ndim, 2)
        self.assertEqual(pronta.dtype, np.uint8)

    def test_reduz_foto_grande_demais(self):
        grande = np.full((1500, 4800, 3), 240, dtype=np.uint8)
        pronta = ocr_motor.preparar(grande)
        self.assertLessEqual(pronta.shape[1], 3200)

    def test_amplia_imagem_pequena(self):
        pequena = imagem_com_linhas_de_texto(900, 560)
        pronta = ocr_motor.preparar(pequena)
        self.assertGreaterEqual(pronta.shape[1], 1800)


class TesseractTest(unittest.TestCase):
    def test_disponivel_respeita_tesseract_cmd(self):
        """Com o Tesseract fora do PATH, TESSERACT_CMD precisa valer também na checagem."""
        import pytesseract

        anterior = pytesseract.pytesseract.tesseract_cmd
        self.addCleanup(setattr, pytesseract.pytesseract, "tesseract_cmd", anterior)
        falso = os.path.join("pasta", "que", "nao", "existe", "tesseract.exe")
        with mock.patch.dict(os.environ, {"TESSERACT_CMD": falso}):
            self.assertFalse(ocr_motor.disponivel("tesseract"))
            self.assertEqual(pytesseract.pytesseract.tesseract_cmd, falso)


if __name__ == "__main__":
    unittest.main()
