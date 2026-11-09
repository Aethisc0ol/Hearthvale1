package com.hearthvale.dialogue;

import com.hearthvale.data.Effects;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.Script;
import com.hearthvale.world.Relationship;

import java.util.List;
import java.util.Random;

/** Fallback lines used when an NPC's JSON doesn't define its own. */
public final class DefaultLines {
    private static final Random R = new Random();

    private DefaultLines() {}

    private static String pick(String... a) { return a[R.nextInt(a.length)]; }

    public static Script.Line greeting(Relationship rel) {
        int h = rel.hearts();
        String t = h < 3 ? pick("Hello there, {player}.", "Good to see you.", "Oh, hi. Can I help you with something?")
                : h < 7 ? pick("{player}! Nice to see a friendly face.", "How's your day going, {player}?", "Always a pleasure, {player}.")
                : pick("{player}! I was hoping you'd stop by.", "There you are! I was just thinking about you.", "You always know when to show up, {player}.");
        return Script.Line.say(t);
    }

    public static String reaction(String category) {
        return switch (category) {
            case "loved" -> pick("{item}?! This is wonderful - thank you so much!", "I love it! How did you know?");
            case "liked" -> pick("{item}! That's really thoughtful.", "Thank you, {player}. I like this.");
            case "disliked" -> pick("Oh. {item}... thanks, I suppose.", "Hm. Not really my thing.");
            case "hated" -> pick("Why would you give me {item}?", "...Please don't do that again.");
            default -> pick("Oh, thank you, {player}.", "That's kind of you.");
        };
    }

    public static Script arrival(NpcDefinition d) {
        Script.Line ask = new Script.Line(null, "I'd love to settle down here, if you'll have me.", List.of(
                new Script.Choice("Welcome to the village!", Script.of("Thank you, {player}. I won't forget this."), List.of(Effects.friendship(30))),
                new Script.Choice("Make yourself at home.", Script.of("That's the nicest thing I've heard in weeks."), List.of(Effects.friendship(20)))
        ), List.of());
        return new Script(List.of(
                Script.Line.say("Hello! My name is {npc}. I've been on the road a long time, and your hearth fire caught my eye."),
                ask));
    }

    public static Script bouquetAccept() {
        return Script.of("Is this... for me? Flowers?", "*{npc} smiles shyly.*", "Yes, {player}. I'd like to see where this goes.");
    }

    public static Script notRomanceable() {
        return Script.of("That's sweet of you, {player}, but I think of you as a dear friend - nothing more.");
    }

    public static Script tooEarly() {
        return Script.of("That's a lovely gesture, but... I think we should get to know each other a little better first.");
    }

    public static Script pendantAccept() {
        return Script.of("Is that a pendant? {player}... are you asking what I think you're asking?",
                "Yes. A thousand times yes. Give me three days, and meet me here for the wedding.");
    }

    public static Script pendantReject() {
        return Script.of("Oh! I... I'm not ready for something like that. Please, give me more time.");
    }

    public static Script wedding() {
        return new Script(List.of(
                Script.Line.say("*The village gathers at the hearth. {npc} stands before you, hands trembling.*"),
                new Script.Line(null, "{player}, will you stay with me - for good?", List.of(
                        new Script.Choice("I will.", Script.of("Then it's settled."), List.of()),
                        new Script.Choice("Always.", Script.of("Then it's settled."), List.of())
                ), List.of()),
                Script.Line.say("*Cheers ring out across the village.*")));
    }

    public static Script affection() {
        return Script.of(pick("*{npc} leans into the hug and smiles.*", "*{npc} squeezes your hand.*", "*{npc} laughs softly and kisses your cheek.*"));
    }
}
