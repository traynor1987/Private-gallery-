package uk.co.traynor.privategallery.core.editor;

/** Stateless composition; never rewrites, classifies or logs the owner's instructions. */
public final class CombinePrompt {
    private CombinePrompt() {}
    public static final String GUIDANCE = "Maintain realistic relative subject scale, natural human proportions, consistent camera perspective, subject distance, ground-plane alignment and coherent lighting. Do not arbitrarily shrink or enlarge people. Follow the user's requested composition and positioning.";
    public static String compose(String prompt, boolean preserveProportions) {
        if (!preserveProportions) return prompt;
        return prompt + "\n\nOptional composition guidance: apply the following only where it does not conflict with the user's instructions above. Explicit requests for unusual scaling or surreal proportions take priority; omit any conflicting guidance.\n" + GUIDANCE;
    }
}
