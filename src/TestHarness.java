import java.util.*;

public class TestHarness {

    static List<int[]> bruteForceDiff(Object[] a, Object[] b) {
        int n = a.length, m = b.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) dp[i][j] = dp[i + 1][j + 1] + 1;
                else dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<int[]> script = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                script.add(new int[]{MyersDiff.KEEP, i, j});
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                script.add(new int[]{MyersDiff.DELETE, i, -1});
                i++;
            } else {
                script.add(new int[]{MyersDiff.INSERT, -1, j});
                j++;
            }
        }
        while (i < n) { script.add(new int[]{MyersDiff.DELETE, i, -1}); i++; }
        while (j < m) { script.add(new int[]{MyersDiff.INSERT, -1, j}); j++; }
        return script;
    }

    static int editCount(List<int[]> script) {
        int c = 0;
        for (int[] e : script) if (e[0] != MyersDiff.KEEP) c++;
        return c;
    }

    // Validate: applying the script to 'a' must reproduce 'b' exactly, and
    // every a-index / b-index must be visited in strictly increasing order
    // with no gaps or repeats.
    static boolean validate(Object[] a, Object[] b, List<int[]> script) {
        int ai = 0, bi = 0;
        for (int[] e : script) {
            if (e[0] == MyersDiff.KEEP) {
                if (e[1] != ai || e[2] != bi) return false;
                if (!a[ai].equals(b[bi])) return false;
                ai++; bi++;
            } else if (e[0] == MyersDiff.DELETE) {
                if (e[1] != ai) return false;
                ai++;
            } else { // INSERT
                if (e[2] != bi) return false;
                bi++;
            }
        }
        return ai == a.length && bi == b.length;
    }

    static Object[] arr(String s) {
        String[] parts = s.split("");
        return parts;
    }

    public static void main(String[] args) {
        // 1. Classic paper example: ABCABBA -> CBABAC, expect D=5
        {
            Object[] a = arr("ABCABBA");
            Object[] b = arr("CBABAC");
            List<int[]> got = MyersDiff.diff(a, b);
            int d = editCount(got);
            boolean valid = validate(a, b, got);
            System.out.println("Test1 (paper example) D=" + d + " valid=" + valid + " expected D=5");
            if (d != 5 || !valid) System.out.println("  FAIL script=" + describe(got, a, b));
        }

        // 2. abc -> axc from assignment PDF
        {
            Object[] a = arr("abc");
            Object[] b = arr("axc");
            List<int[]> got = MyersDiff.diff(a, b);
            System.out.println("Test2 (abc->axc) D=" + editCount(got) + " valid=" + validate(a,b,got));
            System.out.println("  script=" + describe(got, a, b));
        }

        // 3. Empty cases
        {
            Object[] a = new Object[0];
            Object[] b = new Object[0];
            List<int[]> got = MyersDiff.diff(a, b);
            System.out.println("Test3 (empty/empty) size=" + got.size() + " (expect 0)");
        }
        {
            Object[] a = arr("abc");
            Object[] b = new Object[0];
            List<int[]> got = MyersDiff.diff(a, b);
            System.out.println("Test4 (abc/empty) D=" + editCount(got) + " valid=" + validate(a,b,got));
        }
        {
            Object[] a = new Object[0];
            Object[] b = arr("abc");
            List<int[]> got = MyersDiff.diff(a, b);
            System.out.println("Test5 (empty/abc) D=" + editCount(got) + " valid=" + validate(a,b,got));
        }
        {
            Object[] a = arr("abc");
            Object[] b = arr("abc");
            List<int[]> got = MyersDiff.diff(a, b);
            System.out.println("Test6 (identical) D=" + editCount(got) + " valid=" + validate(a,b,got) + " (expect D=0)");
        }

        // 4. Fuzz test against brute force over small random strings
        Random rnd = new Random(42);
        int fails = 0;
        int trials = 20000;
        for (int t = 0; t < trials; t++) {
            int na = rnd.nextInt(8);
            int nb = rnd.nextInt(8);
            String alphabet = "ab"; // small alphabet forces lots of repeats
            StringBuilder sa = new StringBuilder(), sb = new StringBuilder();
            for (int i = 0; i < na; i++) sa.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            for (int i = 0; i < nb; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            Object[] a = arr(sa.toString());
            Object[] b = arr(sb.toString());

            List<int[]> got = MyersDiff.diff(a, b);
            List<int[]> ref = bruteForceDiff(a, b);
            int gotD = editCount(got);
            int refD = editCount(ref);
            boolean valid = validate(a, b, got);

            if (!valid || gotD != refD) {
                fails++;
                System.out.println("FUZZ FAIL a=\"" + sa + "\" b=\"" + sb + "\" gotD=" + gotD + " refD=" + refD + " valid=" + valid);
                System.out.println("  got script=" + describe(got, a, b));
                if (fails > 20) { System.out.println("Too many fails, stopping."); break; }
            }
        }
        System.out.println("Fuzz test done: " + fails + " failures out of " + trials);

        // 5. Larger alphabet fuzz (less repetition, different edit patterns)
        int fails2 = 0;
        for (int t = 0; t < trials; t++) {
            int na = rnd.nextInt(12);
            int nb = rnd.nextInt(12);
            String alphabet = "abcdef";
            StringBuilder sa = new StringBuilder(), sb = new StringBuilder();
            for (int i = 0; i < na; i++) sa.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            for (int i = 0; i < nb; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            Object[] a = arr(sa.toString());
            Object[] b = arr(sb.toString());

            List<int[]> got = MyersDiff.diff(a, b);
            List<int[]> ref = bruteForceDiff(a, b);
            int gotD = editCount(got);
            int refD = editCount(ref);
            boolean valid = validate(a, b, got);

            if (!valid || gotD != refD) {
                fails2++;
                System.out.println("FUZZ2 FAIL a=\"" + sa + "\" b=\"" + sb + "\" gotD=" + gotD + " refD=" + refD + " valid=" + valid);
                if (fails2 > 20) break;
            }
        }
        System.out.println("Fuzz2 test done: " + fails2 + " failures out of " + trials);
    }

    static String describe(List<int[]> script, Object[] a, Object[] b) {
        StringBuilder sb = new StringBuilder();
        for (int[] e : script) {
            if (e[0] == MyersDiff.KEEP) sb.append(" ").append(a[e[1]]);
            else if (e[0] == MyersDiff.DELETE) sb.append("-").append(a[e[1]]);
            else sb.append("+").append(b[e[2]]);
        }
        return sb.toString();
    }
}