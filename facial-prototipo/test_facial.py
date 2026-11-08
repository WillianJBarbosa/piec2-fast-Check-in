"""Testes da decisão e do fluxo, sem precisar do DeepFace instalado.

Rode com:  python -m unittest -v
"""
import unittest

import avaliar_pares
import comparar_rosto
from comparar_rosto import RostoNaoDetectado
from decisao import classificar


class DecisaoTest(unittest.TestCase):
    def test_tres_niveis(self):
        self.assertEqual(classificar(0.10, 0.30), "compativel")
        self.assertEqual(classificar(0.30, 0.30), "revisar")   # no limiar do modelo ainda vai para revisão
        self.assertEqual(classificar(0.40, 0.30), "diferente")

    def test_fronteiras(self):
        # Evita valores exatamente na borda, por causa do arredondamento de decimais.
        self.assertEqual(classificar(0.25, 0.30), "compativel")   # 83% do limiar
        self.assertEqual(classificar(0.27, 0.30), "revisar")      # 90%
        self.assertEqual(classificar(0.34, 0.30), "revisar")      # 113%
        self.assertEqual(classificar(0.36, 0.30), "diferente")    # 120%

    def test_limiar_invalido(self):
        with self.assertRaises(ValueError):
            classificar(0.1, 0)


class FluxoTest(unittest.TestCase):
    def test_resultado_com_distancia_e_razao(self):
        r = comparar_rosto.comparar("d.png", "s.jpg", verificador=lambda *a: (0.12, 0.30))
        self.assertEqual(r["resultado"], "compativel")
        self.assertEqual(r["razao"], 0.4)

    def test_limiar_informado_vale_mais_que_o_do_modelo(self):
        r = comparar_rosto.comparar("d.png", "s.jpg", limiar=0.13, verificador=lambda *a: (0.12, 0.30))
        self.assertEqual(r["resultado"], "revisar")

    def test_rosto_nao_detectado_vai_para_revisao_e_nao_reprova(self):
        def sem_rosto(*args):
            raise RostoNaoDetectado("selfie")

        r = comparar_rosto.comparar("d.png", "s.jpg", verificador=sem_rosto)
        self.assertEqual(r["resultado"], "revisar")
        self.assertIn("selfie", r["motivo"])


class ResumoTest(unittest.TestCase):
    def test_contagem(self):
        linhas = [
            ("mesma", "compativel"),      # acerto
            ("diferente", "diferente"),   # acerto
            ("mesma", "revisar"),         # revisão
            ("diferente", "compativel"),  # falso aceite (grave)
            ("mesma", "diferente"),       # falso rejeito
        ]
        self.assertEqual(avaliar_pares.resumir(linhas), {
            "total": 5, "acertos": 2, "revisar": 1, "falso_aceite": 1, "falso_rejeito": 1})


if __name__ == "__main__":
    unittest.main()
