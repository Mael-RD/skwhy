package skwhy.modules;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.lang.util.SimpleEvent;
import skwhy.SkWhy;
import skwhy.modules.QuestElements.conditions.*;
import skwhy.modules.QuestElements.effects.*;
import skwhy.modules.QuestElements.events.*;
import skwhy.modules.QuestElements.expressions.*;
import skwhy.modules.QuestElements.sections.*;
import skwhy.modules.QuestElements.types.*;
import skwhy.quest.QuestManager;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.QuestObjective;

/**
 * Module de quêtes et de NPC : définitions chargées depuis des fichiers (YAML, ou NBT historique), progression des joueurs
 * en mémoire avec sauvegarde asynchrone, dialogues à réponses cliquables, marqueurs de NPC et boussole. Les scripts Skript
 * pilotent tout cela avec les syntaxes ci-dessous et ajoutent leurs propres types d'actions et de conditions.
 */
public class QuestModule implements AddonModule {

    @Override
    public String name() {
        return "QuestModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return true;
    }

    // init() : types en premier, avant toute syntaxe
    @Override
    public void init(SkriptAddon addon) {
        QuestType.register();
        QuestStatusType.register();
        QuestNpcType.register();
        QuestObjectiveType.register();
        QuestIndicatorType.register();
    }

    // load() : événements, sections, effets, conditions, expressions
    @Override
    public void load(SkriptAddon addon) {
        QuestManager.init(SkWhy.getInstance());

        loadEvents(addon);

        ExprQuest.register(addon);
        ExprQuestStatus.register(addon);
        ExprQuestStep.register(addon);
        ExprObjectiveProgress.register(addon);
        ExprQuestVariable.register(addon);
        ExprSelectedQuest.register(addon);
        ExprQuestsOf.register(addon);
        ExprCurrentObjectives.register(addon);
        ExprQuestInfo.register(addon);
        ExprQuestNpc.register(addon);
        ExprNpcEntities.register(addon);
        ExprNpcInfo.register(addon);
        ExprNpcPosition.register(addon);
        ExprKnownName.register(addon);
        ExprDialogueState.register(addon);
        ExprNpcIndicator.register(addon);
        ExprEventIndicator.register(addon);
        ExprCompass.register(addon);
        ExprCompassTargets.register(addon);

        CondQuestState.register(addon);
        CondQuestAvailable.register(addon);
        CondAtStep.register(addon);
        CondRewardClaimed.register(addon);
        CondKnowsName.register(addon);
        CondCompassEnabled.register(addon);

        EffUnlockQuest.register(addon);
        EffAdvanceQuest.register(addon);
        EffProgressObjective.register(addon);
        EffTriggerAction.register(addon);
        EffClaimReward.register(addon);
        EffSelectQuest.register(addon);
        EffOpenQuestMenu.register(addon);
        EffShowHideNpc.register(addon);
        EffMoveNpc.register(addon);
        EffSendQuestMessage.register(addon);
        EffStartDialogue.register(addon);
        EffShowIndicator.register(addon);
        EffCompass.register(addon);
        EffReloadQuests.register(addon);
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            EffNpcLook.register(addon);
        } catch (ClassNotFoundException e) {
            SkWhy.getInstance().getLogger().info("[Quest] PacketEvents absent — 'make quest npc look at' non chargé.");
        }

        SecSayWithAnswers.register(addon);
        SecAnswer.register(addon);
        SecRegisterAction.register(addon);
        SecRegisterCondition.register(addon);
        SecCreateQuest.register(addon);
    }

    /** À appeler depuis onDisable() du plugin principal (sinon l'arrêt est détecté par l'écouteur du module). */
    public static void shutdown() {
        QuestManager.shutdown();
    }

    // ── Événements ──────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private void event(SkriptAddon addon, String title, Class<? extends Event> type, String pattern, String description, String[] examples, String... keywords) {
        this.moduleRegistry(addon).register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, title)
                .addEvent(type)
                .addPattern(pattern)
                .addDescription(description)
                .addExamples(examples)
                .addKeywords(keywords)
                .addSince("1.6.0")
                .build()
        );
    }

    @SuppressWarnings("null")
    private void loadEvents(SkriptAddon addon) {
        String player = " 'event-player' is the player (not set when he is offline), 'event-offlineplayer' always works.";
        String quest = " 'event-quest' is the quest.";

        event(addon, "[Quest] Quest Unlock", QuestUnlockEvent.class, "quest unlock",
            "Fires when a player is about to unlock a quest (first time he gets it). Cancelling keeps the quest unknown." + player + quest,
            new String[]{"on quest unlock:", "\tsend \"Nouvelle quête : %quest name of event-quest%\" to event-player"}, "quest", "unlock");
        event(addon, "[Quest] Quest Step Change", QuestStepChangeEvent.class, "quest step change",
            "Fires when a player is about to change step in a quest (including the first step after the unlock, a reset, and the finish of the quest). " +
                "Cancelling keeps the player where he is." + player + quest + " 'event-old step number' and 'event-new step number' are the old and new steps numbered from 1 " +
                "(0: not started; after the last step: finished).",
            new String[]{"on quest step change:", "\tif event-new step number is 3:", "\t\tsend \"Étape 3 !\" to event-player"}, "quest", "step");
        event(addon, "[Quest] Quest Objective Progress", QuestObjectiveProgressEvent.class, "quest objective progress",
            "Fires when an objective of a quest is about to progress or regress. Cancelling keeps its current value." + player + quest
                + " 'event-questobjective' is the objective; 'event-old progress' and 'event-new progress' are its values before and after (capped to the objective count).",
            new String[]{"on quest objective progress:", "\tsend \"%event-new progress%\" to event-player"}, "quest", "objective", "progress");
        event(addon, "[Quest] Quest Objective Complete", QuestObjectiveCompleteEvent.class, "quest objective complete",
            "Fires when a player completes an objective of a quest." + player + quest + " 'event-questobjective' is the objective.",
            new String[]{"on quest objective complete:", "\tsend \"Objectif %quest objective number of event-questobjective% terminé\" to event-player"}, "quest", "objective");
        event(addon, "[Quest] Quest Finish", QuestFinishEvent.class, "quest finish",
            "Fires when a player finishes a quest (he gets past its last step)." + player + quest,
            new String[]{"on quest finish:", "\tsend \"Bravo !\" to event-player"}, "quest", "finish");
        event(addon, "[Quest] Quest Reward Claim", QuestRewardClaimEvent.class, "quest reward claim",
            "Fires when a player is about to claim the reward of a finished quest. Cancelling refuses the reward." + player + quest,
            new String[]{"on quest reward claim:", "\tif event-player has no permission \"vip\":", "\t\tcancel event"}, "quest", "reward");
        event(addon, "[Quest] Quest Variable Change", QuestVariableChangeEvent.class, "quest variable change",
            "Fires when a variable of a quest is about to change. Cancelling keeps the value." + player + quest
                + " 'event-variable name' is the name, 'event-old variable value' and 'event-new variable value' the values (not set when the variable did not exist or is removed).",
            new String[]{"on quest variable change:", "\tif event-variable name is \"mana_help\":", "\t\tsend \"%event-new variable value%\" to event-player"}, "quest", "variable");
        event(addon, "[Quest] Quest Select", QuestSelectEvent.class, "quest select",
            "Fires when a player follows another quest (or none)." + player + " 'event-selected quest' is the new quest and 'event-old selected quest' the previous one (not set when there was none).",
            new String[]{"on quest select:", "\tsend \"Tu suis %quest name of event-selected quest%\" to event-player"}, "quest", "select");
        event(addon, "[Quest] Quest NPC Interact", QuestNpcInteractEvent.class, "quest npc interact",
            "Fires when a player right-clicks (talk) or left-clicks (hit) an entity that stands for a quest NPC. Cancelling stops all that SkWhy would do (message, dialogue, trade...)." + player
                + " 'event-questnpc' is the NPC, 'event-entity' the clicked entity and 'event-clicktype' LEFT or RIGHT.",
            new String[]{"on quest npc interact:", "\tif event-clicktype is left:", "\t\tcancel event"}, "quest", "npc", "interact", "click");
        event(addon, "[Quest] Quest NPC Meet", QuestNpcMeetEvent.class, "quest npc meet",
            "Fires the first time a player talks to a quest NPC." + player + " 'event-questnpc' is the NPC.",
            new String[]{"on quest npc meet:", "\tsend \"Tu rencontres %quest npc name of event-questnpc%\" to event-player"}, "quest", "npc", "meet");
        event(addon, "[Quest] Quest NPC Name Learn", QuestNpcNameLearnEvent.class, "quest npc name learn",
            "Fires when a player learns the name of a quest NPC (it said its own name, or the name was set as known)." + player + " 'event-questnpc' is the NPC.",
            new String[]{"on quest npc name learn:", "\tsend \"Il s'appelle %quest npc name of event-questnpc%\" to event-player"}, "quest", "npc", "name");
        event(addon, "[Quest] Quest NPC Position Change", QuestNpcPositionChangeEvent.class, "quest npc position change",
            "Fires when the position a player sees a quest NPC at is about to change. Cancelling keeps the NPC where it is." + player
                + " 'event-questnpc' is the NPC; 'event-old npc position' and 'event-new npc position' are the positions (from 1).",
            new String[]{"on quest npc position change:", "\tsend \"%event-new npc position%\" to event-player"}, "quest", "npc", "position");
        event(addon, "[Quest] Quest Dialogue Start", QuestDialogueStartEvent.class, "quest dialogue start",
            "Fires when a quest NPC is about to show a text of a dialogue to a player. Cancelling shows nothing." + player
                + " 'event-questnpc' is the NPC and 'event-dialogue name' the name of the dialogue.",
            new String[]{"on quest dialogue start:", "\tif event-dialogue name is \"start\":", "\t\tplay sound \"entity.villager.yes\" to event-player"}, "quest", "dialogue");
        event(addon, "[Quest] Quest Dialogue Answer", QuestDialogueAnswerEvent.class, "quest dialogue answer",
            "Fires when a player clicks an answer of a quest NPC (from a dialogue of a file or from 'with answers'). Cancelling ignores the click." + player
                + " 'event-questnpc' is the NPC and 'event-dialogue answer' the text of the answer.",
            new String[]{"on quest dialogue answer:", "\tsend \"Tu as répondu %event-dialogue answer%\" to event-player"}, "quest", "dialogue", "answer");
        event(addon, "[Quest] Quest Indicator Update", QuestIndicatorUpdateEvent.class, "quest indicator update",
            "Fires when the marker above a quest NPC is about to change for a player. Cancelling keeps the previous marker." + player
                + " 'event-questnpc' is the NPC, 'event-questindicator' the new marker and 'event-old indicator' the previous one; "
                + "'displayed quest indicator' can be set to show another marker.",
            new String[]{"on quest indicator update:", "\tif displayed quest indicator is new:", "\t\tset displayed quest indicator to main"}, "quest", "indicator", "marker");

        EventValueRegistry reg = addon.registry(EventValueRegistry.class);
        reg.register(EventValue.simple(QuestPlayerEvent.class, Player.class, QuestPlayerEvent::getPlayer));
        reg.register(EventValue.simple(QuestPlayerEvent.class, OfflinePlayer.class, QuestPlayerEvent::getOfflinePlayer));

        reg.register(EventValue.simple(QuestUnlockEvent.class, Quest.class, QuestUnlockEvent::getQuest));
        reg.register(EventValue.simple(QuestStepChangeEvent.class, Quest.class, QuestStepChangeEvent::getQuest));
        reg.register(EventValue.builder(QuestStepChangeEvent.class, Integer.class).patterns("new step number").getter(QuestStepChangeEvent::getStepNumber).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestStepChangeEvent.class, Integer.class).patterns("old step number").getter(QuestStepChangeEvent::getPastStepNumber).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(QuestObjectiveProgressEvent.class, Quest.class, QuestObjectiveProgressEvent::getQuest));
        reg.register(EventValue.simple(QuestObjectiveProgressEvent.class, QuestObjective.class, QuestObjectiveProgressEvent::getObjective));
        reg.register(EventValue.builder(QuestObjectiveProgressEvent.class, Integer.class).patterns("new progress").getter(QuestObjectiveProgressEvent::getProgress).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestObjectiveProgressEvent.class, Integer.class).patterns("old progress").getter(QuestObjectiveProgressEvent::getPastProgress).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(QuestObjectiveCompleteEvent.class, Quest.class, QuestObjectiveCompleteEvent::getQuest));
        reg.register(EventValue.simple(QuestObjectiveCompleteEvent.class, QuestObjective.class, QuestObjectiveCompleteEvent::getObjective));
        reg.register(EventValue.simple(QuestFinishEvent.class, Quest.class, QuestFinishEvent::getQuest));
        reg.register(EventValue.simple(QuestRewardClaimEvent.class, Quest.class, QuestRewardClaimEvent::getQuest));
        reg.register(EventValue.simple(QuestVariableChangeEvent.class, Quest.class, QuestVariableChangeEvent::getQuest));
        reg.register(EventValue.builder(QuestVariableChangeEvent.class, String.class).patterns("variable name").getter(QuestVariableChangeEvent::getName).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestVariableChangeEvent.class, Object.class).patterns("new variable value").getter(QuestVariableChangeEvent::getValue).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestVariableChangeEvent.class, Object.class).patterns("old variable value").getter(QuestVariableChangeEvent::getPastValue).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestSelectEvent.class, Quest.class).patterns("selected quest").getter(QuestSelectEvent::getQuest).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestSelectEvent.class, Quest.class).patterns("old selected quest").getter(QuestSelectEvent::getPastQuest).time(EventValue.Time.NOW).build());

        reg.register(EventValue.simple(QuestNpcInteractEvent.class, QuestNpc.class, QuestNpcInteractEvent::getNpc));
        reg.register(EventValue.simple(QuestNpcInteractEvent.class, Entity.class, QuestNpcInteractEvent::getEntity));
        reg.register(EventValue.simple(QuestNpcInteractEvent.class, ClickType.class, QuestNpcInteractEvent::getClickType));
        reg.register(EventValue.simple(QuestNpcMeetEvent.class, QuestNpc.class, QuestNpcMeetEvent::getNpc));
        reg.register(EventValue.simple(QuestNpcNameLearnEvent.class, QuestNpc.class, QuestNpcNameLearnEvent::getNpc));
        reg.register(EventValue.simple(QuestNpcPositionChangeEvent.class, QuestNpc.class, QuestNpcPositionChangeEvent::getNpc));
        reg.register(EventValue.builder(QuestNpcPositionChangeEvent.class, Integer.class).patterns("new npc position").getter(QuestNpcPositionChangeEvent::getPosition).time(EventValue.Time.NOW).build());
        reg.register(EventValue.builder(QuestNpcPositionChangeEvent.class, Integer.class).patterns("old npc position").getter(QuestNpcPositionChangeEvent::getPastPosition).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(QuestDialogueStartEvent.class, QuestNpc.class, QuestDialogueStartEvent::getNpc));
        reg.register(EventValue.builder(QuestDialogueStartEvent.class, String.class).patterns("dialogue name").getter(QuestDialogueStartEvent::getDialogue).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(QuestDialogueAnswerEvent.class, QuestNpc.class, QuestDialogueAnswerEvent::getNpc));
        reg.register(EventValue.builder(QuestDialogueAnswerEvent.class, String.class).patterns("dialogue answer").getter(QuestDialogueAnswerEvent::getAnswer).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(QuestIndicatorUpdateEvent.class, QuestNpc.class, QuestIndicatorUpdateEvent::getNpc));
        reg.register(EventValue.simple(QuestIndicatorUpdateEvent.class, QuestIndicator.class, QuestIndicatorUpdateEvent::getIndicator));
        reg.register(EventValue.builder(QuestIndicatorUpdateEvent.class, QuestIndicator.class).patterns("old indicator").getter(QuestIndicatorUpdateEvent::getPastIndicator).time(EventValue.Time.NOW).build());

        // Événement interne des sections « register quest action / condition »
        reg.register(EventValue.simple(QuestScriptEvent.class, QuestNpc.class, QuestScriptEvent::getNpc));
        reg.register(EventValue.simple(QuestScriptEvent.class, Entity.class, QuestScriptEvent::getEntity));
    }
}
