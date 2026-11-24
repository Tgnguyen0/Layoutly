package com.tgnguyen.layoutlybe.service.ai;

import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CodePatchEngine {

    public String applyPatch(String oldContent, String patchOrTarget, String replacement) {
        if (oldContent == null) oldContent = "";

        // Mode 1: Exact search & replace
        if (patchOrTarget != null && !patchOrTarget.isEmpty() && replacement != null) {
            if (oldContent.contains(patchOrTarget)) {
                return oldContent.replace(patchOrTarget, replacement);
            }

            // Mode 1b: Normalized whitespace search & replace
            String normalizedOld = oldContent.replace("\r\n", "\n");
            String normalizedTarget = patchOrTarget.replace("\r\n", "\n");
            String normalizedReplacement = replacement.replace("\r\n", "\n");

            if (normalizedOld.contains(normalizedTarget)) {
                return normalizedOld.replace(normalizedTarget, normalizedReplacement);
            }

            // Mode 1c: Trimmed lines match
            String fuzzyReplaced = fuzzyReplace(normalizedOld, normalizedTarget, normalizedReplacement);
            if (fuzzyReplaced != null) {
                return fuzzyReplaced;
            }

            throw new IllegalArgumentException("Khong tim thay doan code can thay the (Target content not found in file).");
        }

        // Mode 2: Direct replace full file
        if (replacement != null) {
            return replacement;
        }

        throw new IllegalArgumentException("Khong co noi dung thay the duoc cung cap.");
    }

    public CodeDiff generateDiff(String filePath, String oldContent, String newContent) {
        String safeOld = oldContent == null ? "" : oldContent.replace("\r\n", "\n");
        String safeNew = newContent == null ? "" : newContent.replace("\r\n", "\n");

        String changeType;
        if (oldContent == null || oldContent.isBlank()) {
            changeType = "ADDED";
        } else if (newContent == null || newContent.isBlank()) {
            changeType = "DELETED";
        } else {
            changeType = "MODIFIED";
        }

        String[] oldLines = safeOld.isEmpty() ? new String[0] : safeOld.split("\n", -1);
        String[] newLines = safeNew.isEmpty() ? new String[0] : safeNew.split("\n", -1);

        List<CodeDiff.Hunk> hunks = computeHunks(oldLines, newLines);

        StringBuilder unified = new StringBuilder();
        unified.append("--- a/").append(filePath).append("\n");
        unified.append("+++ b/").append(filePath).append("\n");
        for (CodeDiff.Hunk hunk : hunks) {
            unified.append(String.format("@@ -%d,%d +%d,%d @@\n",
                    hunk.oldStart(), hunk.oldLength(), hunk.newStart(), hunk.newLength()));
            for (String line : hunk.lines()) {
                unified.append(line).append("\n");
            }
        }

        return new CodeDiff(filePath, changeType, oldContent, newContent, unified.toString(), hunks);
    }

    private String fuzzyReplace(String content, String target, String replacement) {
        String[] contentLines = content.split("\n", -1);
        String[] targetLines = target.split("\n", -1);

        if (targetLines.length == 0 || targetLines[0].isBlank()) return null;

        for (int i = 0; i <= contentLines.length - targetLines.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetLines.length; j++) {
                if (!contentLines[i + j].trim().equals(targetLines[j].trim())) {
                    match = false;
                    break;
                }
            }
            if (match) {
                StringBuilder result = new StringBuilder();
                for (int k = 0; k < i; k++) {
                    result.append(contentLines[k]).append("\n");
                }
                result.append(replacement);
                if (!replacement.endsWith("\n") && i + targetLines.length < contentLines.length) {
                    result.append("\n");
                }
                for (int k = i + targetLines.length; k < contentLines.length; k++) {
                    result.append(contentLines[k]);
                    if (k < contentLines.length - 1) result.append("\n");
                }
                return result.toString();
            }
        }
        return null;
    }

    private List<CodeDiff.Hunk> computeHunks(String[] oldLines, String[] newLines) {
        List<CodeDiff.Hunk> hunks = new ArrayList<>();
        int oldIdx = 0;
        int newIdx = 0;

        List<String> hunkLines = new ArrayList<>();
        int oldStart = 1;
        int newStart = 1;
        int oldLen = 0;
        int newLen = 0;

        while (oldIdx < oldLines.length || newIdx < newLines.length) {
            if (oldIdx < oldLines.length && newIdx < newLines.length && oldLines[oldIdx].equals(newLines[newIdx])) {
                if (!hunkLines.isEmpty()) {
                    // Include up to 2 context lines
                    hunkLines.add(" " + oldLines[oldIdx]);
                    oldLen++;
                    newLen++;
                    hunks.add(new CodeDiff.Hunk(oldStart, oldLen, newStart, newLen, new ArrayList<>(hunkLines)));
                    hunkLines.clear();
                }
                oldIdx++;
                newIdx++;
            } else {
                if (hunkLines.isEmpty()) {
                    oldStart = Math.max(1, oldIdx + 1);
                    newStart = Math.max(1, newIdx + 1);
                    oldLen = 0;
                    newLen = 0;
                }

                if (oldIdx < oldLines.length && (newIdx >= newLines.length || !containsLine(newLines, oldLines[oldIdx], newIdx))) {
                    hunkLines.add("-" + oldLines[oldIdx]);
                    oldLen++;
                    oldIdx++;
                } else if (newIdx < newLines.length) {
                    hunkLines.add("+" + newLines[newIdx]);
                    newLen++;
                    newIdx++;
                }
            }
        }

        if (!hunkLines.isEmpty()) {
            hunks.add(new CodeDiff.Hunk(oldStart, oldLen, newStart, newLen, hunkLines));
        }

        return hunks;
    }

    private boolean containsLine(String[] lines, String line, int from) {
        for (int i = from; i < Math.min(lines.length, from + 5); i++) {
            if (lines[i].equals(line)) return true;
        }
        return false;
    }
}
