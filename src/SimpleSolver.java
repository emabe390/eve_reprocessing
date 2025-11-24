import java.time.LocalDate;
import java.util.*;

public class SimpleSolver {
    static void main() throws Exception {
        Cache.initialize();
        try {
            // this is the list of resources you care about
            List<Integer> resources = new ArrayList<>();
            resources.add(34);
            resources.add(35);
            resources.add(36);
            resources.add(37);
            resources.add(38);
            resources.add(39);
            resources.add(40);
            new SimpleSolver().solve(resources, 0.5f, 450);
        } finally {
            Cache.close();
        }

    }

    static double apiToPrice(Cache.APIResponse apiResponse) {
        if (apiResponse.buyOrders == 0) return 0;
        if (apiResponse.sellOrders == 0) return 0;
        if (apiResponse.buyVolume < 100) return 0;
        if (apiResponse.sellVolume < 100) return 0;
        return apiResponse.sellAvgFivePercent;
    }

    public List<String> solve(List<Integer> resources, float reprocessing, float costPerM3) throws Exception {
        List<String> headers = new ArrayList<>();
        headers.add("Item");
        List<String> rowName = new ArrayList<>();
        List<Double[]> prices = new ArrayList<>();
        Map<Integer, Integer> resourceToIndex = new HashMap<>();
        Map<Integer, Double> resourceToPrice = new HashMap<>();
        int index = 0;
        for (int resource : resources) {
            resourceToIndex.put(resource, index);
            headers.add(Cache.getItemName(resource));
            rowName.add(Cache.getItemName(resource));

            Double[] priceList = new Double[resources.size()];
            prices.add(priceList);
            double price = apiToPrice(Cache.marketValue(resource)) + costPerM3 * Cache.idToVolume(resource);
            priceList[index] = price;
            resourceToPrice.put(resource, priceList[index]);

            index++;
        }

        HashMap<Double, String> results = new HashMap<>();

        for (Map.Entry<Integer, List<Pair<Integer, Double>>> entry : Cache.getInvTypeMaterials().entrySet()) {
            Integer itemId = entry.getKey();
            List<Pair<Integer, Double>> val = entry.getValue();
            /*if (Cache.getItemName(itemId).equals("Nova Torpedo")) {
                Debug.DEBUG = true;
            } else {
                Debug.DEBUG = false;
            }*/

            double itemPrice = apiToPrice(Cache.marketValue(itemId));
            if (itemPrice == 0) continue;

            Map<Integer, Double> priceForAllMaterials = new HashMap<>();
            double volumeReprocessed = 0;
            double sumReprocessed = 0;
            for (Pair<Integer, Double> reprocessedResult : val) {
                if (!resourceToPrice.containsKey(reprocessedResult.first)) continue;
                double reprocessedValue = apiToPrice(Cache.marketValue(reprocessedResult.first)) * reprocessedResult.second * reprocessing;
                Debug.print(apiToPrice(Cache.marketValue(reprocessedResult.first)) + " * " + reprocessedResult.second + " * " + reprocessing);
                Debug.print(Cache.getItemName(reprocessedResult.first) + " : " + reprocessedValue);
                priceForAllMaterials.put(reprocessedResult.first, reprocessedValue);
                volumeReprocessed += Cache.idToVolume(reprocessedResult.first) * reprocessedResult.second;
                sumReprocessed += reprocessedValue;
            }

            Debug.print(itemId);
            Debug.print("Only item: " + itemPrice);
            Debug.print("Pre hauling: " + sumReprocessed + " . " + Cache.idToVolume(itemId) + " * " + costPerM3 + " = " + (Cache.idToVolume(itemId) * costPerM3));
            sumReprocessed = sumReprocessed - costPerM3 * Cache.idToVolume(itemId) + costPerM3 * volumeReprocessed;
            Debug.print("Post hauling: " + sumReprocessed);

            if (sumReprocessed > itemPrice) {
                results.put(sumReprocessed - itemPrice, Cache.getItemName(itemId));
                //System.out.println(Cache.getItemName(itemId) + "," + (sumReprocessed - itemPrice));
            }
        }

        List<String> res = new ArrayList<>();

        res.add("+ Reprocess " + LocalDate.now() + "\n");
        int len = (int) Math.floor(Math.log(results.keySet().stream().sorted().toList().getLast())) / 2;
        boolean first = true;
        int lastBlock = 0;
        int blockSize = 10000;
        for (Double key : results.keySet().stream().sorted().toList()) {
            if ((int) (key / blockSize) > lastBlock) {
                lastBlock = (int) (key / blockSize);
                res.add(String.format("++ %0" + len + "d-%0" + len + "d%n", lastBlock * blockSize, ((lastBlock + 1) * blockSize - 1)));
            }
            if (lastBlock == 0 && first) {
                first = false;
                res.add(String.format("++ %0" + len + "d-%0" + len + "d%n", 0, blockSize - 1));
            }

            res.add("-- " + results.get(key) + "\n");
        }
        return res;
    }
}
