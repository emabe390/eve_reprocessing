
void main() throws Exception {
    Cache.initialize();

    System.out.println(Cache.getItemName(34));
    System.out.println(Cache.getInvTypeMaterials().size());
    System.out.println(Cache.marketValue(34).buyAvgFivePercent);
    System.out.println(Cache.marketValue(34).buyAvgFivePercent);
    System.out.println(Cache.marketValue(34).buyAvgFivePercent);
    System.out.println(Cache.marketValue(34).buyAvgFivePercent);
    Cache.close();
}