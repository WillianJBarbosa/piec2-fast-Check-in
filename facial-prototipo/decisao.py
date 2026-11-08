"""Transforma a distância entre dois rostos em uma decisão de três níveis.

O sistema nunca reprova sozinho: o que não é claramente igual ou claramente
diferente vai para o recepcionista conferir. A faixa de "revisar" é a margem
de segurança contra aceitar a pessoa errada. Os limites abaixo são um ponto de
partida e devem ser ajustados com pares reais de teste (meta M19).
"""

MARGEM_COMPATIVEL = 0.85  # distância até 85% do limiar do modelo -> compatível
MARGEM_DIFERENTE = 1.15   # acima de 115% do limiar -> diferente; no meio, revisar


def classificar(distancia, limiar, margem_compativel=MARGEM_COMPATIVEL, margem_diferente=MARGEM_DIFERENTE):
    if limiar <= 0:
        raise ValueError("O limiar deve ser maior que zero.")
    razao = distancia / limiar
    if razao <= margem_compativel:
        return "compativel"
    if razao <= margem_diferente:
        return "revisar"
    return "diferente"
