package ua.favn.beacolanders.data;

public record ShopData(
    String id,
    String name,
    String world,
    int x,
    int y,
    int z,
    int stockCount
) {
}
