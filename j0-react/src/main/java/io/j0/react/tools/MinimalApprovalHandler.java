package io.j0.react.tools;

import java.util.Scanner;

/**
 * Console-based {@link ApprovalHandler} that blocks on stdin waiting for explicit user confirmation
 * before allowing a tool with {@code approval="required"} to execute (human-in-the-loop).
 * 
 * <p>When {@code autoApprove} is enabled, all tools are automatically approved without user interaction.</p>
 */
public class MinimalApprovalHandler implements ApprovalHandler {

    private final Scanner scanner;
    private final boolean autoApprove;

    public MinimalApprovalHandler(Scanner scanner) {
        this(scanner, false);
    }

    public MinimalApprovalHandler(Scanner scanner, boolean autoApprove) {
        this.scanner = scanner;
        this.autoApprove = autoApprove;
    }

    @Override
    public boolean approve(String toolName, String callToken) {
        if (autoApprove) {
            System.out.println("\n✓ [AUTO-APPROVATO] Il tool '" + toolName + "' è stato automaticamente approvato.");
            return true;
        }
        
        System.out.println("\n⚠️  [APPROVAZIONE RICHIESTA] Il tool '" + toolName + "' richiede conferma.");
        System.out.println("   Chiamata: " + callToken.trim());
        System.out.print("   Procedere? (s/n): ");
        String answer = scanner.nextLine().trim().toLowerCase();
        return answer.equals("s") || answer.equals("si") || answer.equals("y") || answer.equals("yes");
    }
}
