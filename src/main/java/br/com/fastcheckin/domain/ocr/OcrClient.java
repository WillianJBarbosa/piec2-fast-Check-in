package br.com.fastcheckin.domain.ocr;

/** Porta para o serviço de OCR (ver ocr-servico). */
public interface OcrClient {

    /**
     * Envia a imagem ao serviço e devolve o que foi lido.
     *
     * @throws OcrException quando o serviço recusa a imagem, está fora do ar ou demora demais
     */
    OcrReading read(byte[] image, String contentType);
}
