package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Color;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.util.Transformation;

import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.BlockDisplayData;
import skwhy.data.DisplayData;
import skwhy.data.ItemDisplayData;
import skwhy.data.TextDisplayData;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

public class DisplayFromReal extends SimpleExpression<DisplayData> {

    private Expression<Entity> entityExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        entityExpr = (Expression<Entity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable DisplayData[] get(Event event) {
        Entity entity = entityExpr.getSingle(event);

        // Vérifie que l'entité est bien une Display entity
        if (!(entity instanceof Display display)) return null;

        DisplayData result = buildFromDisplay(display);
        return result != null ? new DisplayData[]{ result } : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction depuis l'entité réelle
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable DisplayData buildFromDisplay(Display display) {

        // ── Transformation (scale, translation, rotations) ────────────────────
        Transformation tf = display.getTransformation();

        Vec3 scale = new Vec3(tf.getScale());
        Vec3 translation = new Vec3(tf.getTranslation());
        Quat4 leftRotation = new Quat4(tf.getLeftRotation());
        Quat4 rightRotation = new Quat4(tf.getRightRotation());

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
    public Class<? extends DisplayData> getReturnType() { return DisplayData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "display from " + entityExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayFromReal.class, DisplayData.class)
                .addPattern("[a] [fake] display from %entity%")
                .build()
        );
    }
}