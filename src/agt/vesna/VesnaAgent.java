package vesna;

import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.asSyntax.parser.ParseException;
import jason.infra.local.LocalAgArch;
import jason.runtime.Settings;
import jason.NoValueException;

import static jason.asSyntax.ASSyntax.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * VesnaAgent: BDI agent with personality-driven plan selection + mask wardrobe.
 *
 * @author Andrea Gatti (original VEsNA-Pro)
 * @author Zahra Daoui (CFR + mask extension)
 */
public class VesnaAgent extends Agent {

    private Temper temper;
    protected transient Logger logger;

    @Override
    public void initAg() {
        super.initAg();
        Settings stts = getTS().getSettings();
        logger = getTS().getLogger();
        initTemper(stts);
    }

    /**
     * Initialize Temper from .jcm parameters.
     * Reads: temper, strategy, seed, cfr_learning, use_masks, mask_delta, mask_contexts
     */
    private void initTemper(Settings stts) {
        String temperStts = stts.getUserParameter("temper");
        String strategy = stts.getUserParameter("strategy");
        if (temperStts == null) return;
        if (strategy == null) strategy = "most_similar";

        boolean useMasks = "true".equals(stts.getUserParameter("use_masks"));

        // In mask mode the core is frozen, so keep the jcm temper (and seed) reproducible.
        // Only replay a persisted personality in baseline mode.
        Map<String, Object> persisted = useMasks ? null : Temper.loadPersonalityFromFile();
        if (persisted != null) {
            @SuppressWarnings("unchecked")
            Map<String, Double> savedP = (Map<String, Double>) persisted.get("personality");
            @SuppressWarnings("unchecked")
            Map<String, Double> savedM = (Map<String, Double>) persisted.get("mood");
            StringBuilder merged = new StringBuilder("temper( ");
            boolean first = true;
            for (Map.Entry<String, Double> e : savedP.entrySet()) {
                if (!first) merged.append(", ");
                merged.append(e.getKey()).append("(").append(Math.round(e.getValue()*1000.0)/1000.0).append(")");
                first = false;
            }
            for (Map.Entry<String, Double> e : savedM.entrySet()) {
                if (!first) merged.append(", ");
                merged.append(e.getKey()).append("(").append(Math.round(e.getValue()*1000.0)/1000.0).append(")[mood]");
                first = false;
            }
            merged.append(" )");
            temperStts = merged.toString();
        }

        long seed = -1;
        String seedStr = stts.getUserParameter("seed");
        if (seedStr != null) try { seed = Long.parseLong(seedStr); } catch (NumberFormatException e) {}

        boolean cfrEnabled = true;
        String cfrStr = stts.getUserParameter("cfr_learning");
        if ("false".equals(cfrStr)) cfrEnabled = false;

        double maskClip = 0.5;
        String clipStr = stts.getUserParameter("mask_delta");
        if (clipStr != null) try { maskClip = Double.parseDouble(clipStr); } catch (NumberFormatException e) {}

        double deltaThreshold = 0.5;
        String threshStr = stts.getUserParameter("delta_threshold");
        if (threshStr != null) try { deltaThreshold = Double.parseDouble(threshStr); } catch (NumberFormatException e) {}

        List<String> contexts = null;
        String ctxStr = stts.getUserParameter("mask_contexts");
        if (ctxStr != null) {
            contexts = new ArrayList<>();
            for (String c : ctxStr.split(",")) contexts.add(c.trim());
        }

        temper = new Temper(temperStts, strategy, seed, cfrEnabled, useMasks, maskClip, deltaThreshold, contexts);
    }

    @Override
    public Option selectOption(List<Option> options) {
        if (options == null || options.isEmpty()) return null;
        if (!hasTemper() || options.size() == 1 || !temper.hasOptionsAnnotation(options))
            return options.remove(0);
        Option selected = temper.selectOption(options);
        System.out.println(temper.toString());
        return selected;
    }

    @Override
    public Intention selectIntention(Queue<Intention> intentions) {
        if (!hasTemper() || intentions.size() == 1 || !temper.hasIntentionsAnnotation(intentions))
            return intentions.poll();
        return temper.selectIntention(intentions);
    }

    private boolean hasTemper() { return temper != null; }
    public Temper getTemper() { return temper; }

    /** Perform a body action (for embodied agent compatibility). */
    public void perform(Object action) {
        // No-op in simulation mode; used by jump/rotate/walk via actions
    }
}
