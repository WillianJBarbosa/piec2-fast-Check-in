"""Gera imagens de documentos FICTÍCIOS para testar o OCR.

São cinco layouts genéricos (CNH antiga, CNH nova, RG de dois estilos e CIN),
cada um em versão limpa e em versão "foto de celular" (inclinada, borrada,
menor e com JPEG). Nenhum usa símbolos oficiais, e todos trazem a faixa
"DOCUMENTO FICTÍCIO". Os CPFs são gerados por algoritmo só para teste e podem
coincidir por acaso com CPFs reais: não use fora destes testes.

Uso:
    python gerar_documentos_ficticios.py
    python gerar_documentos_ficticios.py --foto minha_selfie.jpg   (foto autorizada)
"""
import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

LARGURA, ALTURA = 1000, 640
_FONTES = ["arial.ttf", "C:/Windows/Fonts/arial.ttf", "DejaVuSans.ttf",
           "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
_FONTES_NEGRITO = ["arialbd.ttf", "C:/Windows/Fonts/arialbd.ttf", "DejaVuSans-Bold.ttf",
                   "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]


def _fonte(tamanho, negrito=False):
    for caminho in (_FONTES_NEGRITO if negrito else _FONTES):
        try:
            return ImageFont.truetype(caminho, tamanho)
        except OSError:
            continue
    return ImageFont.load_default()


def _dv(digitos, tamanho):
    soma = sum(int(digitos[i]) * (tamanho + 1 - i) for i in range(tamanho))
    return str((soma * 10) % 11 % 10)


def cpf_de_teste(base9):
    d = base9 + _dv(base9, 9)
    d += _dv(d, 10)
    return d


def formatar_cpf(d):
    return f"{d[:3]}.{d[3:6]}.{d[6:9]}-{d[9:]}"


PESSOAS = [
    {"nome": "MARIA APARECIDA DOS SANTOS", "cpf": cpf_de_teste("529982247"), "nasc": "12/04/1990",
     "rg": "12.345.678-9", "registro": "04512345678", "cidade": "SÃO PAULO", "uf": "SP",
     "pai": "JOSÉ CARLOS DOS SANTOS", "mae": "ANA LÚCIA DOS SANTOS"},
    {"nome": "JOÃO PEDRO ALMEIDA LIMA", "cpf": cpf_de_teste("111444777"), "nasc": "03/09/1985",
     "rg": "23.456.789-X", "registro": "98765432101", "cidade": "CURITIBA", "uf": "PR",
     "pai": "PEDRO HENRIQUE LIMA", "mae": "MARTA ALMEIDA LIMA"},
    {"nome": "ANA BEATRIZ FERREIRA COSTA", "cpf": cpf_de_teste("168995350"), "nasc": "21/07/1992",
     "rg": "34.567.890-1", "registro": "01234567890", "cidade": "RECIFE", "uf": "PE",
     "pai": "CARLOS ALBERTO COSTA", "mae": "LUCIA FERREIRA COSTA"},
    {"nome": "PAULO HENRIQUE ROCHA", "cpf": cpf_de_teste("935411347"), "nasc": "15/02/1978",
     "rg": "45.678.901-2", "registro": "07654321098", "cidade": "BELO HORIZONTE", "uf": "MG",
     "pai": "ANTÔNIO ROCHA FILHO", "mae": "HELENA ROCHA"},
    {"nome": "FERNANDA LIMA OLIVEIRA", "cpf": cpf_de_teste("390533447"), "nasc": "08/11/2001",
     "rg": "56.789.012-3", "registro": "05566778899", "cidade": "SALVADOR", "uf": "BA",
     "pai": "RICARDO OLIVEIRA", "mae": "SANDRA LIMA OLIVEIRA"},
]


# ------------------------------------------------------------ desenho
def _base(titulo, cor, subtitulo=None):
    img = Image.new("RGB", (LARGURA, ALTURA), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, LARGURA, 110], fill=cor)
    d.text((30, 28), titulo, font=_fonte(34, True), fill="white")
    if subtitulo:
        d.text((30, 74), subtitulo, font=_fonte(20), fill="white")
    d.rectangle([0, ALTURA - 46, LARGURA, ALTURA], fill=(190, 30, 30))
    d.text((30, ALTURA - 38), "DOCUMENTO FICTÍCIO · SOMENTE PARA TESTE · SEM VALIDADE",
           font=_fonte(22, True), fill="white")
    return img, d


def _foto(img, caixa, foto):
    x, y, w, h = caixa
    d = ImageDraw.Draw(img)
    if foto:
        f = foto.copy().convert("RGB")
        escala = max(w / f.width, h / f.height)
        f = f.resize((int(f.width * escala), int(f.height * escala)))
        esq, topo = (f.width - w) // 2, (f.height - h) // 2
        img.paste(f.crop((esq, topo, esq + w, topo + h)), (x, y))
    else:
        avatar = Image.new("RGB", (w, h), (205, 210, 220))
        a = ImageDraw.Draw(avatar)
        a.ellipse([w * 0.3, h * 0.15, w * 0.7, h * 0.5], fill=(150, 155, 170))
        a.ellipse([w * 0.1, h * 0.55, w * 0.9, h * 1.2], fill=(150, 155, 170))
        img.paste(avatar, (x, y))
    d.rectangle([x, y, x + w, y + h], outline=(80, 80, 80), width=2)


def _campo(d, x, y, rotulo, valor, tam=30):
    d.text((x, y), rotulo, font=_fonte(16), fill=(110, 110, 110))
    d.text((x, y + 20), valor, font=_fonte(tam, True), fill=(20, 20, 20))


def cnh_antiga(p, foto):
    img, d = _base("CARTEIRA NACIONAL DE HABILITAÇÃO", (30, 90, 60), "MODELO GENÉRICO A")
    _foto(img, (30, 135, 200, 260), foto)
    _campo(d, 260, 130, "NOME", p["nome"])
    _campo(d, 260, 200, "DOC. IDENTIDADE / ORG. EMISSOR / UF", f"{p['rg']} SSP {p['uf']}", 26)
    _campo(d, 260, 270, "CPF", formatar_cpf(p["cpf"]), 28)
    _campo(d, 620, 270, "DATA NASCIMENTO", p["nasc"], 28)
    _campo(d, 260, 345, "REGISTRO", p["registro"], 28)
    _campo(d, 620, 345, "VALIDADE", "10/05/2030", 28)
    _campo(d, 260, 420, "1ª HABILITAÇÃO", "15/06/2010", 28)
    _campo(d, 620, 420, "CATEGORIA", "AB", 28)
    return img


def cnh_nova(p, foto):
    img, d = _base("CARTEIRA NACIONAL DE HABILITAÇÃO", (20, 70, 140), "MODELO GENÉRICO B")
    _foto(img, (30, 135, 200, 260), foto)
    _campo(d, 260, 130, "NOME E SOBRENOME", p["nome"])
    _campo(d, 260, 200, "DATA, LOCAL E UF DE NASCIMENTO", f"{p['nasc']}, {p['cidade']}, {p['uf']}", 26)
    _campo(d, 260, 270, "CPF", formatar_cpf(p["cpf"]), 28)
    _campo(d, 620, 270, "Nº REGISTRO", p["registro"], 28)
    _campo(d, 260, 345, "VALIDADE", "20/01/2031", 28)
    _campo(d, 620, 345, "DATA DE EMISSÃO", "21/01/2021", 28)
    return img


def rg_a(p, foto):
    img, d = _base("CARTEIRA DE IDENTIDADE", (110, 60, 120), "REPÚBLICA FEDERATIVA · MODELO GENÉRICO C")
    _foto(img, (30, 135, 200, 260), foto)
    _campo(d, 260, 125, "REGISTRO GERAL", p["rg"], 30)
    _campo(d, 260, 190, "NOME", p["nome"], 28)
    _campo(d, 260, 255, "FILIAÇÃO", p["pai"], 24)
    d.text((260, 310), p["mae"], font=_fonte(24, True), fill=(20, 20, 20))
    _campo(d, 260, 360, "DATA DE NASCIMENTO", p["nasc"], 28)
    _campo(d, 620, 360, "CPF", formatar_cpf(p["cpf"]), 28)
    return img


def rg_b(p, foto):
    img, d = _base("INSTITUTO DE IDENTIFICAÇÃO", (150, 90, 20), "MODELO GENÉRICO D")
    _foto(img, (770, 135, 200, 260), foto)
    d.text((30, 130), f"RG {p['rg']}", font=_fonte(34, True), fill=(20, 20, 20))
    _campo(d, 30, 200, "NOME / NAME", p["nome"], 28)
    _campo(d, 30, 270, "DATA NASC.", p["nasc"], 28)
    _campo(d, 30, 340, "NATURALIDADE", f"{p['cidade']} - {p['uf']}", 26)
    _campo(d, 30, 410, "CPF", formatar_cpf(p["cpf"]), 28)
    return img


def cin(p, foto):
    img, d = _base("CARTEIRA DE IDENTIDADE NACIONAL", (20, 110, 110), "MODELO GENÉRICO E")
    _foto(img, (30, 135, 200, 260), foto)
    _campo(d, 260, 130, "NOME", p["nome"])
    _campo(d, 260, 200, "CPF", formatar_cpf(p["cpf"]), 30)
    _campo(d, 260, 270, "DATA DE NASCIMENTO", p["nasc"], 28)
    _campo(d, 260, 340, "NATURALIDADE", f"{p['cidade']} - {p['uf']}", 26)
    return img


MODELOS = [
    ("cnh_antiga", cnh_antiga, "CNH", "registro"),
    ("cnh_nova", cnh_nova, "CNH", "registro"),
    ("rg_a", rg_a, "RG", "rg"),
    ("rg_b", rg_b, "RG", "rg"),
    ("cin", cin, "CIN", "cpf"),
]


def como_foto_de_celular(img, semente):
    rnd = random.Random(semente)
    img = img.rotate(rnd.uniform(-2.5, 2.5), expand=True, fillcolor=(235, 235, 235), resample=Image.BICUBIC)
    img = img.resize((int(img.width * 0.8), int(img.height * 0.8)))
    return img.filter(ImageFilter.GaussianBlur(0.9))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--saida", default="amostras", help="pasta de saída (padrão: amostras)")
    ap.add_argument("--foto", help="foto de rosto AUTORIZADA para colar nos documentos")
    args = ap.parse_args()

    saida = Path(args.saida)
    saida.mkdir(parents=True, exist_ok=True)
    foto = Image.open(args.foto) if args.foto else None
    gabarito = {}
    for i, (nome_modelo, desenhar, tipo, fonte_numero) in enumerate(MODELOS):
        pessoa = PESSOAS[i]
        numero = pessoa["cpf"] if fonte_numero == "cpf" else pessoa[fonte_numero]
        esperado = {
            "tipo_documento": tipo,
            "nome": pessoa["nome"],
            "cpf": pessoa["cpf"],
            "data_nascimento": "-".join(reversed(pessoa["nasc"].split("/"))),
            "numero_documento": numero,
        }
        limpo = desenhar(pessoa, foto)
        arquivo = f"{nome_modelo}_limpo.png"
        limpo.save(saida / arquivo)
        gabarito[arquivo] = esperado
        arquivo = f"{nome_modelo}_foto.jpg"
        como_foto_de_celular(limpo, i).save(saida / arquivo, quality=65)
        gabarito[arquivo] = esperado
    (saida / "gabarito.json").write_text(json.dumps(gabarito, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(gabarito)} imagens fictícias e gabarito.json salvos em {saida}/")


if __name__ == "__main__":
    main()
