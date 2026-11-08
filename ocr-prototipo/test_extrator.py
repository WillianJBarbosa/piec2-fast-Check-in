"""Testes do extrator com saídas de OCR de modelos diferentes (dados fictícios).

Rode com:  python -m unittest -v
"""
import unittest
from datetime import date

import extrator

HOJE = date(2026, 11, 9)


def campo(resultado, nome):
    return resultado["campos"][nome]["valor"]


class CpfTest(unittest.TestCase):
    def test_digitos_verificadores(self):
        self.assertTrue(extrator.cpf_valido("52998224725"))
        self.assertTrue(extrator.cpf_valido("11144477735"))
        self.assertFalse(extrator.cpf_valido("52998224724"))
        self.assertFalse(extrator.cpf_valido("11111111111"))
        self.assertFalse(extrator.cpf_valido("123"))


class ModelosDeDocumentoTest(unittest.TestCase):
    def test_cnh_antiga_com_rg_e_registro(self):
        linhas = [
            "REPUBLICA FEDERATIVA DO BRASIL",
            "CARTEIRA NACIONAL DE HABILITAÇÃO",
            "NOME",
            "MARIA APARECIDA DOS SANTOS",
            "DOC. IDENTIDADE / ORG. EMISSOR / UF",
            "12.345.678-9 SSP SP",
            "CPF DATA NASCIMENTO",
            "529.982.247-25 12/04/1990",
            "REGISTRO",
            "04512345678",
            "VALIDADE 10/05/2030 1ª HABILITAÇÃO 15/06/2010",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(r["tipo_documento"], "CNH")
        self.assertEqual(campo(r, "nome"), "MARIA APARECIDA DOS SANTOS")
        self.assertEqual(campo(r, "cpf"), "52998224725")
        self.assertEqual(campo(r, "data_nascimento"), "1990-04-12")
        self.assertEqual(campo(r, "numero_documento"), "04512345678")
        self.assertEqual(r["campos"]["numero_documento"]["tipo_numero"], "REGISTRO_CNH")
        self.assertFalse(r["precisa_revisao"], r["motivos"])

    def test_cnh_nova_data_local_e_uf_na_mesma_linha(self):
        linhas = [
            "CARTEIRA NACIONAL DE HABILITAÇÃO",
            "NOME E SOBRENOME",
            "JOÃO PEDRO ALMEIDA LIMA",
            "DATA, LOCAL E UF DE NASCIMENTO",
            "03/09/1985, CURITIBA, PR",
            "CPF",
            "111.444.777-35",
            "Nº REGISTRO 98765432100",
            "VALIDADE 20/01/2031",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(r["tipo_documento"], "CNH")
        self.assertEqual(campo(r, "nome"), "JOÃO PEDRO ALMEIDA LIMA")
        self.assertEqual(campo(r, "data_nascimento"), "1985-09-03")
        self.assertEqual(campo(r, "cpf"), "11144477735")
        self.assertEqual(campo(r, "numero_documento"), "98765432100")
        self.assertFalse(r["precisa_revisao"], r["motivos"])

    def test_rg_com_rotulo_acima_do_valor_e_filiacao(self):
        linhas = [
            "SECRETARIA DE SEGURANÇA PÚBLICA",
            "CARTEIRA DE IDENTIDADE",
            "REGISTRO GERAL",
            "23.456.789-X",
            "NOME",
            "ANA BEATRIZ FERREIRA COSTA",
            "FILIAÇÃO",
            "CARLOS ALBERTO COSTA",
            "LUCIA FERREIRA COSTA",
            "DATA DE NASCIMENTO",
            "21/07/1992",
            "CPF 168.995.350-09",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(r["tipo_documento"], "RG")
        self.assertEqual(campo(r, "nome"), "ANA BEATRIZ FERREIRA COSTA")
        self.assertEqual(campo(r, "numero_documento"), "23.456.789-X")
        self.assertEqual(campo(r, "data_nascimento"), "1992-07-21")
        self.assertEqual(campo(r, "cpf"), "16899535009")
        self.assertFalse(r["precisa_revisao"], r["motivos"])

    def test_rg_de_outro_estado_com_rg_na_mesma_linha(self):
        linhas = [
            "GOVERNO DO ESTADO",
            "INSTITUTO DE IDENTIFICAÇÃO",
            "RG 34.567.890-1",
            "NOME / NAME PAULO HENRIQUE ROCHA",
            "DATA NASC. 15/02/1978",
            "NATURALIDADE: RECIFE - PE",
            "CPF: 935.411.347-80",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(r["tipo_documento"], "RG")
        self.assertEqual(campo(r, "nome"), "PAULO HENRIQUE ROCHA")
        self.assertEqual(campo(r, "numero_documento"), "34.567.890-1")
        self.assertEqual(campo(r, "data_nascimento"), "1978-02-15")
        self.assertEqual(campo(r, "cpf"), "93541134780")
        self.assertFalse(r["precisa_revisao"], r["motivos"])

    def test_cin_usa_cpf_como_numero_quando_nao_ha_rg(self):
        linhas = [
            "CARTEIRA DE IDENTIDADE NACIONAL",
            "NOME",
            "FERNANDA LIMA OLIVEIRA",
            "CPF",
            "529.982.247-25",
            "DATA DE NASCIMENTO",
            "08/11/2001",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(r["tipo_documento"], "CIN")
        self.assertEqual(campo(r, "numero_documento"), "52998224725")
        self.assertEqual(r["campos"]["numero_documento"]["tipo_numero"], "CPF")
        # número inferido do CPF: o recepcionista deve conferir
        self.assertTrue(r["precisa_revisao"])


class SaidasRuinsDeOcrTest(unittest.TestCase):
    """Erros reais de OCR em foto de celular: tudo deve ir para revisão, nada some em silêncio."""

    def test_sobrenome_partido_e_rotulos_perdidos_na_cnh(self):
        linhas = [
            "CARTEIRA NACIONAL DE HABILITAÇÃO",
            "NOME E SOBRE NOME",
            "JOÃO PEDRO ALMEIDA LIMA",
            "DATA, LOCAL É UF DE NASK IMENTO",
            "03/09/1985, CURITIBA, PR",
            "111.444.777-35 98765432101",
            "VALIDADE DATA DE EMISSÃO",
            "20/01/2031 21/01/2021",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(campo(r, "nome"), "JOÃO PEDRO ALMEIDA LIMA")
        self.assertEqual(campo(r, "data_nascimento"), "1985-09-03")
        self.assertEqual(r["campos"]["data_nascimento"]["origem"], "rotulo")
        self.assertEqual(campo(r, "cpf"), "11144477735")
        self.assertEqual(campo(r, "numero_documento"), "98765432101")
        self.assertEqual(r["campos"]["numero_documento"]["origem"], "inferido")
        self.assertTrue(r["precisa_revisao"])

    def test_rg_com_espaco_no_meio_e_sem_rotulo(self):
        linhas = [
            "CARTEIRA DE IDENTIDADE",
            "REPUBLICA FEDERATIVA MODELO GENÉRICO €",
            "34.567 .890-1",
            "ANA BEATRIZ FERREIRA COSTA",
            "DATA DE NASC IMENTO pf",
            "21/07/1992 168.995.350-09",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(campo(r, "numero_documento"), "34.567.890-1")
        self.assertEqual(campo(r, "nome"), "ANA BEATRIZ FERREIRA COSTA")
        self.assertEqual(campo(r, "data_nascimento"), "1992-07-21")
        self.assertTrue(r["precisa_revisao"])


class RobustezTest(unittest.TestCase):
    def test_letra_o_no_lugar_de_zero_no_cpf(self):
        linhas = ["CARTEIRA DE IDENTIDADE", "CPF 168.995.350-O9"]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(campo(r, "cpf"), "16899535009")

    def test_letra_irreconhecivel_no_cpf_vai_para_revisao(self):
        linhas = ["CARTEIRA DE IDENTIDADE", "CPF 529.982.247-2S"]
        r = extrator.extrair(linhas, HOJE)
        self.assertIsNone(campo(r, "cpf"))
        self.assertTrue(r["precisa_revisao"])

    def test_letra_o_e_i_viram_digitos(self):
        linhas = ["CPF 111.444.777-35".replace("1", "I", 1)]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(campo(r, "cpf"), "11144477735")

    def test_nascimento_inferido_pela_data_mais_antiga(self):
        linhas = [
            "CARTEIRA NACIONAL DE HABILITAÇÃO",
            "MARCOS VINICIUS TEIXEIRA",
            "VALIDADE 10/05/2030",
            "05/03/1988",
            "15/06/2010",
        ]
        r = extrator.extrair(linhas, HOJE)
        self.assertEqual(campo(r, "data_nascimento"), "1988-03-05")
        self.assertEqual(r["campos"]["data_nascimento"]["origem"], "inferido")
        self.assertTrue(r["precisa_revisao"])

    def test_ignora_datas_impossiveis_e_futuras(self):
        linhas = ["DATA NASCIMENTO 31/02/1990", "VALIDADE 10/05/2040"]
        r = extrator.extrair(linhas, HOJE)
        self.assertIsNone(campo(r, "data_nascimento"))

    def test_numero_de_registro_da_cnh_nao_vira_cpf(self):
        linhas = ["CARTEIRA NACIONAL DE HABILITAÇÃO", "Nº REGISTRO 52998224725"]
        r = extrator.extrair(linhas, HOJE)
        self.assertIsNone(campo(r, "cpf"))
        self.assertEqual(campo(r, "numero_documento"), "52998224725")

    def test_texto_vazio_pede_revisao_sem_quebrar(self):
        r = extrator.extrair([], HOJE)
        self.assertEqual(r["tipo_documento"], "DESCONHECIDO")
        self.assertTrue(r["precisa_revisao"])
        for nome in extrator.CAMPOS:
            self.assertIsNone(campo(r, nome))

    def test_corpo_para_a_api(self):
        linhas = ["CARTEIRA DE IDENTIDADE NACIONAL", "NOME", "FERNANDA LIMA OLIVEIRA",
                  "CPF 529.982.247-25", "DATA DE NASCIMENTO 08/11/2001", "REGISTRO GERAL 45.678.901-2"]
        corpo = extrator.para_api(extrator.extrair(linhas, HOJE))
        self.assertEqual(corpo, {
            "fullName": "FERNANDA LIMA OLIVEIRA",
            "cpf": "52998224725",
            "dateOfBirth": "2001-11-08",
            "documentType": "RG",
            "documentNumber": "45.678.901-2",
        })


if __name__ == "__main__":
    unittest.main()
