package au.com.mono.launcher;

/* loaded from: classes.dex */
final class FolderGrid {
    final int height;
    final int insetX;
    final int insetY;
    final int maxTileHeight;
    final int rows;
    final int width;

    FolderGrid(int i, int i2, int i3, int i4, int i5, int i6) {
        this.width = Math.max(0, i);
        this.height = Math.max(0, i2);
        this.rows = Math.max(1, i3);
        this.insetX = i4;
        this.insetY = i5;
        this.maxTileHeight = i6;
    }

    int left(int i) {
        return (((i % 4) * this.width) / 4) + this.insetX;
    }

    int top(int i) {
        return (((i / 4) * this.height) / this.rows) + this.insetY;
    }

    int tileWidth(int i) {
        int i2 = i % 4;
        return Math.max(1, ((((i2 + 1) * this.width) / 4) - ((i2 * this.width) / 4)) - (this.insetX * 2));
    }

    int tileHeight(int i) {
        int i2 = i / 4;
        return Math.max(1, Math.min(this.maxTileHeight, ((((i2 + 1) * this.height) / this.rows) - ((i2 * this.height) / this.rows)) - (this.insetY * 2)));
    }

    int drop(float f, float f2) {
        return (Math.max(0, Math.min(this.rows - 1, (int) ((f2 * this.rows) / Math.max(1, this.height)))) * 4) + Math.max(0, Math.min(3, (int) ((f * 4.0f) / Math.max(1, this.width))));
    }
}
