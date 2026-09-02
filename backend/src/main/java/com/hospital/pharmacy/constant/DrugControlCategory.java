package com.hospital.pharmacy.constant;

import java.util.Set;

public final class DrugControlCategory {

    public static final String GENERAL = "GENERAL";
    public static final String NARCOTIC = "NARCOTIC";
    public static final String PSYCHOTROPIC_I = "PSYCHOTROPIC_I";
    public static final String PSYCHOTROPIC_II = "PSYCHOTROPIC_II";
    public static final String MEDICAL_TOXIC = "MEDICAL_TOXIC";

    private static final Set<String> VALUES = Set.of(
            GENERAL,
            NARCOTIC,
            PSYCHOTROPIC_I,
            PSYCHOTROPIC_II,
            MEDICAL_TOXIC
    );

    private DrugControlCategory() {
    }

    public static boolean isSupported(String value) {
        return VALUES.contains(value);
    }

    public static boolean requiresSpecialReview(String value) {
        return value != null && !GENERAL.equals(value);
    }
}
