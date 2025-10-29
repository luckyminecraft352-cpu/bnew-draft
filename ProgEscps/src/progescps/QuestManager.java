package progescps;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QuestManager: active/completed quest lists are persisted (no transient).
 * readObject still guards initialization for backward compatibility.
 */
public class QuestManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Quest> activeQuests = new ArrayList<>();
    private List<Quest> completedQuests = new ArrayList<>();

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (this.activeQuests == null) this.activeQuests = new ArrayList<>();
        if (this.completedQuests == null) this.completedQuests = new ArrayList<>();
    }

    public void addQuest(String name, String description, List<String> objectives, Map<String, Integer> rewards, String faction) {
        Quest quest = new Quest(name, description, objectives, rewards, faction);
        activeQuests.add(quest);
        System.out.println(Color.colorize("New quest: " + quest.getName(), Color.YELLOW));
    }

    public void updateQuest(String objective, Hero hero) {
        for (Quest quest : new ArrayList<>(activeQuests)) { // Avoid ConcurrentModificationException
            if (quest.getCurrentObjective().equals(objective)) {
                quest.progress();
                if (quest.isComplete()) {
                    applyRewards(quest, hero);
                    completedQuests.add(quest);
                    activeQuests.remove(quest);
                }
                break;
            }
        }
    }

    private void applyRewards(Quest quest, Hero hero) {
        quest.getRewards().forEach((reward, amount) -> {
            if (reward.equals("gold")) hero.addGold(amount);
            else if (reward.equals("xp")) hero.addXP(amount);
            else if (reward.equals("item")) hero.addItem("Quest Reward Item", 1.0f); // Customize item
            System.out.println(Color.colorize("Received " + amount + " " + reward + "!", Color.GREEN));
        });
    }

    public List<Quest> getActiveQuests() {
        return activeQuests;
    }

    public List<Quest> getCompletedQuests() {
        return completedQuests;
    }
}