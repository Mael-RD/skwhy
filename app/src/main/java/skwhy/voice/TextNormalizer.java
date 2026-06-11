package skwhy.voice;

import java.text.Normalizer;

/**
 * Normalisation du texte pour la comparaison de phrases en français.
 * Appliquée aussi bien sur la transcription Vosk que sur les phrases de référence.
 */
public class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Normalise un texte français pour la comparaison :
     * - Minuscules
     * - Suppression des accents
     * - Suppression de la ponctuation
     * - Réduction des espaces multiples
     * - Suppression des mots vides courts parasites (euh, heu, bah...)
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";

        String result = text.toLowerCase().trim();

        // Décomposer les caractères accentués puis supprimer les diacritiques
        result = Normalizer.normalize(result, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // Supprimer la ponctuation (garder les apostrophes pour "j'ai", "c'est", etc.)
        result = result.replaceAll("[^a-z0-9\\s']", "");

        // Développer les contractions courantes FR
        result = result
                .replace("j'", "je ")
                .replace("c'", "ce ")
                .replace("l'", "le ")
                .replace("d'", "de ")
                .replace("n'", "ne ")
                .replace("s'", "se ")
                .replace("m'", "me ")
                .replace("t'", "te ")
                .replace("qu'", "que ");

        // Supprimer les mots parasites de transcription automatique
        result = removeFillerWords(result);

        // Normaliser les espaces
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    /**
     * Supprime les mots de remplissage typiques que Vosk peut transcrire
     * à partir de bruits ou d'hésitations.
     */
    private static String removeFillerWords(String text) {
        // Liste des mots parasites courants en FR
        String[] fillers = {
                "\\beuh\\b", "\\bheu\\b", "\\bhem\\b", "\\bhm\\b",
                "\\bbah\\b", "\\bbeh\\b", "\\bben\\b",
                "\\beuh\\b", "\\bah\\b", "\\boh\\b",
                "\\bvoila\\b", "\\balors\\b"  // Ne supprimer que s'ils sont isolés / parasites
        };

        for (String filler : fillers) {
            text = text.replaceAll(filler, "");
        }

        return text;
    }
}
