package io.github.syst3ms.skriptparser.effects;

import io.github.syst3ms.skriptparser.Main;
import io.github.syst3ms.skriptparser.SkriptLogger;
import io.github.syst3ms.skriptparser.classes.ChangeMode;
import io.github.syst3ms.skriptparser.event.Event;
import io.github.syst3ms.skriptparser.lang.Effect;
import io.github.syst3ms.skriptparser.lang.Expression;
import io.github.syst3ms.skriptparser.parsing.ParseResult;
import io.github.syst3ms.skriptparser.registration.PatternInfos;
import io.github.syst3ms.skriptparser.types.TypeManager;
import io.github.syst3ms.skriptparser.util.ClassUtils;
import io.github.syst3ms.skriptparser.util.StringUtils;

public class EffChange extends Effect {
    public static final PatternInfos<ChangeMode> PATTERNS = new PatternInfos<>(new Object[][]{
            {"set %~objects% to %objects%", ChangeMode.SET},
            {"%~objects% = %objects%", ChangeMode.SET},
            {"add %objects% to %~objects%", ChangeMode.ADD},
            {"%~objects% += %objects%", ChangeMode.ADD},
            {"remove %objects% from %~objects%", ChangeMode.REMOVE},
            {"%~objects% -= %~objects%", ChangeMode.REMOVE},
            {"remove (all|every) %objects% from %~objects%", ChangeMode.REMOVE_ALL},
            {"(delete|clear) %~objects%", ChangeMode.DELETE},
            {"reset %~objects%", ChangeMode.RESET}
    });

    private Expression<?> changed, changeWith;
    private ChangeMode mode;

    static {
        Main.getMainRegistration().addEffect(
                EffChange.class,
                PATTERNS.getPatterns()
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, ParseResult parseResult) {
        ChangeMode mode = PATTERNS.getInfo(matchedPattern);
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) {
            changed = expressions[0];
        } else if ((matchedPattern & 1) == 1 || mode == ChangeMode.SET) {
            changed = expressions[0];
            changeWith = expressions[1];
            assignement = (matchedPattern & 1) == 1;
        } else {
            changed = expressions[1];
            changeWith = expressions[0];
        }
        this.mode = mode;
        if (changeWith == null) {
            assert mode == ChangeMode.DELETE || mode == ChangeMode.RESET;
            if (changed.acceptsChange(mode) == null) {
                SkriptLogger.error(String.format("This expression can't be %s !", mode == ChangeMode.DELETE ? "deleted" : "reset"));
                return false;
            }
        } else {
            Class<?> changeType = changeWith.getReturnType();
            Class<?>[] acceptance = changed.acceptsChange(mode);
            String changedString = changed.toString(null, false);
            if (acceptance == null) {
                switch (mode) {
                    case SET:
                        SkriptLogger.error("Can't set '" + changedString + "' to anything !");
                        break;
                    case ADD:
                        SkriptLogger.error("Can't add anything to '" + changedString + "' !");
                        break;
                    case REMOVE_ALL:
                    case REMOVE:
                        SkriptLogger.error("Can't remove anything from '" + changedString + "' !");
                }
                return false;
            } else if (!ClassUtils.containsSuperclass(acceptance, changeType)) {
                boolean array = changeType.isArray();
                String changeTypeName = StringUtils.withIndefiniteArticle(
                        TypeManager.getByClassExact(changeType).getPluralForms()[array ? 1 : 0],
                        array
                );
                switch (mode) {
                    case SET:
                        SkriptLogger.error("Can't set '" + changedString + "' to " + changeTypeName);
                        break;
                    case ADD:
                        SkriptLogger.error("Can't add " + changeTypeName + " to '" + changedString + "'");
                        break;
                    case REMOVE_ALL:
                    case REMOVE:
                        SkriptLogger.error("Can't remove " + changeTypeName + " from '" + changedString + "'");
                }
                return false;
            }
        }
        return true;
    }

    private boolean assignement;

    @Override
    public String toString(Event e, boolean debug) {
        switch (mode) {
            case SET:
                if (assignement) {
                    return String.format("%s = %s", changed.toString(e, debug), changeWith.toString(e, debug));
                } else {
                    return String.format("set %s to %s", changed.toString(e, debug), changeWith.toString(e, debug));
                }
            case ADD:
                if (assignement) {
                    return String.format("%s += %s", changed.toString(e, debug), changeWith.toString(e, debug));
                } else {
                    return String.format("add %s to %s", changeWith.toString(e, debug), changed.toString(e, debug));
                }
            case REMOVE:
                if (assignement) {
                    return String.format("%s -= %s", changed.toString(e, debug), changeWith.toString(e, debug));
                } else {
                    return String.format("remove %s from %s", changeWith.toString(e, debug), changed.toString(e, debug));
                }
            case DELETE:
            case RESET:
                return String.format("%s %s", mode.name().toLowerCase(), changed.toString(e, debug));
            case REMOVE_ALL:
                return String.format("remove all %s from %s", changeWith.toString(e, debug), changed.toString(e, debug));
            default:
                assert false;
                return "!!!unknown change mode!!!";
        }
    }

    @Override
    public void execute(Event e) {
        if (changeWith == null) {
            changed.change(e, new Object[0], mode);
        } else {
            changed.change(e, changeWith.getValues(e), mode);
        }
    }
}
