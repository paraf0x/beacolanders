package ua.favn.beacolanders.data;

import java.util.ArrayList;
import java.util.List;

public record LocationData(
    int id,
    String name,
    String tag,
    boolean isPublic,
    List<CoordData> coords,
    List<String> memberNames
) {

    public record CoordData(String world, int x, int y, int z) {
    }

    static final class Builder {

        private final int id;
        private final String name;
        private final String tag;
        private final boolean isPublic;
        private final List<CoordData> coords = new ArrayList<>();
        private final List<String> memberNames = new ArrayList<>();

        Builder(int id, String name, String tag, boolean isPublic) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.isPublic = isPublic;
        }

        void addCoord(CoordData coord) {
            coords.add(coord);
        }

        void addMember(String memberName) {
            memberNames.add(memberName);
        }

        LocationData build() {
            return new LocationData(id, name, tag, isPublic,
                List.copyOf(coords), List.copyOf(memberNames));
        }
    }
}
