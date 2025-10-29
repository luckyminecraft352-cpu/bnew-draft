/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package progescps;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Quest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String description;
    private ArrayList<String> objectives;
    private int currentObjective;
    private HashMap<String, Integer> rewards;
    private String faction;
    private boolean isComplete;

    public Quest(String name, String description, List<String> objectives, Map<String, Integer> rewards, String faction) {
        this.name = name;
        this.description = description;
        this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
        this.rewards = rewards != null ? new HashMap<>(rewards) : new HashMap<>();
        this.faction = faction;
        this.currentObjective = 0;
        this.isComplete = false;
    }

    public void progress() {
        if (currentObjective < objectives.size() - 1) {
            currentObjective++;
            System.out.println(Color.colorize("Quest updated: " + objectives.get(currentObjective), Color.YELLOW));
        } else {
            complete();
        }
    }

    private void complete() {
        isComplete = true;
        System.out.println(Color.colorize("Quest completed: " + name, Color.GREEN));
    }

    public String getName() {
        return name;
    }

    public String getCurrentObjective() {
        return objectives.get(currentObjective);
    }

    public Map<String, Integer> getRewards() {
        return rewards;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public String getDescription() {
        return description;
    }
}