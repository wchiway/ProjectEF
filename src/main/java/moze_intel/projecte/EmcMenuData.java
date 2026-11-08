package moze_intel.projecte;

import java.util.function.LongSupplier;
import net.minecraft.world.inventory.ContainerData;

final class EmcMenuData implements ContainerData {
    private static final int PARTS = Long.SIZE / Short.SIZE;
    private final long[] values;
    private final LongSupplier[] sources;

    EmcMenuData(int size) {
        values = new long[size];
        sources = null;
    }

    EmcMenuData(LongSupplier... sources) {
        this.sources = sources;
        values = new long[sources.length];
    }

    long value(int index) {
        return sources == null ? values[index] : sources[index].getAsLong();
    }

    @Override
    public int get(int index) {
        // Vanilla container data packets carry signed shorts.
        return (short) (value(index / PARTS) >>> ((index % PARTS) * Short.SIZE));
    }

    @Override
    public void set(int index, int value) {
        int shift = (index % PARTS) * Short.SIZE;
        int field = index / PARTS;
        values[field] = (values[field] & ~(0xffffL << shift)) | ((value & 0xffffL) << shift);
    }

    @Override
    public int getCount() {
        return values.length * PARTS;
    }
}
