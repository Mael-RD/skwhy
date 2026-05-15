package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.util.Kleenean;
import ch.njol.skript.lang.SkriptParser.ParseResult;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Color;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.util.Transformation;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.util.SimpleExpression;

import skwhy.data.BlockDisplayData;
import skwhy.data.DisplayData;
import skwhy.data.DisplayGroupData;
import skwhy.data.ItemDisplayData;
import skwhy.data.TextDisplayData;
import skwhy.data.Quat4;
import skwhy.data.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateDisplayGroup extends SimpleExpression<DisplayGroupData> {

    private Expression<DisplayData> displaysExpr;
    private Expression<Player> playersExpr;
    private Location location;
    private Entity entity;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {
    
        boolean hasDisplays = (pr.mark & 1) != 0;
        boolean hasPlayers = (pr.mark & 2) != 0;

        if (hasDisplays && hasPlayers) {
            displaysExpr = (Expression<DisplayData>) exprs[1];
            playersExpr = (Expression<Player>) exprs[2];
        } else if (hasDisplays) {
            displaysExpr = (Expression<DisplayData>) exprs[1];
        } else if (hasPlayers) {
            playersExpr = (Expression<Player>) exprs[2];
        }

        Object pos = exprs[0];

        if (pos instanceof Entity entity) {
            this.entity = entity;
        } else if (pos instanceof Location location) {
            this.location = location;
        } else {
            return false;
        }

        return true;
    }

    @Override
    protected @Nullable DisplayGroupData[] get(Event event) {
        List<DisplayData> displays = displaysExpr != null ? Arrays.asList(displaysExpr.getAll(event)) : new ArrayList<>();
        List<Player> players = playersExpr != null ? Arrays.asList(playersExpr.getAll(event)) : new ArrayList<>();
        if (entity != null) {
            return new DisplayGroupData[] { new DisplayGroupData(displays, players, entity) {} };
        } else if (location != null) {
            return new DisplayGroupData[] { new DisplayGroupData(displays, players, location) {} };
        } else {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction depuis l'entité réelle
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable DisplayData buildFromDisplay(Display display) {

        // ── Transformation (scale, translation, rotations) ────────────────────
        Transformation tf = display.getTransformation();

        Vec3 scale = new Vec3(tf.getScale());
        Vec3 translation = new Vec3(tf.getTranslation());
        Quat4 leftRotation = new Quat4(
            tf.getLeftRotation().x(),
            tf.getLeftRotation().y(),
            tf.getLeftRotation().z(),
            tf.getLeftRotation().w()
        );
        Quat4 rightRotation = new Quat4(
            tf.getRightRotation().x(),
            tf.getRightRotation().y(),
            tf.getRightRotation().z(),
            tf.getRightRotation().w()
        );

        // ── Propriétés communes ───────────────────────────────────────────────
        Color glowOverride = display.getGlowColorOverride();
        int glowColor = (glowOverride != null) ? glowOverride.asRGB() : -1;

        float shadowRadius       = display.getShadowRadius();
        float shadowStrength     = display.getShadowStrength();
        float viewRange          = display.getViewRange();
        int   billboardMode      = display.getBillboard().ordinal(); // FIXED=0 VERTICAL=1 HORIZONTAL=2 CENTER=3
        int   interpolationStart = display.getInterpolationDelay();
        int   interpolationDuration = display.getInterpolationDuration();

        // ── Dispatch selon le sous-type ───────────────────────────────────────
        if (display instanceof BlockDisplay blockDisplay) {
            return buildBlock(blockDisplay,
                scale, translation, leftRotation, rightRotation,
                glowColor, shadowRadius, shadowStrength, viewRange,
                billboardMode, interpolationStart, interpolationDuration);

        } else if (display instanceof ItemDisplay itemDisplay) {
            return buildItem(itemDisplay,
                scale, translation, leftRotation, rightRotation,
                glowColor, shadowRadius, shadowStrength, viewRange,
                billboardMode, interpolationStart, interpolationDuration);

        } else if (display instanceof TextDisplay textDisplay) {
            return buildText(textDisplay,
                scale, translation, leftRotation, rightRotation,
                glowColor, shadowRadius, shadowStrength, viewRange,
                billboardMode, interpolationStart, interpolationDuration);
        }

        return null;
    }

    // ── BlockDisplay ──────────────────────────────────────────────────────────

    private BlockDisplayData buildBlock(BlockDisplay entity,
                                        Vec3 scale, Vec3 translation,
                                        Quat4 leftRot, Quat4 rightRot,
                                        int glowColor, float shadowRadius, float shadowStrength,
                                        float viewRange, int billboardMode,
                                        int interpolationStart, int interpolationDuration) {
        // getAsString(false) = format court sans namespace si possible
        String blockData = entity.getBlock().getAsString(false);

        return new BlockDisplayData(
            scale, translation, leftRot, rightRot,
            glowColor, shadowRadius, shadowStrength, viewRange,
            billboardMode, interpolationStart, interpolationDuration,
            blockData
        );
    }

    // ── ItemDisplay ───────────────────────────────────────────────────────────

    private ItemDisplayData buildItem(ItemDisplay entity,
                                      Vec3 scale, Vec3 translation,
                                      Quat4 leftRot, Quat4 rightRot,
                                      int glowColor, float shadowRadius, float shadowStrength,
                                      float viewRange, int billboardMode,
                                      int interpolationStart, int interpolationDuration) {
        // Item — null si slot vide
        String itemStack = (entity.getItemStack() != null)
            ? entity.getItemStack().getType().name()
            : "AIR";

        // NONE=0 THIRDPERSON_LEFT_HAND=1 THIRDPERSON_RIGHT_HAND=2
        // FIRSTPERSON_LEFT_HAND=3 FIRSTPERSON_RIGHT_HAND=4
        // HEAD=5 GUI=6 GROUND=7 FIXED=8
        int displayMode = entity.getItemDisplayTransform().ordinal();

        return new ItemDisplayData(
            scale, translation, leftRot, rightRot,
            glowColor, shadowRadius, shadowStrength, viewRange,
            billboardMode, interpolationStart, interpolationDuration,
            itemStack, displayMode
        );
    }

    // ── TextDisplay ───────────────────────────────────────────────────────────

    private TextDisplayData buildText(TextDisplay entity,
                                      Vec3 scale, Vec3 translation,
                                      Quat4 leftRot, Quat4 rightRot,
                                      int glowColor, float shadowRadius, float shadowStrength,
                                      float viewRange, int billboardMode,
                                      int interpolationStart, int interpolationDuration) {
        // Convertit le Component Adventure en String avec codes &
        String text = LegacyComponentSerializer.legacyAmpersand()
            .serialize(entity.text());

        // Fond — null si non défini
        Color bgColor = entity.getBackgroundColor();
        int backgroundColor = (bgColor != null) ? bgColor.asRGB() : 0;

        // CENTER=0 LEFT=1 RIGHT=2
        int textAlignment = switch (entity.getAlignment()) {
            case LEFT  -> 1;
            case RIGHT -> 2;
            default    -> 0; // CENTER
        };

        int     lineWidth  = entity.getLineWidth();
        boolean seeThrough = entity.isSeeThrough();

        return new TextDisplayData(
            scale, translation, leftRot, rightRot,
            glowColor, shadowRadius, shadowStrength, viewRange,
            billboardMode, interpolationStart, interpolationDuration,
            text, backgroundColor, textAlignment, lineWidth, seeThrough
        );
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends DisplayGroupData> getReturnType() { return DisplayGroupData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "display group from " + displaysExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CreateDisplayGroup.class, DisplayGroupData.class)
                .addPattern("[a] [fake] display group at %location/entity% [from %displaydatas%] [with %players%]")
                .build()
        );
    }
}