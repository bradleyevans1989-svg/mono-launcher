package au.com.mono.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/* loaded from: classes.dex */
final class LearningDeck {
    LearningDeck() {
    }

    static ArrayList<Integer> remaining(String str, ArrayList<Integer> arrayList, int i, Random random) {
        ArrayList<Integer> arrayList2 = new ArrayList<>();
        if (!str.isEmpty()) {
            for (String str2 : str.split(",")) {
                try {
                    int parseInt = Integer.parseInt(str2);
                    if (arrayList.contains(Integer.valueOf(parseInt)) && !arrayList2.contains(Integer.valueOf(parseInt))) {
                        arrayList2.add(Integer.valueOf(parseInt));
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        if (arrayList2.isEmpty()) {
            arrayList2.addAll(arrayList);
            Collections.shuffle(arrayList2, random);
            if (arrayList2.size() > 1 && arrayList2.get(0).intValue() == i) {
                Collections.swap(arrayList2, 0, 1);
            }
        }
        return arrayList2;
    }
}
