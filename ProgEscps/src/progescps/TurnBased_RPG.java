package progescps;

import java.io.*;

/**
 * Main entry point for the Programmed Escapist game.
 * Reworked to launch the Swing UI instead of console interaction.
 */
public class TurnBased_RPG {

    public static void main(String[] args) {
        System.out.println("Starting game (Swing UI)...");
        GameUI.launch();
    }
}