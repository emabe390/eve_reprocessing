import com.google.ortools.Loader;
import com.google.ortools.linearsolver.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Solver {
    private static double INF = Double.POSITIVE_INFINITY;

    static void main() throws Exception {
        Loader.loadNativeLibraries();
        System.out.println("OR-Tools loaded successfully!");
        System.out.println(System.getProperty("java.library.path"));
        Cache.initialize();
        try {
            solve();
        } finally {
            Cache.close();
        }
    }

    static double apiToPrice(Cache.APIResponse apiResponse) {
        return apiResponse.buyAvgFivePercent;
    }

    static void solve() throws IOException {

        MPSolver solver = MPSolver.createSolver("GLOP");


        // GUI:
        Map<String, Integer> required = new HashMap<>();
        required.put("Tritanium", 10000000);
        double reprocessingQuota = 0.5;
        double transportationCostPerM3 = 300;
        // END GUI

        Map<Integer, List<Pair<Integer, Double>>> invTypeMaterials = Cache.getInvTypeMaterials();


        Map<Integer, MPConstraint> constraintMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (entry.getValue() > 0) {
                Debug.print("Requesting " + entry.getValue() + " " + entry.getKey());
                constraintMap.put(Cache.getItemId(entry.getKey()), solver.makeConstraint(entry.getValue(), INF));
            }
        }

        int itemsUnderConsideration = 2 * (invTypeMaterials.size() + constraintMap.size());
        int originalUndercons = invTypeMaterials.size() + constraintMap.size();

        int index = 0;

        int[] itemIds = new int[itemsUnderConsideration];
        double[] price = new double[itemsUnderConsideration];
        double[] volume = new double[itemsUnderConsideration];
        Map<Integer, double[]> yields = new HashMap<>();


        MPVariable[] buy = new MPVariable[itemsUnderConsideration];
        Map<Integer, MPVariable[]> convs = new HashMap<>();


        Map<Integer, MPConstraint> resourceConstraints = new HashMap<>();

        MPObjective objective = solver.objective();
        objective.setMinimization();


        for (String name : required.keySet().stream().sorted().toList()) {
            yields.put(Cache.getItemId(name), new double[itemsUnderConsideration]);
        }

        // Add all resources
        for (String name : required.keySet().stream().sorted().toList()) {
            int itemId = Cache.getItemId(name);
            itemIds[index] = itemId;
            price[index] = apiToPrice(Cache.marketValue(itemId));
            volume[index] = Cache.idToVolume(itemId);
            yields.get(itemId)[index] = 1;

            buy[index] = solver.makeNumVar(0, INF, "Buy_" + itemId);
            convs.computeIfAbsent(itemId, _ -> new MPVariable[itemsUnderConsideration])[index] = solver.makeNumVar(0, INF, "Conv_" + itemId);

            resourceConstraints.put(itemId, solver.makeConstraint(required.get(name), INF));
            resourceConstraints.get(itemId).setCoefficient(convs.get(itemId)[index], yields.get(itemId)[index]);

            MPConstraint limit = solver.makeConstraint(0, INF);
            limit.setCoefficient(convs.get(itemId)[index], 1);
            limit.setCoefficient(buy[index], -1);

            objective.setCoefficient(buy[index], price[index]);
            objective.setCoefficient(buy[index], transportationCostPerM3 * volume[index]);

            index++;
        }

        // Add all items
        for (Map.Entry<Integer, List<Pair<Integer, Double>>> entry : invTypeMaterials.entrySet()) {
            int itemId = entry.getKey();
            List<Pair<Integer, Double>> reprocessedValues = entry.getValue();
            itemIds[index] = itemId;
            price[index] = apiToPrice(Cache.marketValue(itemId));
            volume[index] = Cache.idToVolume(itemId);
            for (Pair<Integer, Double> repr : reprocessedValues) {
                yields.computeIfAbsent(repr.first, _ -> new double[itemsUnderConsideration])[index] = repr.second * reprocessingQuota;
            }

            buy[index] = solver.makeNumVar(0, INF, "Buy_" + itemId);
            MPConstraint limit = solver.makeConstraint(0, INF);
            for (Pair<Integer, Double> repr : reprocessedValues) {
                if (!resourceConstraints.containsKey(repr.first)) continue;
                MPVariable conv = solver.makeNumVar(0, INF, "Conv_" + itemId);
                convs.computeIfAbsent(repr.first, _ -> new MPVariable[itemsUnderConsideration])[index] = conv;

                resourceConstraints.get(repr.first).setCoefficient(conv, yields.get(repr.first)[index]);

                limit.setCoefficient(conv, 1);
            }
            limit.setCoefficient(buy[index], -1);

            index++;
        }

        System.out.println("itemsUnderConsideration = " + itemsUnderConsideration);
        System.out.println("originalUndercons = " + originalUndercons);
        System.out.println("index = " + index);

        System.out.println("Number of constraints: " + solver.constraints().length);
        solver.solve();/*
        for (int i = 0; i < index-1; i++) {
            double bought = buy[i].solutionValue();

            if (bought < 1e-9) continue;

            System.out.printf(
                    "Item %s: buy=%.3f\n",
                    Cache.getItemName(itemIds[i]), bought
            );
        }*/
    }
}
