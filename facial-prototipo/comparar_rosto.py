"""Compara o rosto da foto do documento com o rosto de uma selfie.

    python comparar_rosto.py amostras/documento.png amostras/selfie.jpg

Resultado: compativel, revisar ou diferente (veja decisao.py). Se não achar
rosto em uma das imagens, devolve "revisar": nunca reprova sozinho.
Use apenas fotos suas ou de pessoas que autorizaram.
"""
import argparse
import json
import sys

import cv2
import numpy as np

from decisao import classificar


class RostoNaoDetectado(Exception):
    def __init__(self, onde):
        super().__init__(f"rosto não detectado em {onde}")
        self.onde = onde


def _carregar(caminho):
    img = cv2.imdecode(np.fromfile(caminho, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError(f"Não consegui abrir a imagem: {caminho}")
    return img


def _recortar_rosto(img, detector, onde, altura_minima=300):
    """Acha o maior rosto da imagem e recorta com folga. A foto do documento é pequena, então amplia."""
    from deepface import DeepFace

    try:
        rostos = DeepFace.extract_faces(img_path=img, detector_backend=detector,
                                        enforce_detection=True, align=False)
    except ValueError:
        raise RostoNaoDetectado(onde) from None
    maior = max(rostos, key=lambda r: r["facial_area"]["w"] * r["facial_area"]["h"])["facial_area"]
    x, y, w, h = maior["x"], maior["y"], maior["w"], maior["h"]
    folga = int(0.25 * max(w, h))
    recorte = img[max(0, y - folga): y + h + folga, max(0, x - folga): x + w + folga]
    if recorte.shape[0] < altura_minima:
        fator = altura_minima / recorte.shape[0]
        recorte = cv2.resize(recorte, None, fx=fator, fy=fator, interpolation=cv2.INTER_CUBIC)
    return recorte


def verificar_deepface(documento, selfie, modelo, detector, metrica):
    """Devolve (distancia, limiar_do_modelo)."""
    from deepface import DeepFace

    rosto_doc = _recortar_rosto(_carregar(documento), detector, "documento")
    rosto_selfie = _recortar_rosto(_carregar(selfie), detector, "selfie")
    r = DeepFace.verify(img1_path=rosto_doc, img2_path=rosto_selfie, model_name=modelo,
                        detector_backend=detector, distance_metric=metrica,
                        enforce_detection=False, align=True)
    return float(r["distance"]), float(r["threshold"])


def comparar(documento, selfie, modelo="Facenet512", detector="retinaface", metrica="cosine",
             limiar=None, verificador=verificar_deepface):
    base = {"modelo": modelo, "detector": detector, "metrica": metrica}
    try:
        distancia, limiar_modelo = verificador(documento, selfie, modelo, detector, metrica)
    except RostoNaoDetectado as erro:
        return {**base, "resultado": "revisar", "motivo": str(erro)}
    limiar = limiar if limiar else limiar_modelo
    return {**base, "resultado": classificar(distancia, limiar), "distancia": round(distancia, 4),
            "limiar": round(limiar, 4), "razao": round(distancia / limiar, 3)}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("documento")
    ap.add_argument("selfie")
    ap.add_argument("--modelo", default="Facenet512", help="Facenet512, ArcFace, VGG-Face, ...")
    ap.add_argument("--detector", default="retinaface", help="retinaface, mtcnn, opencv, ...")
    ap.add_argument("--metrica", default="cosine", choices=["cosine", "euclidean", "euclidean_l2"])
    ap.add_argument("--limiar", type=float, help="troca o limiar padrão do modelo")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass
    print(json.dumps(comparar(args.documento, args.selfie, args.modelo, args.detector, args.metrica,
                              args.limiar), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
