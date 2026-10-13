package com.tgnguyen.layoutlybe.service.ai;

import com.tgnguyen.layoutlybe.model.ai.CodeDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodePatchEngineTest {

    private CodePatchEngine patchEngine;

    @BeforeEach
    void setUp() {
        patchEngine = new CodePatchEngine();
    }

    @Test
    void testExactSearchAndReplace() {
        String oldCode = "function Button() {\n  return <button className=\"bg-blue-500\">Click</button>\n}";
        String target = "bg-blue-500";
        String replacement = "bg-yellow-400";

        String patched = patchEngine.applyPatch(oldCode, target, replacement);
        assertTrue(patched.contains("bg-yellow-400"));
        assertFalse(patched.contains("bg-blue-500"));
    }

    @Test
    void testFullContentReplacement() {
        String oldCode = "const oldVal = 1;";
        String replacement = "const newVal = 2;";

        String patched = patchEngine.applyPatch(oldCode, null, replacement);
        assertEquals(replacement, patched);
    }

    @Test
    void testFuzzyTrimmedReplacement() {
        String oldCode = "<div>\n  <span class=\"title\">Hello</span>\n</div>";
        String target = "  <span class=\"title\">Hello</span>  ";
        String replacement = "  <span class=\"title\">Xin chao</span>  ";

        String patched = patchEngine.applyPatch(oldCode, target, replacement);
        assertTrue(patched.contains("Xin chao"));
    }

    @Test
    void testGenerateDiff() {
        String oldCode = "line 1\nline 2\nline 3";
        String newCode = "line 1\nline 2 MODIFIED\nline 3\nline 4";

        CodeDiff diff = patchEngine.generateDiff("src/App.jsx", oldCode, newCode);

        assertEquals("src/App.jsx", diff.filePath());
        assertEquals("MODIFIED", diff.changeType());
        assertNotNull(diff.patchUnified());
        assertTrue(diff.patchUnified().contains("--- a/src/App.jsx"));
        assertTrue(diff.patchUnified().contains("+++ b/src/App.jsx"));
    }
}
