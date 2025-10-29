package progescps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GameUI with updated behavior:
 * - Calls manager.requestStopCurrentGame() and inputProvider.clearPending() before starting a new game
 *   so the previous game thread is asked to stop and any pending UI input is cleared.
 *
 * Small change: console background now uses the same dark color as the loading screen (BG_DARK).
 */
public class GameUI {

    private final JFrame frame;
    private final InputProvider inputProvider;
    private final GameManager manager;

    // CardLayout container so we can switch from loading -> menu -> game
    private final JPanel rootPanel;
    private final CardLayout cardLayout;

    // Panels
    private final JPanel loadingPanel;
    private final JPanel mainMenuPanel;
    private final JPanel gamePanel;

    // Loading UI
    private JProgressBar loadingBar;

    // Main menu UI
    private JButton menuStartBtn;
    private JButton menuLoadBtn;
    private JButton menuQuitBtn;

    // Game UI components (the in-game layout)
    private JLabel gifPlaceholder;
    private JLabel classLabel;
    private JLabel hpLabel;
    private JLabel manaLabel;
    private JLabel goldLabel;
    private JTextArea consoleArea;
    private JTextField inputField;

    // Color palette (fully-qualified to avoid name clash with progescps.Color)
    private static final java.awt.Color BG_DARK = new java.awt.Color(25, 25, 35);
    private static final java.awt.Color ACCENT_LIGHT = new java.awt.Color(220, 220, 255);
    private static final java.awt.Color FOOTER = new java.awt.Color(190, 190, 220);
    private static final java.awt.Color BUTTON_BG = new java.awt.Color(45, 62, 92);
    private static final java.awt.Color BUTTON_BORDER = new java.awt.Color(160, 180, 220);
    private static final java.awt.Color GAME_LEFT_BG = new java.awt.Color(28, 34, 45);
    // Changed: console background uses the same BG_DARK as the loading screen
    private static final java.awt.Color CONSOLE_BG = BG_DARK;
    private static final java.awt.Color CONSOLE_TEXT = java.awt.Color.WHITE;

    public GameUI() {
        // disable ANSI coloring so console text looks plain inside Swing components
        try { progescps.Color.USE_ANSI = false; } catch (Throwable ignored) {}

        this.inputProvider = new InputProvider();
        this.manager = new GameManager(inputProvider);

        frame = new JFrame("Codeborne - UI Console");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(820, 520);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        frame.setContentPane(rootPanel);

        // Build panels
        loadingPanel = createLoadingPanel();
        mainMenuPanel = createMainMenuPanel();
        gamePanel = createGamePanel(); // in-game console + stats layout

        // Add panels to card layout
        rootPanel.add(loadingPanel, "LOADING");
        rootPanel.add(mainMenuPanel, "MENU");
        rootPanel.add(gamePanel, "GAME");

        // Show loading first
        cardLayout.show(rootPanel, "LOADING");

        frame.setVisible(true);

        // Start loading animation then switch to main menu
        SwingUtilities.invokeLater(this::startLoadingSequence);

        // Periodic refresh of stats for the in-game screen
        javax.swing.Timer statsTimer = new javax.swing.Timer(500, evt -> refreshStats());
        statsTimer.setRepeats(true);
        statsTimer.start();
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                statsTimer.stop();
            }
        });
    }

    private JPanel createLoadingPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));
        panel.setBackground(BG_DARK);

        // Title centered
        JLabel title = new JLabel("Codeborne: Odyssey of the Programmer", SwingConstants.CENTER);
        title.setForeground(ACCENT_LIGHT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(title, BorderLayout.NORTH);

        // Center: only the large progress bar and a small status label
        JPanel center = new JPanel();
        center.setOpaque(true);
        center.setBackground(BG_DARK);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(40, 80, 40, 80));

        JLabel loadingLabel = new JLabel("Loading...");
        loadingLabel.setForeground(java.awt.Color.WHITE);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.PLAIN, 14f));
        center.add(loadingLabel);

        center.add(Box.createRigidArea(new Dimension(0, 12)));

        loadingBar = new JProgressBar(0, 100);
        loadingBar.setValue(0);
        loadingBar.setStringPainted(true);
        loadingBar.setPreferredSize(new Dimension(520, 28));
        loadingBar.setMaximumSize(new Dimension(520, 28));
        loadingBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingBar.setForeground(ACCENT_LIGHT);
        loadingBar.setBackground(BUTTON_BG.darker());
        center.add(loadingBar);

        panel.add(center, BorderLayout.CENTER);

        // Footer / credits
        JLabel footer = new JLabel("A Tale of Code and Digital Adventures", SwingConstants.CENTER);
        footer.setForeground(FOOTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createMainMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        panel.setBackground(BG_DARK);

        // Title
        JLabel title = new JLabel("Codeborne: Odyssey of the Programmer", SwingConstants.CENTER);
        title.setForeground(ACCENT_LIGHT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(title, BorderLayout.NORTH);

        // Center area: menu buttons stacked
        JPanel center = new JPanel();
        center.setBorder(new EmptyBorder(24, 12, 24, 12));
        center.setOpaque(true);
        center.setBackground(BG_DARK);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        center.add(Box.createVerticalGlue());

        menuStartBtn = new JButton("Start Game");
        menuLoadBtn = new JButton("Load Game");
        menuQuitBtn = new JButton("Quit");

        Dimension btnSize = new Dimension(360, 44);
        menuStartBtn.setMaximumSize(btnSize);
        menuLoadBtn.setMaximumSize(btnSize);
        menuQuitBtn.setMaximumSize(btnSize);

        menuStartBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        menuLoadBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        menuQuitBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        styleMenuButton(menuStartBtn);
        styleMenuButton(menuLoadBtn);
        styleMenuButton(menuQuitBtn);

        center.add(menuStartBtn);
        center.add(Box.createRigidArea(new Dimension(0, 12)));
        center.add(menuLoadBtn);
        center.add(Box.createRigidArea(new Dimension(0, 12)));
        center.add(menuQuitBtn);

        center.add(Box.createVerticalGlue());

        panel.add(center, BorderLayout.CENTER);

        // Footer
        JLabel footer = new JLabel("A Tale of Code and Digital Adventures", SwingConstants.CENTER);
        footer.setForeground(FOOTER);
        panel.add(footer, BorderLayout.SOUTH);

        // Button actions
        menuStartBtn.addActionListener(e -> {
            // First stop any running game and clear pending inputs then open class selection
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            showClassSelectionDialog();
        });
        menuLoadBtn.addActionListener(e -> showLoadDialogFromMenu());
        menuQuitBtn.addActionListener(e -> {
            boolean ok = showConfirmation("Quit Game", "Are you sure you want to quit the game?");
            if (ok) {
                manager.uiQuitGame();
            }
        });

        return panel;
    }

    /**
     * Applies consistent styling to primary menu-like buttons used across screens.
     * Ensures buttons are opaque, have a clear border and hover effect to avoid visual artifacts.
     */
    private void styleMenuButton(final JButton b) {
        b.setBackground(BUTTON_BG);
        b.setForeground(ACCENT_LIGHT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createLineBorder(BUTTON_BORDER, 2));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        // a subtle hover effect
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(BUTTON_BG.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(BUTTON_BG);
            }
        });
    }

    private void startLoadingSequence() {
        // Simulate loading for ~1.8 seconds then switch to the MAIN MENU
        new Thread(() -> {
            try {
                for (int i = 0; i <= 100; i += 5) {
                    final int v = i;
                    SwingUtilities.invokeLater(() -> loadingBar.setValue(v));
                    Thread.sleep(90);
                }
            } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                loadingBar.setValue(100);
                // After loading finishes, show the main menu screen
                cardLayout.show(rootPanel, "MENU");
            });
        }, "Loading-Thread").start();
    }

    private JPanel createGamePanel() {
        // This builds the in-game split layout (left stats + right console) - adapted from original GameUI
        JPanel container = new JPanel(new BorderLayout(6, 6));
        container.setBackground(BG_DARK);

        // Split left / right
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(260);
        split.setResizeWeight(0.0);
        split.setBorder(null);

        // LEFT panel
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(new EmptyBorder(8, 8, 8, 8));
        left.setBackground(GAME_LEFT_BG);

        // Character placeholder (styled)
        gifPlaceholder = new JLabel("CHARACTER GIF", SwingConstants.CENTER);
        gifPlaceholder.setOpaque(true);
        gifPlaceholder.setBackground(new java.awt.Color(210, 175, 40)); // warm accent for the portrait area
        gifPlaceholder.setForeground(java.awt.Color.DARK_GRAY);
        gifPlaceholder.setPreferredSize(new Dimension(240, 240));
        gifPlaceholder.setBorder(BorderFactory.createLineBorder(FOOTER.darker(), 2));
        left.add(gifPlaceholder, BorderLayout.NORTH);

        // Stats area
        JPanel stats = new JPanel(new GridLayout(4, 1, 4, 4));
        stats.setBackground(GAME_LEFT_BG);
        stats.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FOOTER.darker(), 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        classLabel = new JLabel("Class: -");
        classLabel.setForeground(ACCENT_LIGHT);
        hpLabel = new JLabel(" HP: - / -");
        hpLabel.setForeground(ACCENT_LIGHT);
        manaLabel = new JLabel(" Mana: - / -");
        manaLabel.setForeground(ACCENT_LIGHT);
        goldLabel = new JLabel(" Gold: -");
        goldLabel.setForeground(ACCENT_LIGHT);
        stats.add(classLabel);
        stats.add(hpLabel);
        stats.add(manaLabel);
        stats.add(goldLabel);
        left.add(stats, BorderLayout.CENTER);

        // Control buttons (in game)
        JPanel controls = new JPanel(new GridLayout(2, 2, 8, 8));
        controls.setBackground(GAME_LEFT_BG);
        JButton newBtn = new JButton("New Game");
        JButton saveBtn = new JButton("Save Game");
        JButton loadBtn = new JButton("Load Game");
        JButton quitBtn = new JButton("Main Menu"); // label changed to "MainMenu"
        styleMenuButton(newBtn);
        styleMenuButton(saveBtn);
        styleMenuButton(loadBtn);
        styleMenuButton(quitBtn);
        controls.add(newBtn);
        controls.add(saveBtn);
        controls.add(loadBtn);
        controls.add(quitBtn);
        left.add(controls, BorderLayout.SOUTH);

        // Button actions: New Game uses class selection, Save/Load show dialogs, MainMenu -> confirmation then main menu
        newBtn.addActionListener(e -> {
            boolean ok = showConfirmation("Start New Game", "Start a new game? Current progress will be lost.");
            if (ok) {
                // request stop of current game and clear pending inputs, then show class selection
                manager.requestStopCurrentGame();
                inputProvider.clearPending();
                consoleArea.setText("");
                showClassSelectionDialog();
            }
        });
        saveBtn.addActionListener(e -> showSaveDialog());
        loadBtn.addActionListener(e -> showLoadDialogFromGame());
        quitBtn.addActionListener(e -> {
            boolean ok = showConfirmation("Return to Main Menu", "Return to Main Menu? Current progress will continue in background.");
            if (ok) {
                cardLayout.show(rootPanel, "MENU");
            }
        });

        // RIGHT panel: console area
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(new EmptyBorder(8, 8, 8, 8));
        right.setBackground(BG_DARK);

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setBackground(CONSOLE_BG); // dark console bg (now same as loading BG_DARK)
        consoleArea.setForeground(CONSOLE_TEXT);
        consoleArea.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane consoleScroll = new JScrollPane(consoleArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        consoleScroll.setBorder(BorderFactory.createLineBorder(FOOTER.darker(), 1));
        right.add(consoleScroll, BorderLayout.CENTER);

        // Bottom of RIGHT panel: input field (inside right panel)
        JPanel inputPanel = new JPanel(new BorderLayout(6, 6));
        inputPanel.setBackground(BG_DARK);
        inputField = new JTextField();
        inputField.setBackground(java.awt.Color.WHITE);
        inputField.setForeground(java.awt.Color.BLACK);
        inputField.setPreferredSize(new Dimension(520, 36));
        inputPanel.add(inputField, BorderLayout.CENTER);
        JButton sendBtn = new JButton("Send");
        styleMenuButton(sendBtn);
        sendBtn.setPreferredSize(new Dimension(90, 36));
        inputPanel.add(sendBtn, BorderLayout.EAST);
        right.add(inputPanel, BorderLayout.SOUTH);

        // Submit on Enter or Send button
        inputField.addActionListener(e -> submitFromInputField());
        sendBtn.addActionListener(e -> submitFromInputField());

        split.setLeftComponent(left);
        split.setRightComponent(right);
        container.add(split, BorderLayout.CENTER);

        // Setup interceptor and redirect System.out/System.err to consoleArea
        ConsoleOutputInterceptor interceptor = new ConsoleOutputInterceptor(consoleArea);
        PrintStream ps = new PrintStream(interceptor, true);
        System.setOut(ps);
        System.setErr(ps);

        return container;
    }

    private void showClassSelectionDialog() {
        // Modal dialog that lets the player choose one of the 6 classes
        JDialog dialog = new JDialog(frame, "Choose Your Class", true);
        dialog.setSize(640, 320);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getContentPane().setBackground(BG_DARK);

        JLabel header = new JLabel("Select your class", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        header.setForeground(ACCENT_LIGHT);
        header.setBorder(new EmptyBorder(8, 8, 8, 8));
        dialog.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 3, 12, 12));
        grid.setBorder(new EmptyBorder(12, 12, 12, 12));
        grid.setOpaque(true);
        grid.setBackground(BG_DARK);

        // Buttons with short descriptions - style like menu buttons
        JButton btnDebugger = new JButton("<html><center>Debugger<br/><small>High HP & Defense</small></center></html>");
        JButton btnHacker = new JButton("<html><center>Hacker<br/><small>High Mana & Exploits</small></center></html>");
        JButton btnTester = new JButton("<html><center>Tester<br/><small>Criticals & Debuffs</small></center></html>");
        JButton btnArchitect = new JButton("<html><center>Architect<br/><small>Design & Rally</small></center></html>");
        JButton btnPenTester = new JButton("<html><center>PenTester<br/><small>Stealth & Critical</small></center></html>");
        JButton btnSupport = new JButton("<html><center>Support<br/><small>Heals & Buffs</small></center></html>");

        styleMenuButton(btnDebugger);
        styleMenuButton(btnHacker);
        styleMenuButton(btnTester);
        styleMenuButton(btnArchitect);
        styleMenuButton(btnPenTester);
        styleMenuButton(btnSupport);

        grid.add(btnDebugger);
        grid.add(btnHacker);
        grid.add(btnTester);
        grid.add(btnArchitect);
        grid.add(btnPenTester);
        grid.add(btnSupport);

        dialog.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(BG_DARK);
        JButton cancel = new JButton("Cancel");
        styleMenuButton(cancel);
        cancel.setPreferredSize(new Dimension(100, 34));
        bottom.add(cancel);
        dialog.add(bottom, BorderLayout.SOUTH);

        // Action listeners: request stop of any running game, clear pending inputs then start new game with chosen class
        btnDebugger.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(1);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });
        btnHacker.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(2);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });
        btnTester.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(3);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });
        btnArchitect.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(4);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });
        btnPenTester.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(5);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });
        btnSupport.addActionListener(e -> {
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiStartGameWithClass(6);
            cardLayout.show(rootPanel, "GAME");
            dialog.dispose();
        });

        cancel.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showSaveDialog() {
        // Default filename: save_YYYYMMDD_HHmmss.dat
        String defaultName = "save_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".dat";
        String filename = (String) JOptionPane.showInputDialog(frame,
                "Enter save filename (will be stored in /saves):",
                "Save Game",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                defaultName);
        if (filename != null) {
            filename = filename.trim();
            if (filename.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Invalid filename.", "Save Cancelled", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Ensure extension
            if (!filename.toLowerCase().endsWith(".dat")) filename = filename + ".dat";
            manager.uiSaveGameTo(filename);
            JOptionPane.showMessageDialog(frame, "Save queued: " + filename, "Save", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showLoadDialogFromMenu() {
        // Called from main menu — list saves and allow load (or show empty)
        List<String> saves = manager.listSaveFiles();
        if (saves.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No save files found.", "Load Game", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JList<String> list = new JList<>(saves.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(420, 160));
        int res = JOptionPane.showConfirmDialog(frame, sp, "Select a save to load", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            String chosen = list.getSelectedValue();
            manager.requestStopCurrentGame();
            inputProvider.clearPending();
            manager.uiLoadGameFrom(chosen);
            // switch to game panel so the loaded player appears
            cardLayout.show(rootPanel, "GAME");
        }
    }

    private void showLoadDialogFromGame() {
        // Same as above but called from in-game UI
        showLoadDialogFromMenu();
    }

    private void submitFromInputField() {
        String text = inputField.getText();
        if (text == null) text = "";
        inputField.setText("");
        // echo into console area so user sees entered text
        consoleArea.append("> " + text + System.lineSeparator());
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        inputProvider.submitTrimmed(text);
    }

    private void refreshStats() {
        try {
            Hero p = manager.player;
            if (p == null) {
                SwingUtilities.invokeLater(() -> {
                    classLabel.setText("Class: -");
                    hpLabel.setText("HP: - / -");
                    manaLabel.setText("Mana: - / -");
                    goldLabel.setText("Gold: -");
                });
                return;
            }
            final String cls = p.getClassName();
            final String hp = "HP: " + p.hp + " / " + p.maxHP;
            final String mana = "Mana: " + p.mana + " / " + p.maxMana;
            final String gold = "Gold: " + p.getGold();
            SwingUtilities.invokeLater(() -> {
                classLabel.setText("Class: " + cls);
                hpLabel.setText(hp);
                manaLabel.setText(mana);
                goldLabel.setText(gold);
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Show a confirmation dialog with project color scheme.
     * Returns true when the user confirms (Yes).
     */
    private boolean showConfirmation(String title, String message) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG_DARK);
        JLabel msg = new JLabel("<html><div style='width:380px;'>" + message + "</div></html>");
        msg.setForeground(ACCENT_LIGHT);
        msg.setBorder(new EmptyBorder(8,8,8,8));
        p.add(msg, BorderLayout.CENTER);

        // Use custom options with themed labels
        String[] options = {"Yes", "No"};
        int res = JOptionPane.showOptionDialog(frame, p, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]);
        return res == JOptionPane.YES_OPTION;
    }

    public static void launch() {
        SwingUtilities.invokeLater(GameUI::new);
    }
}