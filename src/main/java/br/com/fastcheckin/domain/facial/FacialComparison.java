package br.com.fastcheckin.domain.facial;

/**
 * O que o serviço facial devolveu. {@code ratio} é a distância dividida pelo
 * limiar do modelo (menor é mais parecido) e pode ser nulo quando não havia
 * rosto para comparar; nesse caso {@code reason} explica.
 */
public record FacialComparison(FacialResult result, Double ratio, String reason, String model) {

    public boolean needsReview() {
        return result != FacialResult.COMPATIVEL;
    }
}
