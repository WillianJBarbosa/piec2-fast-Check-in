package br.com.fastcheckin.domain.facial;

/**
 * Resultado da comparação facial. O sistema nunca reprova sozinho: só
 * {@link #COMPATIVEL} segue direto; os outros dois passam pelo recepcionista.
 */
public enum FacialResult {
    COMPATIVEL,
    REVISAR,
    DIFERENTE
}
