"""Extrai os campos de CNH, RG e CIN a partir das linhas de texto do OCR.

Os documentos mudam conforme o estado, o ano de emissão e o tipo (CNH antiga,
CNH nova, RG de cada estado, Carteira de Identidade Nacional). Por isso o
extrator não depende da posição dos campos: procura rótulos conhecidos, aceita
o valor na mesma linha ou na linha de baixo e, quando não acha o rótulo, usa
regras de apoio e marca o campo como "inferido" para o recepcionista revisar.
"""
import re
import unicodedata
from datetime import date

CAMPOS = ("nome", "cpf", "data_nascimento", "numero_documento")

# Palavras que aparecem em cabeçalhos e rótulos: uma linha com elas não é um nome.
_NAO_NOME = {
    "REPUBLICA", "FEDERATIVA", "BRASIL", "MINISTERIO", "SECRETARIA", "CARTEIRA",
    "HABILITACAO", "IDENTIDADE", "DEPARTAMENTO", "TRANSITO", "NACIONAL",
    "FILIACAO", "ASSINATURA", "VALIDADE", "CATEGORIA", "DOCUMENTO", "ESTADO",
    "REGISTRO", "GERAL", "NASCIMENTO", "EMISSAO", "ORGAO", "EMISSOR", "FICTICIO",
    "TESTE", "NOME", "SOBRENOME", "NAME", "NATURALIDADE", "OBSERVACOES", "HABILITACAO", "DETRAN", "INSTITUTO",
    "SEGURANCA", "PUBLICA", "CPF", "DATA", "LOCAL", "SOMENTE", "PARA", "MODELO",
}
_PARTICULAS = {"DE", "DA", "DO", "DAS", "DOS", "E"}


def _norm(texto):
    """Maiúsculas e sem acento, mantendo o mesmo tamanho da linha original."""
    return "".join(unicodedata.normalize("NFD", c)[0] for c in texto).upper()


# ---------------------------------------------------------------- tipo
def detectar_tipo(linhas):
    texto = " ".join(_norm(l) for l in linhas)
    if "HABILITACAO" in texto or re.search(r"\bCNH\b", texto):
        return "CNH"
    if "IDENTIDADE NACIONAL" in texto or re.search(r"\bCIN\b", texto):
        return "CIN"
    if "REGISTRO GERAL" in texto or "IDENTIDADE" in texto or re.search(r"\bRG\b", texto):
        return "RG"
    return "DESCONHECIDO"


# ----------------------------------------------------------------- CPF
_TROCA_DIGITO = str.maketrans({"O": "0", "o": "0", "I": "1", "l": "1"})
_CPF = re.compile(
    r"(?<![0-9])([0-9OoIl]{3})[.\s]?([0-9OoIl]{3})[.\s]?([0-9OoIl]{3})\s?[-.\s]?\s?([0-9OoIl]{2})(?![0-9])"
)


def cpf_valido(digitos):
    if len(digitos) != 11 or not digitos.isdigit() or digitos == digitos[0] * 11:
        return False
    for tamanho in (9, 10):
        soma = sum(int(digitos[i]) * (tamanho + 1 - i) for i in range(tamanho))
        if (soma * 10) % 11 % 10 != int(digitos[tamanho]):
            return False
    return True


def _achar_cpf(linhas):
    candidatos = []
    for i, linha in enumerate(linhas):
        n = _norm(linha)
        tem_rotulo_cpf = re.search(r"\bCPF\b", n) is not None
        tem_registro = "REGISTRO" in n and not tem_rotulo_cpf
        # O rótulo CPF pode estar na linha de cima do valor.
        rotulo_acima = i > 0 and re.search(r"\bCPF\b", _norm(linhas[i - 1])) is not None
        for m in _CPF.finditer(linha):
            digitos = "".join(m.groups()).translate(_TROCA_DIGITO)
            if not digitos.isdigit():
                continue
            if tem_registro:  # número de registro da CNH também tem 11 dígitos
                continue
            rotulado = tem_rotulo_cpf or rotulo_acima
            candidatos.append((cpf_valido(digitos), rotulado, -i, digitos))
    if not candidatos:
        return None
    valido, rotulado, _, digitos = max(candidatos)
    return {"valor": digitos, "origem": "rotulo" if rotulado else "inferido", "valido": valido}


# ---------------------------------------------------------------- datas
_DATA = re.compile(r"(?<!\d)(\d{2})\s?[/\-.]\s?(\d{2})\s?[/\-.]\s?(\d{4})(?!\d)")


def _datas_da_linha(linha, hoje=None):
    hoje = hoje or date.today()
    achadas = []
    for dia, mes, ano in _DATA.findall(linha):
        try:
            d = date(int(ano), int(mes), int(dia))
        except ValueError:
            continue
        if 1900 <= d.year <= hoje.year and d <= hoje:
            achadas.append(d)
    return achadas


def _achar_nascimento(linhas, hoje=None):
    for i, linha in enumerate(linhas):
        n = _norm(linha)
        if "NASC" in n or "NASK" in n or "IMENTO" in n.replace(" ", ""):
            for j in range(i, min(i + 3, len(linhas))):
                datas = _datas_da_linha(linhas[j], hoje)
                if datas:
                    return {"valor": datas[0].isoformat(), "origem": "rotulo"}
    todas = [d for l in linhas for d in _datas_da_linha(l, hoje)]
    if todas:
        # Emissão, validade e primeira habilitação são sempre mais novas.
        return {"valor": min(todas).isoformat(), "origem": "inferido"}
    return None


# ----------------------------------------------------------------- nome
_ROTULO_NOME = re.compile(r"^\s*NOME(\s+[EÉ]\s+SOBRE\s*NOME|\s+COMPLETO)?(\s*[/:\-]\s*NAME)?\s*[:\-]?")
_OUTRO_NOME = re.compile(r"SOCIAL|\bPAI\b|\bMAE\b|\bPAIS\b|FILIACAO")


def _parece_nome(linha):
    n = _norm(linha).strip()
    sem_espaco = n.replace(" ", "")
    if len(n) < 8 or not sem_espaco:
        return False
    letras = sum(c.isalpha() or c in "'-" for c in sem_espaco)
    if letras / len(sem_espaco) < 0.9:
        return False
    palavras = n.split()
    if len(palavras) < 2 or not any(len(p) >= 3 for p in palavras):
        return False
    return not any(p in _NAO_NOME for p in palavras)


def _limpar_nome(texto):
    texto = re.sub(r"[^A-Za-zÀ-ÿ'\- ]", " ", texto)
    return re.sub(r"\s+", " ", texto).strip().upper()


def _achar_nome(linhas):
    for i, linha in enumerate(linhas):
        n = _norm(linha)
        m = _ROTULO_NOME.match(n)
        if not m or _OUTRO_NOME.search(n):
            continue
        resto = linha[m.end():]
        if _parece_nome(resto):
            return {"valor": _limpar_nome(resto), "origem": "rotulo"}
        for j in range(i + 1, min(i + 3, len(linhas))):
            if _parece_nome(linhas[j]):
                return {"valor": _limpar_nome(linhas[j]), "origem": "rotulo"}
    for linha in linhas:
        if _parece_nome(linha) and not _OUTRO_NOME.search(_norm(linha)):
            return {"valor": _limpar_nome(linha), "origem": "inferido"}
    return None


# ------------------------------------------------------ número do documento
_ROTULO_REGISTRO_CNH = re.compile(r"N\W{0,2}\s*REGISTRO|REGISTRO(?!\s+GERAL)|\bREG\b")
_ROTULO_RG = re.compile(r"REGISTRO\s+GERAL|\bRG\b|\bR\.G\.|DOC\w*\.?\s*(DE\s+)?IDENTIDADE")
_NUM_CNH = re.compile(r"(?<!\d)\d{9,11}(?!\d)")
_NUM_RG = re.compile(r"(?<![\d/])\d[\d.\-]{4,12}[\dXx]?(?![\d/])")
# Formato típico de RG (12.345.678-9), mesmo que o OCR coloque espaços no meio.
_RG_FORMATADO = re.compile(r"(?<![\d.])(\d{1,2}\s?\.\s?\d{3}\s?\.\s?\d{3}\s?-?\s?[\dXx])(?!\d)")


def _valor_apos_rotulo(linhas, i, rotulo, numero, evitar_cpf=False):
    m = rotulo.search(_norm(linhas[i]))
    if not m:
        return None
    for j in range(i, min(i + 3, len(linhas))):
        trecho = linhas[j][m.end():] if j == i else linhas[j]
        for achado in numero.finditer(trecho):
            valor = achado.group().strip(".-")
            if _DATA.search(valor):
                continue
            if evitar_cpf and cpf_valido(re.sub(r"\D", "", valor)):
                continue  # número com cara de CPF não é o RG
            return valor
    return None


def _achar_numero(linhas, tipo, cpf):
    if tipo in ("CNH", "DESCONHECIDO"):
        for i in range(len(linhas)):
            v = _valor_apos_rotulo(linhas, i, _ROTULO_REGISTRO_CNH, _NUM_CNH)
            if v:
                return {"valor": v, "origem": "rotulo", "tipo_numero": "REGISTRO_CNH"}
    if tipo in ("RG", "CIN", "DESCONHECIDO"):
        for i in range(len(linhas)):
            v = _valor_apos_rotulo(linhas, i, _ROTULO_RG, _NUM_RG, evitar_cpf=True)
            if v:
                return {"valor": v, "origem": "rotulo", "tipo_numero": "RG"}
    texto = "\n".join(linhas)
    if tipo in ("CNH", "DESCONHECIDO"):
        # OCR perdeu o rótulo: o número de registro é outro número de 9 a 11 dígitos.
        for achado in _NUM_CNH.finditer(texto):
            if not cpf or achado.group() != cpf["valor"]:
                return {"valor": achado.group(), "origem": "inferido", "tipo_numero": "REGISTRO_CNH"}
    if tipo in ("RG", "DESCONHECIDO"):
        for achado in _RG_FORMATADO.finditer(texto):
            valor = re.sub(r"\s", "", achado.group(1))
            if not cpf_valido(re.sub(r"\D", "", valor)):
                return {"valor": valor, "origem": "inferido", "tipo_numero": "RG"}
    if tipo == "CIN" and cpf:
        # Na CIN o CPF é o número nacional de identificação.
        return {"valor": cpf["valor"], "origem": "inferido", "tipo_numero": "CPF"}
    return None


# --------------------------------------------------------------- principal
def extrair(linhas, hoje=None):
    """Recebe as linhas lidas pelo OCR e devolve campos, tipo e se precisa de revisão."""
    linhas = [l.strip() for l in linhas if l and l.strip()]
    tipo = detectar_tipo(linhas)
    cpf = _achar_cpf(linhas)
    campos = {
        "nome": _achar_nome(linhas),
        "cpf": cpf,
        "data_nascimento": _achar_nascimento(linhas, hoje),
        "numero_documento": _achar_numero(linhas, tipo, cpf),
    }
    motivos = []
    if tipo == "DESCONHECIDO":
        motivos.append("tipo de documento não reconhecido")
    for nome in CAMPOS:
        c = campos[nome]
        if c is None:
            motivos.append(f"{nome} não encontrado")
        elif c["origem"] == "inferido":
            motivos.append(f"{nome} inferido sem rótulo")
    if cpf and not cpf["valido"]:
        motivos.append("CPF com dígitos verificadores inválidos")
    return {
        "tipo_documento": tipo,
        "campos": {k: (v if v else {"valor": None, "origem": "ausente"}) for k, v in campos.items()},
        "precisa_revisao": bool(motivos),
        "motivos": motivos,
    }


def para_api(resultado):
    """Monta o corpo do POST /api/v1/guests. Campos ausentes ficam nulos para revisão."""
    c = resultado["campos"]
    tipo = "CNH" if resultado["tipo_documento"] == "CNH" else "RG"
    return {
        "fullName": c["nome"]["valor"],
        "cpf": c["cpf"]["valor"],
        "dateOfBirth": c["data_nascimento"]["valor"],
        "documentType": tipo,
        "documentNumber": c["numero_documento"]["valor"],
    }
