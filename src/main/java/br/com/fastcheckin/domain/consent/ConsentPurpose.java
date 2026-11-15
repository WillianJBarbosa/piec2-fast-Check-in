package br.com.fastcheckin.domain.consent;

public enum ConsentPurpose {

    CHECKIN_FNRH("Check-in digital e preenchimento da FNRH"),
    BIOMETRIA_FACIAL("Comparação facial entre o documento e o hóspede");

    private final String description;

    ConsentPurpose(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
