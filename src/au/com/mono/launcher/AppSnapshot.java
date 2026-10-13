package au.com.mono.launcher;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/* loaded from: classes.dex */
final class AppSnapshot {

    static final class Entry {
        final byte[] icon;
        final String id;
        final String label;

        Entry(String str, String str2, byte[] bArr) {
            this.id = str;
            this.label = str2;
            this.icon = bArr;
        }
    }

    AppSnapshot() {
    }

    static ArrayList<Entry> read(InputStream inputStream) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(inputStream));
        if (dataInputStream.readInt() != 1297042994) {
            throw new IOException("Invalid cache version");
        }
        int readInt = dataInputStream.readInt();
        if (readInt < 1 || readInt > 3000) {
            throw new IOException("Invalid app count");
        }
        ArrayList<Entry> arrayList = new ArrayList<>();
        HashSet hashSet = new HashSet();
        for (int i = 0; i < readInt; i++) {
            String readUTF = dataInputStream.readUTF();
            String readUTF2 = dataInputStream.readUTF();
            int readInt2 = dataInputStream.readInt();
            if (readUTF.isEmpty() || !hashSet.add(readUTF) || readInt2 < 1 || readInt2 > 262144) {
                throw new IOException("Invalid app entry");
            }
            byte[] bArr = new byte[readInt2];
            dataInputStream.readFully(bArr);
            arrayList.add(new Entry(readUTF, readUTF2, bArr));
        }
        return arrayList;
    }

    static void write(OutputStream outputStream, List<Entry> list) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(1297042994);
        dataOutputStream.writeInt(list.size());
        for (Entry entry : list) {
            dataOutputStream.writeUTF(entry.id);
            dataOutputStream.writeUTF(entry.label);
            dataOutputStream.writeInt(entry.icon.length);
            dataOutputStream.write(entry.icon);
        }
        dataOutputStream.flush();
    }
}
