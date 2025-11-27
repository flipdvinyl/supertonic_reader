package com.supertone.ebook;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unicode text processor
 */
class UnicodeProcessor {
    private static final String TAG = "UnicodeProcessor";
    private long[] indexer;
    
    public UnicodeProcessor(String unicodeIndexerJsonPath) throws IOException {
        this.indexer = SupertonicHelper.loadJsonLongArray(unicodeIndexerJsonPath);
    }
    
    public TextProcessResult call(List<String> textList) {
        List<String> processedTexts = new ArrayList<>();
        for (String text : textList) {
            processedTexts.add(preprocessText(text));
        }
        
        int[] textIdsLengths = new int[processedTexts.size()];
        int maxLen = 0;
        for (int i = 0; i < processedTexts.size(); i++) {
            textIdsLengths[i] = processedTexts.get(i).length();
            maxLen = Math.max(maxLen, textIdsLengths[i]);
        }
        
        long[][] textIds = new long[processedTexts.size()][maxLen];
        for (int i = 0; i < processedTexts.size(); i++) {
            int[] unicodeVals = textToUnicodeValues(processedTexts.get(i));
            for (int j = 0; j < unicodeVals.length; j++) {
                if (unicodeVals[j] < indexer.length) {
                    textIds[i][j] = indexer[unicodeVals[j]];
                } else {
                    textIds[i][j] = 0; // Unknown character
                }
            }
        }
        
        float[][][] textMask = getTextMask(textIdsLengths);
        return new TextProcessResult(textIds, textMask);
    }
    
    private static String removeEmojis(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int codePoint;
            if (Character.isHighSurrogate(text.charAt(i)) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                codePoint = Character.codePointAt(text, i);
                i++; // Skip the low surrogate
            } else {
                codePoint = text.charAt(i);
            }
            
            // Check if code point is in emoji ranges
            boolean isEmoji = (codePoint >= 0x1F600 && codePoint <= 0x1F64F) ||
                              (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) ||
                              (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) ||
                              (codePoint >= 0x1F700 && codePoint <= 0x1F77F) ||
                              (codePoint >= 0x1F780 && codePoint <= 0x1F7FF) ||
                              (codePoint >= 0x1F800 && codePoint <= 0x1F8FF) ||
                              (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) ||
                              (codePoint >= 0x1FA00 && codePoint <= 0x1FA6F) ||
                              (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) ||
                              (codePoint >= 0x2600 && codePoint <= 0x26FF) ||
                              (codePoint >= 0x2700 && codePoint <= 0x27BF) ||
                              (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF);
            
            if (!isEmoji) {
                if (codePoint > 0xFFFF) {
                    result.append(Character.toChars(codePoint));
                } else {
                    result.append((char) codePoint);
                }
            }
        }
        return result.toString();
    }
    
    private String preprocessText(String text) {
        // TODO: Need advanced normalizer for better performance
        text = Normalizer.normalize(text, Normalizer.Form.NFKD);

        // FIXME: this should be fixed for non-English languages

        // Remove emojis (wide Unicode range)
        // Java Pattern doesn't support \x{...} syntax for Unicode above \uFFFF
        // Use character filtering instead
        text = removeEmojis(text);

        // Replace various dashes and symbols
        Map<String, String> replacements = new HashMap<>();
        replacements.put("–", "-");      // en dash
        replacements.put("‑", "-");      // non-breaking hyphen
        replacements.put("—", "-");      // em dash
        replacements.put("¯", " ");      // macron
        replacements.put("_", " ");      // underscore
        replacements.put("\u201C", "\"");     // left double quote
        replacements.put("\u201D", "\"");     // right double quote
        replacements.put("\u2018", "'");      // left single quote
        replacements.put("\u2019", "'");      // right single quote
        replacements.put("´", "'");      // acute accent
        replacements.put("`", "'");      // grave accent
        replacements.put("[", " ");      // left bracket
        replacements.put("]", " ");      // right bracket
        replacements.put("|", " ");      // vertical bar
        replacements.put("/", " ");      // slash
        replacements.put("#", " ");      // hash
        replacements.put("→", " ");      // right arrow
        replacements.put("←", " ");      // left arrow

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        // Remove combining diacritics // FIXME: this should be fixed for non-English languages
        text = text.replaceAll("[\\u0302\\u0303\\u0304\\u0305\\u0306\\u0307\\u0308\\u030A\\u030B\\u030C\\u0327\\u0328\\u0329\\u032A\\u032B\\u032C\\u032D\\u032E\\u032F]", "");

        // Remove special symbols
        text = text.replaceAll("[♥☆♡©\\\\]", "");

        // Replace known expressions
        Map<String, String> exprReplacements = new HashMap<>();
        exprReplacements.put("@", " at ");
        exprReplacements.put("e.g.,", "for example, ");
        exprReplacements.put("i.e.,", "that is, ");

        for (Map.Entry<String, String> entry : exprReplacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        // Fix spacing around punctuation
        text = text.replaceAll(" ,", ",");
        text = text.replaceAll(" \\.", ".");
        text = text.replaceAll(" !", "!");
        text = text.replaceAll(" \\?", "?");
        text = text.replaceAll(" ;", ";");
        text = text.replaceAll(" :", ":");
        text = text.replaceAll(" '", "'");

        // Remove duplicate quotes
        while (text.contains("\"\"")) {
            text = text.replace("\"\"", "\"");
        }
        while (text.contains("''")) {
            text = text.replace("''", "'");
        }
        while (text.contains("``")) {
            text = text.replace("``", "`");
        }

        // Remove extra spaces
        text = text.replaceAll("\\s+", " ").trim();

        // If text doesn't end with punctuation, quotes, or closing brackets, add a period
        if (!text.matches(".*[.!?;:,'\"\\u201C\\u201D\\u2018\\u2019)\\]}…。」』】〉》›»]$")) {
            text += ".";
        }

        return text;
    }
    
    private int[] textToUnicodeValues(String text) {
        int[] values = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            values[i] = text.codePointAt(i);
        }
        return values;
    }
    
    private float[][][] getTextMask(int[] lengths) {
        int bsz = lengths.length;
        int maxLen = 0;
        for (int len : lengths) {
            maxLen = Math.max(maxLen, len);
        }
        
        float[][][] mask = new float[bsz][1][maxLen];
        for (int i = 0; i < bsz; i++) {
            for (int j = 0; j < maxLen; j++) {
                mask[i][0][j] = j < lengths[i] ? 1.0f : 0.0f;
            }
        }
        return mask;
    }
    
    static class TextProcessResult {
        long[][] textIds;
        float[][][] textMask;
        
        TextProcessResult(long[][] textIds, float[][][] textMask) {
            this.textIds = textIds;
            this.textMask = textMask;
        }
    }
}


