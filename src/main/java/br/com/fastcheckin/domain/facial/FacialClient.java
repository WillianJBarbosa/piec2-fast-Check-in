package br.com.fastcheckin.domain.facial;

/** Porta para o serviço facial (ver facial-servico). */
public interface FacialClient {

    /**
     * Envia a foto do documento e a selfie e devolve o resultado da comparação.
     *
     * @throws FacialException quando o serviço recusa as imagens, está fora do ar ou demora demais
     */
    FacialComparison compare(byte[] documentImage, byte[] selfieImage);
}
