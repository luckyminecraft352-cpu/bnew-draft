package progescps;

import javax.swing.SwingUtilities;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Full GameManager with UI wrapper methods (uiNewGame/uiSaveGame/uiLoadGame/uiQuitGame) added.
 *
 * Extended with save/load slot support and safe "stop current game" coordination so that
 * starting a new game from the UI won't let the previous game's loop continue to consume
 * queued/stale UI input.
 */
public class GameManager {

    private static final int MENU_WIDTH = 60;

    private final InputProvider scan;

    public Hero player;
    public Enemy enemy;

    Stack<Integer> lastPlayerHP = new Stack<>();
    Stack<Integer> lastEnemyHP = new Stack<>();

    int gameTimer = 2;
    String equippedWeapon = "Basic Sword";
    String equippedArmor = "Cloth Armor";
    private boolean useColor = true;
    private QuestManager questManager = new QuestManager();

    private Map<String, Location> worldMap;
    private Random random = new Random();
    private List<Faction> availableFactions;

    private final File savesDir = new File("saves");

    // Fields for safe stop coordination
    private volatile boolean stopRequested = false;   // set when external caller (UI) requests current game stop
    private volatile boolean gameRunning = false;     // true while the in-game loop is active

    /**
     * Internal unchecked exception used to unwind loops quickly when a stop is requested.
     */
    private static class GameStopException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Default constructor for backwards compatibility: creates its own InputProvider (not used by UI).
     */
    public GameManager() {
        this(new InputProvider());
    }

    /**
     * Preferred constructor when running with a UI: pass an InputProvider instance that will supply user input.
     */
    public GameManager(InputProvider inputProvider) {
        this.scan = inputProvider != null ? inputProvider : new InputProvider();
        initializeWorld();
        initializeFactions();
        if (!savesDir.exists()) {
            // create saves directory if not present
            try {
                savesDir.mkdirs();
            } catch (SecurityException ignored) {}
        }
    }

    /**
     * Request the currently-running in-game loop to stop and wait briefly for it to exit.
     * The UI calls this before starting a new game.
     */
    public void requestStopCurrentGame() {
        // If no game running, nothing to do
        if (!gameRunning) {
            stopRequested = false;
            return;
        }

        stopRequested = true;

        // Submit an empty input to unblock any waiting read
        try {
            scan.submitTrimmed("");
        } catch (Exception ignored) {}

        // Wait briefly for the running loop to clear
        long deadline = System.currentTimeMillis() + 1500;
        while (gameRunning && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Clear the request flag so new games start fresh (UI also clears pending)
        stopRequested = false;
    }

    /**
     * Reset core game state to a fresh baseline for starting a new game.
     * Called before creating the player for a fresh run.
     */
    private void resetForNewGame() {
        initializeWorld();
        initializeFactions();
        this.questManager = new QuestManager();
        this.equippedWeapon = "Basic Sword";
        this.equippedArmor = "Cloth Armor";
        this.gameTimer = 2;
        this.player = null;
        this.enemy = null;
    }

    /**
     * Public entry point used by UI code. For console mode the previous behavior
     * of calling displayMainMenu() directly is preserved.
     */
    public void start() {
        displayMainMenu();
    }

    /*
     * Public UI wrapper methods
     * These allow GameUI to call manager.uiNewGame(), manager.uiSaveGame(), etc.
     * They run the underlying logic on background threads so the Swing EDT is not blocked.
     */

    public void uiNewGame() {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Starting new game...", Color.YELLOW));
            try {
                startGame();
            } catch (Throwable t) {
                System.err.println("Error starting new game: " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-NewGame-Thread").start();
    }

    /**
     * New UI entry: start a new game with a class chosen by the GUI.
     * choice: 1=Debugger, 2=Hacker, 3=Tester, 4=Architect, 5=PenTester, 6=Support
     */
    public void uiStartGameWithClass(int choice) {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Starting new game (GUI class selection) ...", Color.YELLOW));
            try {
                startGameWithChoice(choice);
            } catch (Throwable t) {
                System.err.println("Error starting new game with choice: " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-StartGame-Thread").start();
    }

    public void uiSaveGame() {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Saving game...", Color.YELLOW));
            try {
                saveGame();
            } catch (Throwable t) {
                System.err.println("Error saving game: " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-Save-Thread").start();
    }

    /**
     * Save to a named slot (written into the 'saves' directory). Filename should include .dat.
     */
    public void uiSaveGameTo(String filename) {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Saving game to '" + filename + "'...", Color.YELLOW));
            try {
                saveGameTo(filename);
            } catch (Throwable t) {
                System.err.println("Error saving game to " + filename + ": " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-SaveSlot-Thread").start();
    }

    public void uiLoadGame() {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Loading game...", Color.YELLOW));
            try {
                loadGame();
            } catch (Throwable t) {
                System.err.println("Error loading game: " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-Load-Thread").start();
    }

    /**
     * Load a named slot (from the 'saves' directory or legacy savegame.dat).
     */
    public void uiLoadGameFrom(String filename) {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Loading game from '" + filename + "'...", Color.YELLOW));
            try {
                loadGameFrom(filename);
            } catch (Throwable t) {
                System.err.println("Error loading game from " + filename + ": " + t.getMessage());
                t.printStackTrace();
            }
        }, "UI-LoadSlot-Thread").start();
    }

    public void uiQuitGame() {
        new Thread(() -> {
            System.out.println(Color.colorize("UI: Quitting game...", Color.YELLOW));
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> System.exit(0));
        }, "UI-Quit-Thread").start();
    }

    /**
     * Return a list of available save filenames. This is synchronous and used by the UI to populate dialogs.
     * It looks for files in the "saves" directory and also includes the legacy "savegame.dat" if present.
     */
    public List<String> listSaveFiles() {
        List<String> names = new ArrayList<>();
        // legacy
        File legacy = new File("savegame.dat");
        if (legacy.exists() && legacy.isFile()) {
            names.add(legacy.getName());
        }
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] files = savesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".dat"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                for (File f : files) {
                    names.add(f.getName());
                }
            }
        }
        return names;
    }

    /**
     * Save game into the 'saves' directory under the given filename.
     */
    private synchronized void saveGameTo(String filename) {
        if (player == null) {
            System.out.println(Color.colorize("No active player to save.", Color.RED));
            return;
        }
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
        File outFile = filename.equals("savegame.dat") ? new File(filename) : new File(savesDir, filename);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outFile))) {
            oos.writeObject(player);
            oos.writeObject(worldMap);
            oos.writeObject(questManager);
            oos.writeObject(equippedWeapon);
            oos.writeObject(equippedArmor);
            oos.writeInt(gameTimer);
            oos.writeBoolean(useColor);
            System.out.println(Color.colorize("Game saved successfully to " + outFile.getPath(), Color.GREEN));
        } catch (IOException e) {
            System.out.println(Color.colorize("Error saving game: " + e.getMessage(), Color.RED));
        }
    }

    /**
     * Legacy save (keeps previous behavior) — writes to "savegame.dat" in app root.
     */
    private synchronized void saveGame() {
        saveGameTo("savegame.dat");
    }

    /**
     * Load from a save name (either legacy savegame.dat in root or files in saves/).
     */
    private synchronized void loadGameFrom(String filename) {
        File inFile = filename.equals("savegame.dat") ? new File(filename) : new File(savesDir, filename);
        if (!inFile.exists()) {
            System.out.println(Color.colorize("Save file not found: " + inFile.getPath(), Color.RED));
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(inFile))) {
            player = (Hero) ois.readObject();
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Location> map = (Map<String, Location>) obj;
                worldMap = map;
            } else {
                System.out.println(Color.colorize("Warning: saved worldMap has unexpected type; initializing empty world.", Color.YELLOW));
                worldMap = new HashMap<>();
            }
            questManager = (QuestManager) ois.readObject();
            equippedWeapon = (String) ois.readObject();
            equippedArmor = (String) ois.readObject();
            gameTimer = ois.readInt();
            useColor = ois.readBoolean();
            Color.USE_ANSI = useColor;
            System.out.println(Color.colorize("Game loaded successfully from " + inFile.getPath(), Color.GREEN));
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(Color.colorize("Error loading game: " + e.getMessage(), Color.RED));
        }
    }

    /**
     * Legacy load (previous behaviour) — load from "savegame.dat" in app root.
     */
    private synchronized void loadGame() {
        loadGameFrom("savegame.dat");
    }

    private void printCenteredLine(String text, String color) {
        String trimmed = text == null ? "" : text.trim();
        int contentWidth = MENU_WIDTH - 2;
        if (trimmed.length() > contentWidth) {
            // If text is too long, truncate and add "..."
            trimmed = trimmed.substring(0, contentWidth - 3) + "...";
        }
        int padding = (contentWidth - trimmed.length()) / 2;
        if (padding < 0) padding = 0;
        String line = "|" + " ".repeat(padding) + trimmed + " ".repeat(contentWidth - padding - trimmed.length()) + "|";
        System.out.println(Color.colorize(line, color));
    }

    private void printBorder(String type) {
        System.out.println(Color.colorize("+" + "=".repeat(MENU_WIDTH - 2) + "+", Color.WHITE));
    }

    private void initializeFactions() {
        availableFactions = new ArrayList<>();
        availableFactions.add(new Faction("Hackers Alliance"));
        availableFactions.add(new Faction("Cyber Thieves"));
        availableFactions.add(new Faction("Shadow Coders"));
        availableFactions.add(new Faction("Tech University"));
        availableFactions.add(new Faction("Firewall Guardians"));
    }

    private void initializeWorld() {
        worldMap = new HashMap<>();

        Location centralServer = new Location("Central Server Hub",
                "A vast data center with interconnected servers, home to programmers and the mighty Hackers Alliance.", 1, true,
                new String[]{"Virus", "Trojan", "Malware"});
        centralServer.addFeature("Hackers Den");
        centralServer.addFeature("Black Market");
        worldMap.put("Central Server Hub", centralServer);

        Location darkWeb = new Location("Dark Web Forest",
                "A dense network shrouded in encryption, hiding cybercriminals and data smugglers.", 2, true,
                new String[]{"Trojan", "Spyware", "Worm"});
        darkWeb.addFeature("Cyber Thieves Hideout");
        darkWeb.addFeature("Encrypted Keep");
        worldMap.put("Dark Web Forest", darkWeb);

        Location firewall = new Location("Firewall Cliffs",
                "Towering firewalls overlooking the digital sea, home to the Firewall Guardians and elite coders.", 3, true,
                new String[]{"Ransomware", "Phishing", "Trojan"});
        firewall.addFeature("Blue Palace");
        firewall.addFeature("Docks");
        worldMap.put("Firewall Cliffs", firewall);

        Location frozenCode = new Location("Frozen Code Tundra",
                "A frozen wasteland where hackers study ancient algorithms at the Tech University.", 4, false,
                new String[]{"Ice Virus", "Frost Worm", "Glitch"});
        frozenCode.addFeature("Tech University");
        frozenCode.addFeature("Frozen Archives");
        frozenCode.setEnvironmentalEffect("Blizzard");
        worldMap.put("Frozen Code Tundra", frozenCode);

        Location corruptedData = new Location("Corrupted Data Ruins",
                "Ancient server ruins carved into mountains, plagued by malware and dark code.", 5, false,
                new String[]{"Malware", "Corrupt File", "Rootkit"});
        corruptedData.addFeature("Data Museum");
        corruptedData.addFeature("Shadow Coders Sanctuary");
        corruptedData.setEnvironmentalEffect("Cave-in");
        worldMap.put("Corrupted Data Ruins", corruptedData);

        Location backup = new Location("Backup Coast",
                "A chilly coastal server battered by data storms, known for its hardy backups.", 2, true,
                new String[]{"Backup Worm", "Trojan", "Ice Malware"});
        backup.addFeature("Backup Harbor");
        backup.addFeature("Data Mine");
        worldMap.put("Backup Coast", backup);

        Location malware = new Location("Malware Forest",
                "A lush network with towering data trees, haunted by viruses and cybercriminals.", 2, true,
                new String[]{"Trojan", "Spyware", "Worm"});
        malware.addFeature("Malware Graveyard");
        malware.addFeature("Jarl's Longhouse");
        worldMap.put("Malware Forest", malware);

        Location glitch = new Location("Glitch Marshes",
                "A foggy swamp filled with buggy code and dangerous glitches.", 3, true,
                new String[]{"Bug", "Glitch Troll", "Error"});
        glitch.addFeature("Glitch Inn");
        glitch.addFeature("Highmoon Hall");
        glitch.setEnvironmentalEffect("Thick Fog");
        worldMap.put("Glitch Marshes", glitch);

        Location encrypted = new Location("Encrypted Snowfields",
                "A snow-covered expanse surrounding the ancient city of the encrypted.", 3, true,
                new String[]{"Ice Virus", "Snow Worm", "Trojan"});
        encrypted.addFeature("Palace of the Kings");
        encrypted.addFeature("Candlehearth Hall");
        worldMap.put("Encrypted Snowfields", encrypted);

        Location dataStream = new Location("Data Stream Valley",
                "A peaceful valley with a rushing data stream, home to miners and traders.", 1, true,
                new String[]{"Virus", "Trojan", "Bug"});
        dataStream.addFeature("Sleeping Giant Inn");
        dataStream.addFeature("Data Stream Trader");
        worldMap.put("Data Stream Valley", dataStream);

        Location forgottenCache = new Location("Forgotten Cache Barrow",
                "An ancient data cache filled with traps and corrupt files.", 4, false,
                new String[]{"Corrupt File", "Skeleton Code", "Rootkit"});
        forgottenCache.addFeature("Ancient Altar");
        forgottenCache.addFeature("Code Wall");
        worldMap.put("Forgotten Cache Barrow", forgottenCache);

        Location summitServer = new Location("Summit Server",
                "The sacred mountain server of the Greybeards, shrouded in mist.", 5, false,
                new String[]{"Frost Worm", "Ice Virus", "Snow Malware"});
        summitServer.addFeature("Greybeard Sanctum");
        summitServer.addFeature("Meditation Chamber");
        worldMap.put("Summit Server", summitServer);

        Location remoteNode = new Location("Remote Node Village",
                "A small settlement at the base of the Summit Server.", 1, true,
                new String[]{"Virus", "Trojan", "Worm"});
        remoteNode.addFeature("Vilemyr Inn");
        remoteNode.addFeature("Riftweald Farm");
        worldMap.put("Remote Node Village", remoteNode);

        Location firewallBridge = new Location("Firewall Bridge",
                "A strategic crossing with a firewall bridge shaped like a dragon.", 2, true,
                new String[]{"Trojan", "Malware", "Ransomware"});
        firewallBridge.addFeature("Four Shields Tavern");
        firewallBridge.addFeature("Firewall Bridge Lumber Camp");
        worldMap.put("Firewall Bridge", firewallBridge);

        Location mining = new Location("Mining Hills",
                "Rugged hills rich with data mines, contested by ransomware.", 3, true,
                new String[]{"Ransomware", "Trojan", "Glitch"});
        mining.addFeature("Data Mine");
        mining.addFeature("Mining Hall");
        worldMap.put("Mining Hills", mining);

        Location fertile = new Location("Fertile Fields",
                "Fertile data fields known for bountiful code and peaceful folk.", 1, true,
                new String[]{"Virus", "Trojan", "Fox"});
        fertile.addFeature("Frostfruit Inn");
        fertile.addFeature("Cowflop Farm");
        worldMap.put("Fertile Fields", fertile);

        Location crashedSystem = new Location("Crashed System Ruins",
                "A destroyed server recently ravaged by a rootkit attack.", 3, false,
                new String[]{"Trojan", "Corrupt File", "Skeleton Code"});
        crashedSystem.addFeature("Burned Keep");
        crashedSystem.addFeature("Hidden Escape Tunnel");
        worldMap.put("Crashed System Ruins", crashedSystem);

        Location undergroundNetwork = new Location("Underground Network",
                "A vast underground network lit by glowing LEDs and server ruins.", 5, false,
                new String[]{"Malware", "Chaurus", "Automaton"});
        undergroundNetwork.addFeature("Tower of Mzark");
        undergroundNetwork.addFeature("Silent City");
        undergroundNetwork.setEnvironmentalEffect("Glowing Spores");
        worldMap.put("Underground Network", undergroundNetwork);

        Location voidCache = new Location("Void Cache",
                "A desolate plane of the void filled with lost data and necrotic energy.", 5, false,
                new String[]{"Boneman", "Mistman", "Wrathman"});
        voidCache.addFeature("Data Well");
        voidCache.addFeature("Boneyard");
        voidCache.setEnvironmentalEffect("Soul Drain");
        worldMap.put("Void Cache", voidCache);

        Location hiddenPartition = new Location("Hidden Partition",
                "A hidden glacial partition home to ancient malware temples.", 4, false,
                new String[]{"Malware", "Frostbite Spider", "Ice Virus"});
        hiddenPartition.addFeature("Auriel's Shrine");
        hiddenPartition.addFeature("Frozen Lake");
        hiddenPartition.setEnvironmentalEffect("Ancient Power");
        worldMap.put("Hidden Partition", hiddenPartition);

        Location overclocked = new Location("Overclocked Springs",
                "Steaming geothermal pools surrounded by volcanic circuits.", 3, false,
                new String[]{"Horker", "Troll", "Ash Spawn"});
        overclocked.addFeature("Sulfur Pools");
        overclocked.addFeature("Ancient Cairn");
        overclocked.setEnvironmentalEffect("Soothing Vapors");
        worldMap.put("Overclocked Springs", overclocked);

        Location bugBog = new Location("Bug Bog",
                "A treacherous wetland teeming with dangerous bugs.", 3, false,
                new String[]{"Bug", "Glitch Troll", "Chaurus"});
        bugBog.addFeature("Sunken Ruins");
        bugBog.addFeature("Bog Beacon");
        worldMap.put("Bug Bog", bugBog);

        Location frozenSector = new Location("Frozen Sector",
                "A stark, snowy landscape with scattered server ruins.", 2, false,
                new String[]{"Snow Virus", "Ice Virus", "Skeleton Code"});
        frozenSector.addFeature("Frostmere Crypt");
        frozenSector.addFeature("Ancient Watchtower");
        worldMap.put("Frozen Sector", frozenSector);

        Location isolatedCoast = new Location("Isolated Coast",
                "A frozen shoreline littered with crashed servers and ice floes.", 3, false,
                new String[]{"Horker", "Ice Malware", "Frost Worm"});
        isolatedCoast.addFeature("Wreck of the Winter War");
        isolatedCoast.addFeature("Ice Cave");
        worldMap.put("Isolated Coast", isolatedCoast);

        Location vampireServer = new Location("Vampire Server",
                "A foreboding vampire server on a remote island.", 5, false,
                new String[]{"Vampire", "Death Hound", "Gargoyle"});
        vampireServer.addFeature("Volkihar Cathedral");
        vampireServer.addFeature("Bloodstone Chalice");
        vampireServer.setEnvironmentalEffect("Vampiric Aura");
        worldMap.put("Vampire Server", vampireServer);

        Location ashNode = new Location("Ash Node",
                "A Dunmer colony on the ash-covered island of Solstheim.", 4, true,
                new String[]{"Ash Spawn", "Riekling", "Netch"});
        ashNode.addFeature("Redoran Council Hall");
        ashNode.addFeature("The Retching Netch");
        worldMap.put("Ash Node", ashNode);

        Location wizardTower = new Location("Wizard Tower",
                "A Telvanni wizard tower surrounded by ash and fungal growths.", 4, false,
                new String[]{"Ash Spawn", "Spriggan", "Burnt Spriggan"});
        wizardTower.addFeature("Telvanni Tower");
        wizardTower.addFeature("Silt Strider Stable");
        worldMap.put("Wizard Tower", wizardTower);

        Location nordicVillage = new Location("Nordic Village",
                "A small Nordic settlement on Solstheim, devoted to the All-Maker.", 2, true,
                new String[]{"Virus", "Worm", "Riekling"});
        nordicVillage.addFeature("Shaman's Hut");
        nordicVillage.addFeature("Greathall");
        worldMap.put("Nordic Village", nordicVillage);

        Location warriorHall = new Location("Warrior Hall",
                "A warrior lodge on Solstheim, recently reclaimed from Riekling.", 3, true,
                new String[]{"Riekling", "Worm", "Troll"});
        warriorHall.addFeature("Mead Hall");
        warriorHall.addFeature("Hunter's Camp");
        worldMap.put("Warrior Hall", warriorHall);

        Location forbiddenRealm = new Location("Forbidden Realm",
                "The otherworldly realm of Hermaeus Mora, filled with forbidden knowledge.", 5, false,
                new String[]{"Seeker", "Lurker", "Daedra"});
        forbiddenRealm.addFeature("Black Book Archive");
        forbiddenRealm.addFeature("Forbidden Library");
        forbiddenRealm.setEnvironmentalEffect("Forbidden Knowledge");
        worldMap.put("Forbidden Realm", forbiddenRealm);
    }

    public void displayMainMenu() {
        while (true) {
            printBorder("top");
            printCenteredLine("Codeborne: Odyssey of the Programmer", Color.PURPLE);
            printCenteredLine("A Tale of Code and Digital Adventures", Color.GRAY);
            printBorder("divider");
            printCenteredLine("1. Start New Game", Color.WHITE);
            printCenteredLine("2. Load Game", Color.WHITE);
            printCenteredLine("3. Exit", Color.WHITE);
            printBorder("bottom");
            System.out.print(Color.colorize("Choose an option (1-3): ", Color.YELLOW));

            String input = scan.nextLine().trim();
            if (input.equals("1")) {
                // ensure any previous game is requested stopped
                requestStopCurrentGame();
                resetForNewGame();
                startGame();
            } else if (input.equals("2")) {
                loadGame();
            } else if (input.equals("3")) {
                System.out.println(Color.colorize("Thank you for playing! Farewell, adventurer!", Color.GREEN));
                break;
            } else {
                System.out.println(Color.colorize("Invalid option. Please choose again.", Color.RED));
            }
        }
    }

    private void startGame() {
        // Defensive: ensure a previous run is stopped
        requestStopCurrentGame();

        resetForNewGame();

        printBorder("top");
        printCenteredLine("A New Legend Begins", Color.PURPLE);
        printBorder("divider");
        printCenteredLine("  Oh good, you're awake! Choose your path:  ", Color.YELLOW);
        System.out.println(Color.colorize("|" + " ".repeat(MENU_WIDTH - 2) + "|", Color.WHITE));
        System.out.println(Color.colorize("| 1. Debugger: Alex Codebreaker, The Bug Squasher           |", Color.BLUE));
        System.out.println(Color.colorize("|    - High health and defense, specializes in debugging    |", Color.GRAY));
        System.out.println(Color.colorize("| 2. Hacker: Maya Firewall, The Code Breaker                 |", Color.BLUE));
        System.out.println(Color.colorize("|    - Powerful code manipulator, specializes in exploits   |", Color.GRAY));
        System.out.println(Color.colorize("| 3. Tester: Sam Byte, The Bug Hunter                        |", Color.BLUE));
        System.out.println(Color.colorize("|    - Expert at finding vulnerabilities and bugs           |", Color.GRAY));
        System.out.println(Color.colorize("| 4. Architect: Linus Kernel, The System Designer            |", Color.BLUE));
        System.out.println(Color.colorize("|    - Strategic planner with strong defensive abilities     |", Color.GRAY));
        System.out.println(Color.colorize("| 5. PenTester: Vex Shadowblade, The Silent Intruder         |", Color.BLUE));
        System.out.println(Color.colorize("|    - Stealthy and deadly, excels at critical exploits      |", Color.GRAY));
        System.out.println(Color.colorize("| 6. Support: Elara Lightbringer, The System Maintainer       |", Color.BLUE));
        System.out.println(Color.colorize("|    - Supportive maintainer with powerful buffs and heals  |", Color.GRAY));
        System.out.println(Color.colorize("|" + " ".repeat(MENU_WIDTH - 2) + "|", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Enter your choice (1-6): ", Color.YELLOW));

        String choice = scan.nextLine().trim();
        switch (choice) {
            case "1":
                player = new Debugger();
                break;
            case "2":
                player = new Hacker();
                break;
            case "3":
                player = new Tester();
                break;
            case "4":
                player = new Architect();
                break;
            case "5":
                player = new PenTester();
                break;
            case "6":
                player = new Support();
                break;
            default:
                System.out.println(Color.colorize("Invalid choice. Defaulting to Debugger.", Color.RED));
                player = new Debugger();
        }
        System.out.println(Color.colorize("You are now a " + player.getClassName() + "! The world awaits your legend.", Color.GREEN));
        startGamePostSelection();
    }

    /**
     * Helper used for both console startGame() and the new GUI startGameWithChoice.
     * Adds starter quests and enters the in-game menu loop.
     */
    private void startGamePostSelection() {
        questManager.addQuest(
                "Code Hunt",
                "Seek a code snippet in Central Server Hub.",
                Arrays.asList("Find code snippet in Central Server Hub", "Return to Central Server Hub"),
                Map.of("gold", 100, "xp", 50),
                null
        );
        questManager.addQuest(
                "Virus Hunt",
                "Defeat a virus in Data Stream Valley.",
                Arrays.asList("Defeat a Virus in Data Stream Valley"),
                Map.of("gold", 50, "xp", 20),
                null
        );
        inGameMenu();
    }

    /**
     * Create a player by choice and start the game loop.
     */
    private void startGameWithChoice(int choice) {
        // Defensive: request stop of any previous run and reset state for a fresh run
        requestStopCurrentGame();
        resetForNewGame();

        switch (choice) {
            case 1: player = new Debugger(); break;
            case 2: player = new Hacker(); break;
            case 3: player = new Tester(); break;
            case 4: player = new Architect(); break;
            case 5: player = new PenTester(); break;
            case 6: player = new Support(); break;
            default: player = new Debugger(); break;
        }
        System.out.println(Color.colorize("You are now a " + player.getClassName() + "! The world awaits your legend.", Color.GREEN));
        startGamePostSelection();
    }

    private void inGameMenu() {
        // Mark game as running so external callers can request it to stop
        gameRunning = true;
        stopRequested = false;
        try {
            while (true) {
                if (stopRequested) throw new GameStopException();

                printBorder("top");
                printCenteredLine("Adventurer's Lodge", Color.PURPLE);
                printBorder("divider");
                printCenteredLine("1. Travel to New Lands", Color.WHITE);
                printCenteredLine("2. Manage Inventory and Equipment", Color.WHITE);
                printCenteredLine("3. View Gold", Color.WHITE);
                printCenteredLine("4. View Quest Log", Color.WHITE);
                printCenteredLine("5. View Player Stats", Color.WHITE);
                printCenteredLine("6. Rest at Inn", Color.WHITE);
                printCenteredLine("7. Faction Menu", Color.WHITE);
                printCenteredLine("8. Save Game", Color.WHITE);
                printBorder("bottom");
                System.out.print(Color.colorize("Choose an option (1-8): ", Color.YELLOW));

                int choice = getChoice(1, 8); // may throw GameStopException

                switch (choice) {
                    case 1:
                        travel();
                        break;
                    case 2:
                        manageInventoryAndEquipment();
                        break;
                    case 3:
                        viewGold();
                        break;
                    case 4:
                        viewQuestLog();
                        break;
                    case 5:
                        viewPlayerStats();
                        break;
                    case 6:
                        restAtInn();
                        break;
                    case 7:
                        factionMenu();
                        break;
                    case 8:
                        saveGame();
                        break;
                    default:
                        System.out.println(Color.colorize("Invalid option. Try again.", Color.RED));
                }
            }
        } catch (GameStopException e) {
            System.out.println(Color.colorize("Game interrupted by request. Returning to main menu.", Color.YELLOW));
        } finally {
            gameRunning = false;
            stopRequested = false;
        }
    }

    private void manageInventoryAndEquipment() {
        try {
            while (true) {
                if (stopRequested) throw new GameStopException();

                printBorder("top");
                printCenteredLine("Inventory and Equipment", Color.PURPLE);
                printBorder("divider");
                printCenteredLine("1. View Inventory", Color.WHITE);
                printCenteredLine("2. View Equipment", Color.WHITE);
                printCenteredLine("3. Equip or Use Item", Color.WHITE);
                printCenteredLine("4. Back to Main Menu", Color.WHITE);
                printBorder("bottom");
                System.out.print(Color.colorize("Choose an option (1-4): ", Color.YELLOW));

                String input = scan.nextLine().trim();
                if (stopRequested) throw new GameStopException();
                switch (input) {
                    case "1":
                        viewInventory();
                        break;
                    case "2":
                        viewEquipment();
                        break;
                    case "3":
                        equipOrUseItem();
                        break;
                    case "4":
                        return;
                    default:
                        System.out.println(Color.colorize("Invalid option. Try again.", Color.RED));
                }
            }
        } catch (GameStopException e) {
            throw e;
        }
    }

    private void factionMenu() {
        try {
            while (true) {
                if (stopRequested) throw new GameStopException();

                printBorder("top");
                printCenteredLine("Faction Hall", Color.PURPLE);
                printBorder("divider");
                printCenteredLine("1. Join a Faction", Color.WHITE);
                printCenteredLine("2. View Faction Status", Color.WHITE);
                printCenteredLine("3. Undertake Faction Quest", Color.WHITE);
                printCenteredLine("4. Back", Color.WHITE);
                printBorder("bottom");
                System.out.print(Color.colorize("Choose an option (1-4): ", Color.YELLOW));
                int choice = getChoice(1, 4);
                switch (choice) {
                    case 1:
                        joinFaction();
                        break;
                    case 2:
                        viewFactions();
                        break;
                    case 3:
                        doFactionQuest();
                        break;
                    case 4:
                        return;
                }
            }
        } catch (GameStopException e) {
            throw e;
        }
    }

    private void joinFaction() {
        printBorder("top");
        printCenteredLine("Available Factions", Color.PURPLE);
        printBorder("divider");
        int count = 0;
        for (int i = 0; i < availableFactions.size(); i++) {
            Faction faction = availableFactions.get(i);
            if (!player.isInFaction(faction.getName())) {
                count++;
                System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", count, faction.getName()) + " |", Color.WHITE));
            }
        }
        if (count == 0) {
            printCenteredLine("No factions available to join.", Color.GRAY);
            printBorder("bottom");
            return;
        }
        System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", count + 1, "Cancel") + " |", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Choose a faction (1-" + (count + 1) + "): ", Color.YELLOW));
        int choice = getChoice(1, count + 1);
        if (choice <= count) {
            int factionIdx = 0;
            for (Faction faction : availableFactions) {
                if (!player.isInFaction(faction.getName())) {
                    if (factionIdx == choice - 1) {
                        player.joinFaction(faction);
                        System.out.println(Color.colorize("You have joined the " + faction.getName() + "!", Color.GREEN));
                        return;
                    }
                    factionIdx++;
                }
            }
        }
    }

    private void viewFactions() {
        printBorder("top");
        printCenteredLine("Your Factions", Color.PURPLE);
        printBorder("divider");
        if (player.getFactions().isEmpty()) {
            printCenteredLine("You are not a member of any factions.", Color.GRAY);
        } else {
            for (Faction faction : player.getFactions()) {
                String factionLine = faction.getName() + " (Reputation: " + faction.getReputation() + ")";
                System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", factionLine) + " |", Color.WHITE));
            }
        }
        printBorder("bottom");
    }

    private void doFactionQuest() {
        if (player.getFactions().isEmpty()) {
            printBorder("top");
            printCenteredLine("Faction Quest", Color.PURPLE);
            printBorder("divider");
            printCenteredLine("You must join a faction first!", Color.RED);
            printBorder("bottom");
            return;
        }
        printBorder("top");
        printCenteredLine("Choose a Faction Quest", Color.PURPLE);
        printBorder("divider");
        List<Faction> playerFactions = player.getFactions();
        for (int i = 0; i < playerFactions.size(); i++) {
            System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", i + 1, playerFactions.get(i).getName()) + " |", Color.WHITE));
        }
        System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", playerFactions.size() + 1, "Cancel") + " |", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Choose a faction (1-" + (playerFactions.size() + 1) + "): ", Color.YELLOW));
        int choice = getChoice(1, playerFactions.size() + 1);
        if (choice <= playerFactions.size()) {
            Faction faction = playerFactions.get(choice - 1);
            String questName = getFactionQuestName(faction.getName());
            String objective = getFactionQuestObjective(faction.getName());
            questManager.addQuest(
                    questName,
                    objective,
                    Arrays.asList(objective),
                    Map.of("gold", 100, "xp", 50),
                    faction.getName()
            );
            player.addFactionReputation(faction.getName(), 50);
        }
    }

    private String getFactionQuestName(String factionName) {
        switch (factionName) {
            case "Companions":
                return "Clear Bandit Camp";
            case "Thieves Guild":
                return "Steal Noble Artifact";
            case "Dark Brotherhood":
                return "Assassinate Merchant";
            case "College of Winterhold":
                return "Retrieve Ancient Tome";
            case "Imperial Legion":
                return "Defend Supply Caravan";
            default:
                return "No Quest";
        }
    }

    private String getFactionQuestObjective(String factionName) {
        switch (factionName) {
            case "Companions":
                return "Clear a bandit camp near Whiterun Plains";
            case "Thieves Guild":
                return "Steal a valuable artifact in Riften Woods";
            case "Dark Brotherhood":
                return "Assassinate a corrupt merchant in Solitude Cliffs";
            case "College of Winterhold":
                return "Retrieve an ancient tome in Winterhold Tundra";
            case "Imperial Legion":
                return "Defend a supply caravan near Solitude Cliffs";
            default:
                return "No quest available";
        }
    }

    /**
     * Read an integer choice from the InputProvider. Throws GameStopException when a stop is requested.
     */
    private int getChoice(int min, int max) {
        while (true) {
            if (stopRequested) throw new GameStopException();
            String line = scan.nextLine();
            if (stopRequested) throw new GameStopException();
            if (line == null) line = "";
            line = line.trim();
            try {
                int choice = Integer.parseInt(line);
                if (choice >= min && choice <= max) {
                    return choice;
                }
                System.out.println("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    // --- travel, encounter, combat and supporting methods (unchanged) ---
    public void travel() {
        printBorder("top");
        printCenteredLine("Available Locations", Color.PURPLE);
        printBorder("divider");
        int i = 1;
        List<String> locations = new ArrayList<>(worldMap.keySet());
        for (String name : locations) {
            Location loc = worldMap.get(name);
            String locLine = String.format("%d. %s (Danger: %d/5)", i, name, loc.dangerLevel);
            printCenteredLine(locLine, Color.WHITE);
            printCenteredLine(loc.description, Color.GRAY);
            i++;
        }
        printBorder("bottom");
        System.out.print(Color.colorize("Choose a location (1-" + worldMap.size() + "): ", Color.YELLOW));
        try {
            int choice = getChoice(1, worldMap.size()) - 1;
            if (choice >= 0 && choice < worldMap.size()) {
                String destination = locations.get(choice);
                Location loc = worldMap.get(destination);
                enterLocation(loc);
            } else {
                System.out.println(Color.colorize("Invalid choice.", Color.RED));
            }
        } catch (GameStopException e) {
            throw e;
        }
    }

    private void enterLocation(Location loc) {
        if (stopRequested) throw new GameStopException();

        System.out.println(Color.colorize("\nYou arrive at " + loc.name, Color.YELLOW));
        System.out.println(Color.colorize(loc.description, Color.GRAY));

        if (loc.hasTown) {
            System.out.println(Color.colorize("\nThis location has a town where you can rest and trade.", Color.GREEN));
            encounterNPC(loc);
        }

        int encounters = 1 + random.nextInt(loc.dangerLevel);
        for (int i = 0; i < encounters; i++) {
            if (stopRequested) throw new GameStopException();
            if (random.nextFloat() < 0.6f) {
                generateEncounter(loc);
            } else {
                generateDiscovery(loc);
            }
        }
    }

    private void generateEncounter(Location loc) {
        if (stopRequested) throw new GameStopException();
        if (player == null) {
            System.out.println(Color.colorize("Error: No player selected. Please start a new game.", Color.RED));
            return;
        }
        enemy = new Enemy(Enemy.Tier.values()[random.nextInt(3)], player.level);
        enemy.changeName(loc.enemyPool[random.nextInt(loc.enemyPool.length)]);
        displayEnemyArt(enemy);
        String color = enemy.getTier() == Enemy.Tier.WEAK ? Color.GRAY
                : enemy.getTier() == Enemy.Tier.NORMAL ? Color.YELLOW : Color.RED;
        System.out.println("\n" + Color.colorize("You encounter a " + enemy.getDisplayName() + " in " + loc.name + "!", color));
        encounter(loc);
    }

    private void displayEnemyArt(Enemy enemy) {
        String enemyName = enemy.getCurrentName();
        if (enemyName.equals("Dragon")) {
            System.out.println(Color.colorize(
                    "     /|\\\n"
                            + "    / 0 \\\n"
                            + "   / ===Y*===\n"
                            + "  /_______/",
                    Color.RED));
        } else if (enemyName.equals("Bandit")) {
            System.out.println(Color.colorize(
                    "   O\n"
                            + "  /|\\\n"
                            + "  / \\",
                    Color.YELLOW));
        }
    }

    private void encounter(Location loc) {
        if (player == null || enemy == null) {
            System.out.println(Color.colorize("Error: Combat cannot start. Player or enemy is missing.", Color.RED));
            return;
        }

        List<String> combatLog = new ArrayList<>();
        combatLog.add("Combat starts!");

        try {
            while (player.hp > 0 && enemy.isAlive()) {
                if (stopRequested) throw new GameStopException();

                applyEnvironmentalEffects(player, enemy, loc);
                player.updateStatusEffects();
                enemy.updateStatusEffects();
                displayCombatStatus(player, enemy, combatLog);

                printBorder("top");
                printCenteredLine("Your turn! Choose an action:", Color.YELLOW);
                printBorder("divider");
                printCenteredLine("1. Attack", Color.WHITE);
                printCenteredLine("2. Use Skill", Color.WHITE);
                printCenteredLine("3. Use Shout", Color.WHITE);
                printCenteredLine("4. Flee", Color.WHITE);
                printBorder("bottom");
                System.out.print(Color.colorize("Choose an option (1-4): ", Color.YELLOW));
                int choice = getChoice(1, 4);

                boolean dodgeAttack = checkDodge(player);

                if (choice == 1) {
                    performPlayerAttack(player, enemy, combatLog);
                } else if (choice == 2) {
                    performPlayerSkill(player, enemy, combatLog);
                } else if (choice == 3) {
                    performPlayerShout(player, enemy, combatLog);
                } else {
                    if (random.nextInt(100) < 50) {
                        combatLog.add(player.getClassName() + " flees from battle!");
                        System.out.println(Color.colorize("You fled from the battle!", Color.YELLOW));
                        return;
                    } else {
                        combatLog.add(player.getClassName() + " fails to flee!");
                        System.out.println(Color.colorize("You failed to flee!", Color.RED));
                    }
                }

                if (player instanceof Architect && random.nextInt(100) < 15) {
                    int counterDmg = player.minDmg + random.nextInt(player.maxDmg - player.minDmg + 1);
                    combatLog.add(player.getClassName() + " counterattacks for " + counterDmg + " damage!");
                    System.out.println(Color.colorize(player.getClassName() + " counterattacks for " + counterDmg + " damage!", Color.GREEN));
                    enemy.receiveDamage(counterDmg);
                }

                player.decrementCooldowns();

                if (!enemy.isAlive()) {
                    handleVictory(player, combatLog, enemy);
                    break;
                }

                if (dodgeAttack || enemy.stunnedForNextTurn) {
                    combatLog.add("Enemy is stunned or you dodged, so they skip their turn!");
                    System.out.println(Color.colorize("Enemy is stunned or you dodged, so they skip their turn!", Color.GREEN));
                    enemy.stunnedForNextTurn = false;
                } else {
                    if (player instanceof PenTester && ((PenTester) player).smokeBombActive) {
                        combatLog.add("Enemy's attack is weakened by Smoke Bomb!");
                        System.out.println(Color.colorize("Enemy's attack is weakened by Smoke Bomb!", Color.YELLOW));
                        int originalMinDmg = enemy.minDmg;
                        int originalMaxDmg = enemy.maxDmg;
                        enemy.minDmg = (int) (enemy.minDmg * 0.5);
                        enemy.maxDmg = (int) (enemy.maxDmg * 0.5);
                        enemy.takeTurn(player);
                        enemy.minDmg = originalMinDmg;
                        enemy.maxDmg = originalMaxDmg;
                    } else {
                        enemy.takeTurn(player);
                    }
                }

                if (player.hp <= 0) {
                    combatLog.add("Defeat! You have been slain by the " + enemy.getCurrentName() + "...");
                    System.out.println(Color.colorize("You have been defeated by the " + enemy.getCurrentName() + "...", Color.RED));
                    break;
                }
            }
        } catch (GameStopException e) {
            System.out.println(Color.colorize("Combat interrupted by request. Exiting combat.", Color.YELLOW));
            return;
        }

        if (player != null && player.checkLevelUp()) {
            player.levelUp();
            combatLog.add(player.getClassName() + " levels up to " + player.level + "!");
        }

        System.out.println("\n" + Color.colorize("=== Combat Log Summary ===", Color.YELLOW));
        combatLog.forEach(System.out::println);
    }

    private boolean checkDodge(Hero player) {
        if (player instanceof Hacker && random.nextInt(100) < 20) {
            System.out.println(Color.colorize(player.getClassName() + " dodges the enemy’s next attack!", Color.GREEN));
            return true;
        } else if (player instanceof PenTester && random.nextInt(100) < 10) {
            System.out.println(Color.colorize("PenTester dodges the enemy’s next attack!", Color.GREEN));
            return true;
        }
        return false;
    }

    private void performPlayerAttack(Hero player, Enemy enemy, List<String> combatLog) {
        int damage = player.minDmg + random.nextInt(player.maxDmg - player.minDmg + 1);
        boolean isCritical = random.nextInt(100) < 15;
        if (isCritical) {
            damage *= 2;
            combatLog.add(player.getClassName() + " lands a critical hit for " + damage + " damage!");
            System.out.println(Color.colorize("Critical hit! You deal " + damage + " damage!", Color.GREEN));
        } else {
            combatLog.add(player.getClassName() + " attacks for " + damage + " damage!");
            System.out.println(Color.colorize("You attack for " + damage + " damage!", Color.WHITE));
        }
        enemy.receiveDamage(damage);
    }

    private void performPlayerSkill(Hero player, Enemy enemy, List<String> combatLog) {
        player.showAttacks();
        System.out.print(Color.colorize("Choose a skill (1-" + player.attackNames.length + "): ", Color.YELLOW));
        int skillChoice = getChoice(1, player.attackNames.length);
        String skillName = player.attackNames[skillChoice - 1];

        if (player instanceof Support) {
            if (skillName.equals("Fireball")) {
                int damage = player.minDmg + random.nextInt(player.maxDmg - player.minDmg + 1) + 10;
                enemy.receiveDamage(damage);
                enemy.applyStatusEffect(new StatusEffect("Burn", 3, 1.0, "damage", 5));
                combatLog.add(player.getClassName() + " casts Fireball for " + damage + " damage and applies Burn!");
            } else if (skillName.equals("Ice Storm")) {
                int damage = player.minDmg + random.nextInt(player.maxDmg - player.minDmg + 1) + 5;
                enemy.receiveDamage(damage);
                enemy.applyStatusEffect(new StatusEffect("Freeze", 2, 0.5, "damage", 0));
                combatLog.add(player.getClassName() + " casts Ice Storm for " + damage + " damage and applies Freeze!");
            } else {
                player.useSkill(skillChoice - 1, enemy);
                combatLog.add(player.getClassName() + " uses " + skillName);
            }
        } else {
            player.useSkill(skillChoice - 1, enemy);
            combatLog.add(player.getClassName() + " uses " + skillName);
        }
    }

    private void performPlayerShout(Hero player, Enemy enemy, List<String> combatLog) {
        player.showAttacks();
        System.out.print(Color.colorize("Choose a shout (1-" + player.getShouts().length + "): ", Color.YELLOW));
        int shoutChoice = getChoice(1, player.getShouts().length);
        String shoutName = player.getShouts()[shoutChoice - 1];

        player.useShout(shoutChoice - 1, enemy);
        combatLog.add(player.getClassName() + " shouts " + shoutName);
    }

    private void handleVictory(Hero player, List<String> combatLog, Enemy enemy) {
        player.addGold(20);
        player.addXP(20);
        combatLog.add("You gain 20 gold and 20 XP.");
        String color = enemy.getTier() == Enemy.Tier.WEAK ? Color.GRAY
                : enemy.getTier() == Enemy.Tier.NORMAL ? Color.YELLOW : Color.RED;
        combatLog.add("You defeated the " + Color.colorize(enemy.getDisplayName(), color) + "!");
        if (enemy.getCurrentName().equals("Virus")) {
            questManager.updateQuest("Defeat a Virus in Data Stream Valley", player);
            questManager.updateQuest("Clear a virus camp near Central Server Hub", player);
        }
    }

    private void applyEnvironmentalEffects(Hero player, Enemy enemy, Location loc) {
        if (loc.environmentalEffect == null) {
            return;
        }

        System.out.println(Color.colorize("The environment affects the battle!", Color.PURPLE));

        switch (loc.environmentalEffect) {
            case "Blizzard":
                System.out.println(Color.colorize("A fierce blizzard rages, dealing 5 damage to all combatants.", Color.BLUE));
                player.receiveDamage(5);
                enemy.receiveDamage(5);
                break;
            case "Cave-in":
                if (random.nextInt(100) < 10) {
                    System.out.println(Color.colorize("The cave trembles! A rock falls, dealing 20 damage to a random combatant.", Color.RED));
                    if (random.nextBoolean()) {
                        player.receiveDamage(20);
                    } else {
                        enemy.receiveDamage(20);
                    }
                }
                break;
            case "Thick Fog":
                System.out.println(Color.colorize("Thick fog reduces accuracy for all combatants.", Color.GRAY));
                // accuracy not implemented; just message
                break;
            case "Glowing Spores":
                System.out.println(Color.colorize("Glowing spores heal all combatants for 5 HP.", Color.GREEN));
                player.hp = Math.min(player.maxHP, player.hp + 5);
                enemy.hp = Math.min(enemy.maxHP, enemy.hp + 5);
                break;
            case "Soul Drain":
                System.out.println(Color.colorize("The Soul Cairn drains 5 mana from the player.", Color.PURPLE));
                player.mana = Math.max(0, player.mana - 5);
                break;
            case "Ancient Power":
                System.out.println(Color.colorize("The Forgotten Vale's ancient power boosts the player's damage by 10%.", Color.YELLOW));
                player.minDmg = (int) (player.minDmg * 1.1);
                player.maxDmg = (int) (player.maxDmg * 1.1);
                break;
            case "Soothing Vapors":
                System.out.println(Color.colorize("Soothing vapors from the hot springs restore 10 HP to all combatants.", Color.GREEN));
                player.hp = Math.min(player.maxHP, player.hp + 10);
                enemy.hp = Math.min(enemy.maxHP, enemy.hp + 10);
                break;
            case "Vampiric Aura":
                System.out.println(Color.colorize("A vampiric aura drains 5 HP from the player and heals the enemy.", Color.RED));
                player.receiveDamage(5);
                enemy.hp = Math.min(enemy.maxHP, enemy.hp + 5);
                break;
            case "Forbidden Knowledge":
                System.out.println(Color.colorize("Forbidden knowledge from Apocrypha boosts the player's mana regeneration by 5.", Color.BLUE));
                player.mana = Math.min(player.maxMana, player.mana + 5);
                break;
        }
    }

    private void displayCombatStatus(Hero player, Enemy enemy, List<String> combatLog) {
        printBorder("top");
        printCenteredLine("=== Combat Status ===", Color.YELLOW);
        printBorder("divider");
        System.out.printf("| %-30s | %-30s |%n",
                player.getClassName() + " (Lv " + player.level + ")",
                enemy.getDisplayName() + " (Lv " + enemy.level + ")");
        System.out.printf("| HP: %-25s | HP: %-25s |%n",
                player.hp + "/" + player.maxHP + " [" + "=".repeat(Math.max(0, player.hp * 20 / player.maxHP)) + "]",
                enemy.hp + "/" + enemy.maxHP + " [" + "=".repeat(Math.max(0, enemy.hp * 20 / enemy.maxHP)) + "]");
        System.out.printf("| Mana: %-25s | %-30s |%n",
                player.mana + "/" + player.maxMana + " [" + "=".repeat(Math.max(0, player.mana * 20 / player.maxMana)) + "]", "");
        String playerStatus = player.getStatusEffects().stream().map(StatusEffect::getName).collect(Collectors.joining(", "));
        String enemyStatus = enemy.getStatusEffects().stream().map(StatusEffect::getName).collect(Collectors.joining(", "));
        if (!playerStatus.isEmpty() || !enemyStatus.isEmpty()) {
            System.out.printf("| Status: %-25s | Status: %-25s |%n", playerStatus, enemyStatus);
        }
        printBorder("divider");
        System.out.println("| Recent Actions:");
        int start = Math.max(0, combatLog.size() - 3);
        for (int i = start; i < combatLog.size(); i++) {
            System.out.println("| " + combatLog.get(i));
        }
        printBorder("bottom");
    }

    private String getItemRarity(String itemName) {
        if (itemName.contains("Dragonbone") || itemName.contains("Dawnbreaker")
                || itemName.contains("Chillrend") || itemName.contains("Dragonbane")
                || itemName.contains("Archmage") || itemName.contains("Daedric")) {
            return Color.PURPLE;
        } else if (itemName.contains("Elven") || itemName.contains("Glass")
                || itemName.contains("Greater") || itemName.contains("Major")
                || itemName.contains("Cloak of Shadows") || itemName.contains("Nightshade")
                || itemName.contains("Orb of Elements")) {
            return Color.BLUE;
        } else if (itemName.contains("Steel") || itemName.contains("Mithril")
                || itemName.contains("Leather") || itemName.contains("Mana")
                || itemName.contains("Chainmail") || itemName.contains("Composite")
                || itemName.contains("Longbow")) {
            return Color.GREEN;
        } else {
            return Color.WHITE;
        }
    }

    private void generateDiscovery(Location loc) {
        if (stopRequested) throw new GameStopException();

        String[] discoveries = {
                "You find an abandoned campsite",
                "You discover a hidden cave",
                "You stumble upon an ancient relic",
                "You meet a traveling merchant"
        };

        String discovery = discoveries[random.nextInt(discoveries.length)];
        System.out.println("\n" + Color.colorize(discovery + " in " + loc.name, Color.GREEN));

        if (discovery.contains("relic")) {
            player.addItem("Ancient Relic", 1.0f);
            System.out.println(Color.colorize("You found an Ancient Relic!", Color.YELLOW));
            if (loc.name.equals("Central Server Hub")) {
                questManager.updateQuest("Find code snippet in Central Server Hub", player);
            }
            questManager.updateQuest("Find a Lost Relic for " + loc.name, player);
        }

        String[] loot = {"gold", "potion", "weapon", "armor", "food", "misc"};
        String found = loot[random.nextInt(loot.length)];

        if (found.equals("gold")) {
            int amount = 10 + random.nextInt(20 * loc.dangerLevel);
            player.addGold(amount);
            System.out.println(Color.colorize("You found " + amount + " gold!", Color.YELLOW));
        } else if (found.equals("potion")) {
            String[] potions = {
                    "Health Potion", "Mana Potion", "Greater Health Potion", "Major Health Potion",
                    "Minor Health Potion", "Greater Mana Potion", "Major Mana Potion", "Minor Mana Potion",
                    "Antidote Potion", "Fire Resistance Potion", "Frost Resistance Potion",
                    "Poison Resistance Potion", "Healing Elixir", "Potion of Ultimate Healing"
            };
            String potion = potions[random.nextInt(potions.length)];
            player.addItem(potion, 0.5f);
            System.out.println(Color.colorize("You found a " + potion + "!", getItemRarity(potion)));
        } else if (found.equals("weapon")) {
            String[] classWeapons = player instanceof Debugger ? new String[]{
                    "Iron Sword", "Steel Sword", "Mithril Sword", "Elven Sword", "Glass Sword",
                    "Daedric Sword", "Dragonbone Sword", "Dawnbreaker", "Chillrend", "Dragonbane"
            }
                    : player instanceof Hacker ? new String[]{
                    "Fire Staff", "Ice Wand", "Staff of Fireballs", "Staff of Ice Storms",
                    "Staff of Healing", "Wand of Mana", "Orb of Elements"
            }
                    : player instanceof Tester ? new String[]{
                    "Hunting Bow", "Longbow", "Composite Bow", "Elven Bow", "Glass Bow",
                    "Daedric Bow", "Dragonbone Bow"
            }
                    : player instanceof Architect ? new String[]{
                    "Warhammer", "Battleaxe", "Mace", "Flail"
            }
                    : player instanceof PenTester ? new String[]{
                    "Iron Dagger", "Steel Dagger", "Mithril Dagger", "Elven Dagger", "Glass Dagger",
                    "Daedric Dagger", "Ebony Dagger"
            }
                    : new String[]{
                    "Staff of Healing", "Holy Scepter", "Divine Mace"
            };
            String weapon = classWeapons[random.nextInt(classWeapons.length)];
            player.addItem(weapon, 2.0f);
            System.out.println(Color.colorize("You found a " + weapon + "!", getItemRarity(weapon)));
        } else if (found.equals("armor")) {
            String[] classNames = player instanceof Debugger ? new String[]{
                    "Plate Armor", "Dragonbone Armor"
            } : player instanceof Hacker ? new String[]{"Robe of Protection", "Archmage Robes"
            } : player instanceof Tester ? new String[]{
                    "Leather Armor", "Elven Armor"
            } : player instanceof Architect ? new String[]{
                    "Chainmail", "Dragonscale Armor"
            } : player instanceof PenTester ? new String[]{
                    "Cloak of Shadows", "Nightshade Cloak"
            } : new String[]{"Robe of Protection", "Holy Shroud"};
            String armor = classNames[random.nextInt(classNames.length)];
            player.addItem(armor, 3.0f);
            System.out.println(Color.colorize("You found a " + armor + "!", getItemRarity(armor)));
        } else if (found.equals("food")) {
            String[] foods = {
                    "Apple", "Bread Loaf", "Cheese Wheel", "Roasted Meat", "Vegetable Stew"
            };
            String food = foods[random.nextInt(foods.length)];
            player.addItem(food, 0.4f);
            System.out.println(Color.colorize("You found a " + food + "!", getItemRarity(food)));
        } else if (found.equals("misc")) {
            String[] misc = {"Torch", "Map of the Realm", "Ancient Coin", "Silver Ring", "Amulet of Talos"};
            String item = misc[random.nextInt(misc.length)];
            player.addItem(item, 0.3f);
            System.out.println(Color.colorize("You found a " + item + "!", getItemRarity(item)));
        }
    }

    private void encounterNPC(Location loc) {
        try {
            if (stopRequested) throw new GameStopException();

            printBorder("top");
            printCenteredLine("Town of " + loc.name, Color.PURPLE);
            printBorder("divider");
            printCenteredLine("You enter a bustling town...", Color.YELLOW);

            int npcCount = 4 + random.nextInt(2);
            List<Enemy> npcs = new ArrayList<>();
            List<String> npcNames = new ArrayList<>();
            LinkedList<String> availableNames = new LinkedList<>();
            availableNames.addAll(Arrays.asList(Enemy.listEnemyNames()));

            for (int i = 0; i < npcCount && !availableNames.isEmpty(); i++) {
                if (stopRequested) throw new GameStopException();
                Enemy npc = new Enemy();
                String uniqueName = availableNames.remove(random.nextInt(availableNames.size()));
                npc.changeName(uniqueName);
                npc.setHostile(random.nextInt(100) < 20);
                npcs.add(npc);
                String color = npc.isHostile() ? Color.RED : Color.GREEN;
                npcNames.add(Color.colorize(npc.getDisplayName(), color));
                System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", i + 1, npcNames.get(i)) + " |", Color.WHITE));
            }
            printBorder("bottom");

            while (true) {
                if (stopRequested) throw new GameStopException();
                printBorder("top");
                printCenteredLine("Town Options", Color.PURPLE);
                printBorder("divider");
                for (int i = 0; i < npcs.size(); i++) {
                    System.out.println(Color.colorize("| " + String.format("%d. Interact with %-" + (MENU_WIDTH - 14) + "s", i + 1, npcNames.get(i)) + " |", Color.WHITE));
                }
                System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", npcs.size() + 1, "Continue Exploring") + " |", Color.WHITE));
                System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", npcs.size() + 2, "Leave Town") + " |", Color.WHITE));
                printBorder("bottom");
                System.out.print(Color.colorize("Choose an option (1-" + (npcs.size() + 2) + "): ", Color.YELLOW));
                int choice = getChoice(1, npcs.size() + 2);

                try {
                    if (choice >= 1 && choice <= npcs.size()) {
                        Enemy npc = npcs.get(choice - 1);
                        if (npc.isHostile()) {
                            interactWithHostileNPC(npc, loc.name);
                        } else if (npc.isDocile()) {
                            System.out.println(Color.colorize("This NPC is friendly and cannot be fought.", Color.GREEN));
                            interactWithDocileNPC(npc.getCurrentName(), loc);
                        } else {
                            System.out.println(Color.colorize("This NPC's status is unclear. Try another.", Color.YELLOW));
                        }
                    } else if (choice == npcs.size() + 1) {
                        continueExploring(loc);
                    } else if (choice == npcs.size() + 2) {
                        System.out.println(Color.colorize("You leave the town.", Color.YELLOW));
                        break;
                    } else {
                        System.out.println(Color.colorize("Invalid choice.", Color.RED));
                    }
                } catch (GameStopException e) {
                    throw e;
                }
            }
        } catch (GameStopException e) {
            System.out.println(Color.colorize("Town interaction interrupted by request.", Color.YELLOW));
            throw e;
        }
    }

    private void continueExploring(Location loc) {
        if (stopRequested) throw new GameStopException();
        System.out.println(Color.colorize("\nYou continue exploring " + loc.name + "...", Color.YELLOW));
        if (random.nextFloat() < 0.5f) {
            generateRandomNPCEncounter(loc);
        } else {
            System.out.println(Color.colorize("You find nothing of interest.", Color.GRAY));
        }
    }

    private void generateRandomNPCEncounter(Location loc) {
        if (stopRequested) throw new GameStopException();
        if (player == null) {
            System.out.println(Color.colorize("Error: No player selected. Please start a new game.", Color.RED));
            return;
        }
        Enemy npc = new Enemy(Enemy.Tier.values()[random.nextInt(3)], player.level);
        npc.setHostile(random.nextFloat() < 0.3f);
        String color = npc.isHostile() ? Color.RED : Color.GREEN;
        System.out.println("\n" + Color.colorize("You encounter a " + npc.getDisplayName() + " while exploring " + loc.name + "!", color));
        if (npc.isHostile()) {
            interactWithHostileNPC(npc, loc.name);
        } else if (npc.isDocile()) {
            System.out.println(Color.colorize("This NPC is friendly and cannot be fought.", Color.GREEN));
            interactWithDocileNPC(npc.getCurrentName(), loc);
        } else {
            System.out.println(Color.colorize("This NPC's status is unclear. You avoid them.", Color.YELLOW));
        }
    }

    private void interactWithHostileNPC(Enemy npc, String location) {
        if (!npc.isHostile()) {
            System.out.println(Color.colorize(npc.getCurrentName() + " is not hostile. You cannot fight them.", Color.YELLOW));
            interactWithDocileNPC(npc.getCurrentName(), worldMap.get(location));
            return;
        }
        System.out.println(Color.colorize(npc.getDisplayName() + " attacks!", Color.RED));
        this.enemy = npc;
        encounter(worldMap.get(location));
    }

    private void interactWithDocileNPC(String npcName, Location loc) {
        printBorder("top");
        printCenteredLine("Friendly NPC: " + npcName, Color.GREEN);
        printBorder("divider");
        while (true) {
            if (stopRequested) throw new GameStopException();
            printCenteredLine("What would you like to do?", Color.YELLOW);
            printCenteredLine("1. Trade (buy/sell items)", Color.WHITE);
            printCenteredLine("2. Talk (learn about " + loc.name + ")", Color.WHITE);
            printCenteredLine("3. Leave", Color.WHITE);
            printBorder("bottom");
            System.out.print(Color.colorize("Choose an option (1-3): ", Color.YELLOW));
            String choice = scan.nextLine().trim();
            if (choice.equals("1")) {
                tradeWithNPC(npcName);
            } else if (choice.equals("2")) {
                talkToNPC(npcName, loc);
            } else if (choice.equals("3")) {
                System.out.println(Color.colorize("You leave " + npcName + ".", Color.YELLOW));
                break;
            } else {
                System.out.println(Color.colorize("Invalid option. Try again.", Color.RED));
            }
        }
    }

    private void tradeWithNPC(String npcName) {
        while (true) {
            if (stopRequested) throw new GameStopException();
            printBorder("top");
            printCenteredLine("Trading with " + npcName, Color.PURPLE);
            printBorder("divider");
            printCenteredLine("1. Buy Items", Color.WHITE);
            printCenteredLine("2. Sell Items", Color.WHITE);
            printCenteredLine("3. Cancel", Color.WHITE);
            printBorder("bottom");
            System.out.print(Color.colorize("Choose an option (1-3): ", Color.YELLOW));

            String choice = scan.nextLine().trim();
            if (choice.equals("1")) {
                buyItem(npcName);
            } else if (choice.equals("2")) {
                sellItem(npcName);
            } else if (choice.equals("3")) {
                System.out.println(Color.colorize("You stop trading with " + npcName + ".", Color.GREEN));
                break;
            } else {
                System.out.println(Color.colorize("Invalid option. Try again.", Color.RED));
            }
        }
    }

    private void buyItem(String npcName) {
        printBorder("top");
        printCenteredLine("Items for Sale from " + npcName, Color.PURPLE);
        printBorder("divider");
        String[] items;
        int[] prices;
        if (player instanceof Debugger) {
            items = new String[]{
                    "Iron Sword", "Steel Sword", "Mithril Sword", "Elven Sword", "Glass Sword",
                    "Daedric Sword", "Dragonbone Sword", "Dawnbreaker", "Chillrend", "Dragonbane",
                    "Plate Armor", "Dragonbone Armor",
                    "Health Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{10, 15, 20, 25, 30, 35, 40, 45, 42, 40, 20, 35, 5, 15, 10};
        } else if (player instanceof Hacker) {
            items = new String[]{
                    "Fire Staff", "Ice Wand", "Staff of Fireballs", "Staff of Ice Storm",
                    "Staff of Healing", "Wand of Lightning", "Orb of Elements",
                    "Robe of Protection", "Archmage Robes",
                    "Mana Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{15, 20, 25, 30, 35, 40, 45, 15, 25, 7, 15, 10};
        } else if (player instanceof Tester) {
            items = new String[]{
                    "Hunting Bow", "Longbow", "Composite Bow", "Elven Bow", "Glass Bow",
                    "Daedric Bow", "Dragonbone Bow",
                    "Leather Armor", "Elven Armor",
                    "Health Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{10, 15, 20, 25, 30, 35, 40, 15, 25, 5, 15, 10};
        } else if (player instanceof Architect) {
            items = new String[]{
                    "Warhammer", "Battleaxe", "Mace", "Flail",
                    "Chainmail", "Dragonscale Armor",
                    "Health Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{15, 20, 25, 30, 20, 35, 5, 15, 10};
        } else if (player instanceof PenTester) {
            items = new String[]{
                    "Iron Dagger", "Steel Dagger", "Mithril Dagger", "Elven Dagger", "Glass Dagger",
                    "Daedric Dagger", "Ebony Dagger",
                    "Cloak of Shadows", "Nightshade Cloak",
                    "Health Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{10, 15, 20, 25, 30, 35, 40, 15, 25, 5, 15, 10};
        } else { // Support/other
            items = new String[]{
                    "Staff of Healing", "Holy Scepter", "Divine Mace",
                    "Robe of Protection", "Holy Shroud",
                    "Mana Potion", "Potion of Ultimate Healing", "Amulet of Talos"
            };
            prices = new int[]{15, 20, 25, 15, 25, 7, 15, 10};
        }

        for (int i = 0; i < items.length; i++) {
            System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 14) + "s", i + 1, items[i] + " (" + prices[i] + " gold)") + " |", getItemRarity(items[i])));
        }
        System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", items.length + 1, "Cancel") + " |", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Choose an item to buy (1-" + (items.length + 1) + "): ", Color.YELLOW));
        int choice = getChoice(1, items.length + 1);

        if (choice <= items.length) {
            String item = items[choice - 1];
            int price = prices[choice - 1];
            if (player.spendGold(price)) {
                player.addItem(item, 1.0f);
                System.out.println(Color.colorize("You bought a " + item + " for " + price + " gold!", getItemRarity(item)));
            } else {
                // spendGold already printed "Not enough gold!"
            }
        } else {
            System.out.println(Color.colorize("Purchase cancelled.", Color.YELLOW));
        }
    }

    private void sellItem(String npcName) {
        printBorder("top");
        printCenteredLine("Your Inventory for Sale to " + npcName, Color.PURPLE);
        printBorder("divider");
        List<Hero.InventoryItem> inventory = player.getInventory();
        if (inventory.isEmpty()) {
            printCenteredLine("Your inventory is empty!", Color.GRAY);
            printBorder("bottom");
            return;
        }

        int[] prices = new int[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            Hero.InventoryItem item = inventory.get(i);
            prices[i] = (int) (Math.random() * 10 + 5); // Random price 5-15 gold
            System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 14) + "s", i + 1, item.name + " x" + item.quantity + " (" + prices[i] + " gold)") + " |", getItemRarity(item.name)));
        }
        System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", inventory.size() + 1, "Cancel") + " |", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Choose an item to sell (1-" + (inventory.size() + 1) + "): ", Color.YELLOW));

        int choice = getChoice(1, inventory.size() + 1);

        if (choice <= inventory.size()) {
            Hero.InventoryItem item = inventory.get(choice - 1);
            int price = prices[choice - 1];
            player.removeItem(item.name);
            player.addGold(price);
            System.out.println(Color.colorize("You sold a " + item.name + " for " + price + " gold!", getItemRarity(item.name)));
        } else {
            System.out.println(Color.colorize("Sale cancelled.", Color.YELLOW));
        }
    }

    private void talkToNPC(String npcName, Location loc) {
        printBorder("top");
        printCenteredLine("Conversation with " + npcName, Color.GREEN);
        printBorder("divider");
        String[] rumors = {
                npcName + " shares a tale about a hidden treasure in " + loc.name + ".",
                npcName + " warns you about a dangerous " + loc.enemyPool[random.nextInt(loc.enemyPool.length)] + " nearby.",
                npcName + " mentions a local festival happening soon in " + loc.name + ".",
                npcName + " offers insight about a powerful artifact lost in " + loc.name + "."
        };
        String rumor = rumors[random.nextInt(rumors.length)];
        System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", rumor) + " |", Color.WHITE));
        printBorder("bottom");
        if (rumor.contains("powerful artifact")) {
            questManager.addQuest(
                    "Find a Lost Relic for " + loc.name,
                    "Locate the artifact mentioned by " + npcName + " in " + loc.name + ".",
                    Arrays.asList("Find a Lost Relic for " + loc.name),
                    Map.of("gold", 150, "xp", 75),
                    null
            );
        }
        if (loc.name.equals("Whiterun Plains")) {
            questManager.updateQuest("Return to Whiterun", player);
        }
    }

    private void viewInventory() {
        printBorder("top");
        printCenteredLine("Your Inventory", Color.PURPLE);
        printBorder("divider");
        List<Hero.InventoryItem> inventory = player.getInventory();
        if (inventory.isEmpty()) {
            printCenteredLine("Your inventory is empty!", Color.GRAY);
        } else {
            for (Hero.InventoryItem item : inventory) {
                String itemLine = item.name + " x" + item.quantity + " (Weight: " + item.weight + ")";
                System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", itemLine) + " |", getItemRarity(item.name)));
            }
        }
        printBorder("bottom");
    }

    private void viewEquipment() {
        printBorder("top");
        printCenteredLine("Your Equipment", Color.PURPLE);
        printBorder("divider");
        printCenteredLine("Weapon: " + equippedWeapon, getItemRarity(equippedWeapon));
        printCenteredLine("Armor: " + equippedArmor, getItemRarity(equippedArmor));
        printBorder("bottom");
    }

    private void equipOrUseItem() {
        printBorder("top");
        printCenteredLine("Equip or Use Item", Color.PURPLE);
        printBorder("divider");
        List<Hero.InventoryItem> inventory = player.getInventory();
        if (inventory.isEmpty()) {
            printCenteredLine("Your inventory is empty!", Color.GRAY);
            printBorder("bottom");
            return;
        }

        for (int i = 0; i < inventory.size(); i++) {
            Hero.InventoryItem item = inventory.get(i);
            System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", i + 1, item.name) + " |", getItemRarity(item.name)));
        }
        System.out.println(Color.colorize("| " + String.format("%d. %-" + (MENU_WIDTH - 6) + "s", inventory.size() + 1, "Cancel") + " |", Color.WHITE));
        printBorder("bottom");
        System.out.print(Color.colorize("Choose an item (1-" + (inventory.size() + 1) + "): ", Color.YELLOW));
        int choice = getChoice(1, inventory.size() + 1);

        if (choice <= inventory.size()) {
            Hero.InventoryItem item = inventory.get(choice - 1);
            String itemName = item.name;
            if (itemName.toLowerCase().contains("potion") || itemName.toLowerCase().contains("elixir")) {
                if (itemName.toLowerCase().contains("health")) {
                    player.hp = Math.min(player.maxHP, player.hp + 50);
                    System.out.println(Color.colorize("You used a " + itemName + " and restored 50 HP!", getItemRarity(itemName)));
                } else if (itemName.toLowerCase().contains("mana")) {
                    player.mana = Math.min(player.maxMana, player.mana + 50);
                    System.out.println(Color.colorize("You used a " + itemName + " and restored 50 Mana!", getItemRarity(itemName)));
                } else {
                    // generic potion or elixir
                    player.hp = Math.min(player.maxHP, player.hp + 20);
                    player.mana = Math.min(player.maxMana, player.mana + 20);
                    System.out.println(Color.colorize("You used a " + itemName + "!", getItemRarity(itemName)));
                }
                player.removeItem(itemName);
            } else if (itemName.contains("Sword") || itemName.contains("Staff") || itemName.contains("Bow")
                    || itemName.contains("Dagger") || itemName.contains("Mace") || itemName.contains("Warhammer")
                    || itemName.contains("Battleaxe") || itemName.contains("Flail") || itemName.contains("Wand")
                    || itemName.contains("Scepter")) {
                equippedWeapon = itemName;
                player.removeItem(itemName);
                System.out.println(Color.colorize("You equipped " + itemName + "!", getItemRarity(itemName)));
            } else if (itemName.contains("Armor") || itemName.contains("Robe") || itemName.contains("Cloak")
                    || itemName.contains("Chainmail") || itemName.contains("Shroud")) {
                equippedArmor = itemName;
                player.removeItem(itemName);
                System.out.println(Color.colorize("You equipped " + itemName + "!", getItemRarity(itemName)));
            } else {
                System.out.println(Color.colorize("You cannot use or equip " + itemName + ".", Color.RED));
            }
        } else {
            System.out.println(Color.colorize("Cancelled.", Color.YELLOW));
        }
    }

    private void viewGold() {
        printBorder("top");
        printCenteredLine("Your Wealth", Color.PURPLE);
        printBorder("divider");
        printCenteredLine("Gold: " + player.getGold(), Color.YELLOW);
        printBorder("bottom");
    }

    private void viewQuestLog() {
        printBorder("top");
        printCenteredLine("Quest Log", Color.BLUE);
        printBorder("divider");
        List<Quest> activeQuests = questManager.getActiveQuests();
        List<Quest> completedQuests = questManager.getCompletedQuests();
        if (activeQuests.isEmpty() && completedQuests.isEmpty()) {
            printCenteredLine("No quests available.", Color.GRAY);
        } else {
            if (!activeQuests.isEmpty()) {
                printCenteredLine("Active Quests", Color.YELLOW);
                for (Quest quest : activeQuests) {
                    System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", quest.getName()) + " |", Color.YELLOW));
                    System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", quest.getDescription()) + " |", Color.GRAY));
                    System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", "Objective: " + quest.getCurrentObjective()) + " |", Color.WHITE));
                }
            }
            if (!completedQuests.isEmpty()) {
                printCenteredLine("Completed Quests", Color.GREEN);
                for (Quest quest : completedQuests) {
                    System.out.println(Color.colorize("| " + String.format("%-" + (MENU_WIDTH - 4) + "s", quest.getName() + " (Completed)") + " |", Color.GREEN));
                }
            }
        }
        printBorder("bottom");
    }

    private void viewPlayerStats() {
        printBorder("top");
        printCenteredLine("Player Stats", Color.RED);
        printBorder("divider");
        printCenteredLine("Type: " + player.getClassName(), Color.YELLOW);
        printCenteredLine("Level: " + player.level, Color.WHITE);
        printCenteredLine("HP: " + player.hp + "/" + player.maxHP, Color.RED);
        printCenteredLine("Mana: " + player.mana + "/" + player.maxMana, Color.BLUE);
        printCenteredLine("Damage: " + player.minDmg + "-" + player.maxDmg, Color.WHITE);
        printCenteredLine("Gold: " + player.getGold(), Color.YELLOW);
        printCenteredLine("XP: " + player.xp + "/" + player.xpToLevel, Color.GREEN);
        String status = player.getStatusEffects().stream()
                .map(StatusEffect::getName)
                .collect(Collectors.joining(", "));
        if (!status.isEmpty()) {
            printCenteredLine("Status: " + status, Color.PURPLE);
        }
        printBorder("bottom");
    }

    private void restAtInn() {
        printBorder("top");
        printCenteredLine("Rest at Inn", Color.PURPLE);
        printBorder("divider");
        printCenteredLine("Resting costs 10 gold. Proceed?", Color.YELLOW);
        printCenteredLine("1. Yes", Color.WHITE);
        printCenteredLine("2. No", Color.WHITE);
        printBorder("bottom");
        System.out.print(Color.colorize("Choose an option (1-2): ", Color.YELLOW));
        int choice = getChoice(1, 2);
        if (choice == 1) {
            if (player.spendGold(10)) {
                player.hp = player.maxHP;
                player.mana = player.maxMana;
                player.clearStatusEffects();
                System.out.println(Color.colorize("You rest and recover fully! HP and Mana restored.", Color.GREEN));
            } else {
                // spendGold printed "Not enough gold!"
            }
        } else {
            System.out.println(Color.colorize("You decide not to rest.", Color.YELLOW));
        }
    }
}