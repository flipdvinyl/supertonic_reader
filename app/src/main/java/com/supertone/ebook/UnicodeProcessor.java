package com.supertone.ebook;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

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
    
    private String preprocessText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD);
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


