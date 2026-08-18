package com.lumi.app.image;

public class ImageSafetyChecker {

    public boolean canProceed(String prompt, boolean nsfwAllowed) {
        if (prompt == null) return true;
        if (nsfwAllowed) return true;
        String p = prompt.toLowerCase();
        String[] blocked = new String[] {
                
        };
        for (String b : blocked) {
            if (p.contains(b)) return false;
        }
        return true;
    }
}