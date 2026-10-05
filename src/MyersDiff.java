import java.util.*;

/**
 * Linear-space Myers diff (the O(ND) algorithm with the divide-and-conquer
 * "middle snake" refinement from section 4b of Myers' 1986 paper).
 *
 * Works on any Object[] sequence, comparing elements with equals().
 * Main.java uses it for lines (Part A) and for code points (Part B).
 *
 * Produces an edit script as a list of int[3]: {type, aIndex, bIndex}
 *   type 0 = KEEP   (aIndex, bIndex both valid)
 *   type 1 = DELETE (aIndex valid, bIndex = -1)
 *   type 2 = INSERT (bIndex valid, aIndex = -1)
 */
public class MyersDiff {

    public static final int KEEP = 0;
    public static final int DELETE = 1;
    public static final int INSERT = 2;

    private final Object[] a;
    private final Object[] b;
    private final List<int[]> script = new ArrayList<>();

    // Shared scratch buffers for the middle-snake search, allocated ONCE for
    // the whole diff (sized for the top-level problem) and reused by every
    // recursive call. k (= x - y) is always a LOCAL coordinate centered on 0
    // regardless of which sub-box we're in, so a single fixed offset works
    // for every recursive call. Re-allocating these per call (one box at a
    // time, as recursion goes arbitrarily deep for files with many scattered
    // isolated edits) is what makes a naive implementation blow past the
    // time limit on large inputs -- this is the fix for that.
    private final int[] vf;
    private final int[] vb;
    private final int offset;

    private MyersDiff(Object[] a, Object[] b) {
        this.a = a;
        this.b = b;
        int maxD = (a.length + b.length) / 2 + 1;
        this.offset = maxD;
        this.vf = new int[2 * maxD + 1];
        this.vb = new int[2 * maxD + 1];
    }

    public static List<int[]> diff(Object[] a, Object[] b) {
        MyersDiff d = new MyersDiff(a, b);
        d.diffBox(0, a.length, 0, b.length);
        return d.script;
    }

    // Diffs the box [aLow,aHigh) x [bLow,bHigh): trims the common prefix and
    // suffix, finds the middle snake, recurses on the part before it, emits
    // the snake as KEEPs, then recurses on the part after it.
    private void diffBox(int aLow, int aHigh, int bLow, int bHigh) {
        // Trim common prefix
        while (aLow < aHigh && bLow < bHigh && a[aLow].equals(b[bLow])) {
            script.add(new int[]{KEEP, aLow, bLow});
            aLow++; bLow++;
        }
        // Trim common suffix
        int aEnd = aHigh, bEnd = bHigh;
        int suffixLen = 0;
        while (aEnd > aLow && bEnd > bLow && a[aEnd - 1].equals(b[bEnd - 1])) {
            aEnd--; bEnd--;
            suffixLen++;
        }

        int n = aEnd - aLow;
        int m = bEnd - bLow;

        if (n == 0 && m == 0) {
            // nothing left in the middle
        } else if (n == 0) {
            for (int j = bLow; j < bEnd; j++) script.add(new int[]{INSERT, -1, j});
        } else if (m == 0) {
            for (int i = aLow; i < aEnd; i++) script.add(new int[]{DELETE, i, -1});
        } else {
            int[] snake = middleSnake(aLow, aEnd, bLow, bEnd);
            // snake = {x_start, y_start, x_end, y_end} of the middle snake/diagonal
            int xs = snake[0], ys = snake[1], xe = snake[2], ye = snake[3];
            diffBox(aLow, xs, bLow, ys);
            // The middle snake is a diagonal run of matching items: emit as KEEPs.
            int len = xe - xs;
            for (int off = 0; off < len; off++) {
                script.add(new int[]{KEEP, xs + off, ys + off});
            }
            diffBox(xe, aEnd, ye, bEnd);
        }

        // Emit trimmed suffix keeps
        for (int off = 0; off < suffixLen; off++) {
            script.add(new int[]{KEEP, aEnd + off, bEnd + off});
        }
    }

    /**
     * Finds the middle snake of the box [aLow,aHigh) x [bLow,bHigh) using the
     * combined forward/backward search (Myers 1986, section 4b).
     * Returns {x_start, y_start, x_end, y_end} describing the middle snake:
     * the diagonal run from (x_start,y_start) to (x_end,y_end) that the
     * shortest edit path passes through.
     */
    private int[] middleSnake(int aLow, int aHigh, int bLow, int bHigh) {
        int N = aHigh - aLow;
        int M = bHigh - bLow;
        int maxD = (N + M + 1) / 2;
        int delta = N - M;

        // Reuse the shared scratch buffers (allocated once in the constructor).
        // vf[k]  (offset by `offset`): furthest forward x reached on forward-diagonal k.
        // vb[k'] (offset by `offset`): furthest backward "distance-from-end" x reached
        //         on the backward search's OWN diagonal k' (reversed coordinates,
        //         where reversed position (u,v) = (N-x, M-y), k' = u - v = delta - k).
        // k is always a LOCAL coordinate (x - y within THIS box), so indices used
        // here never depend on aLow/bLow -- only on maxD, which is <= the top-level
        // maxD the buffers were sized for. Every index read below was written
        // earlier in THIS SAME call (standard Myers invariant), so stale data left
        // over from a different box is never observed.
        int maxD0 = this.offset; // buffer's own offset constant, reused across all calls
        int[] vf = this.vf;
        int[] vb = this.vb;

        boolean deltaOdd = (delta % 2) != 0;

        vf[1 + maxD0] = 0;   // forward start, diagonal k=1 convention for d=0 extension
        vb[1 + maxD0] = 0;   // backward start, own diagonal k'=1 convention for d=0 extension

        for (int d = 0; d <= maxD; d++) {
            // ---- Forward pass: extend forward diagonals k = -d..d ----
            for (int k = -d; k <= d; k += 2) {
                int idx = k + maxD0;
                int x;
                if (k == -d || (k != d && vf[idx - 1] < vf[idx + 1])) {
                    x = vf[idx + 1];
                } else {
                    x = vf[idx - 1] + 1;
                }
                int y = x - k;
                int xStart = x, yStart = y;
                while (x < N && y < M && a[aLow + x].equals(b[bLow + y])) {
                    x++; y++;
                }
                vf[idx] = x;

                // Check overlap with backward search (which has completed rounds 0..d-1).
                int kPrime = delta - k;
                if (deltaOdd && kPrime >= -(d - 1) && kPrime <= (d - 1)) {
                    int backU = vb[kPrime + maxD0];   // backward's u = N - realX
                    int backRealX = N - backU;
                    if (x >= backRealX) {
                        return new int[]{aLow + xStart, bLow + yStart, aLow + x, bLow + y};
                    }
                }
            }

            // ---- Backward pass: extend backward's own diagonals k' = -d..d ----
            for (int kPrime = -d; kPrime <= d; kPrime += 2) {
                int idx = kPrime + maxD0;
                int u;
                if (kPrime == -d || (kPrime != d && vb[idx - 1] < vb[idx + 1])) {
                    u = vb[idx + 1];
                } else {
                    u = vb[idx - 1] + 1;
                }
                int v = u - kPrime;
                int uStart = u, vStart = v;
                while (u < N && v < M
                        && a[aHigh - u - 1].equals(b[bHigh - v - 1])) {
                    u++; v++;
                }
                vb[idx] = u;

                // Check overlap with forward search (which has completed rounds 0..d, since
                // the forward pass for this same d already ran above).
                int k = delta - kPrime;
                if (!deltaOdd && k >= -d && k <= d) {
                    int fwdX = vf[k + maxD0];
                    int backRealX = N - u;
                    if (fwdX >= backRealX) {
                        int realXStart = N - uStart;
                        int realYStart = M - vStart;
                        int realXEnd = N - u;
                        int realYEnd = M - v;
                        return new int[]{aLow + realXEnd, bLow + realYEnd, aLow + realXStart, bLow + realYStart};
                    }
                }
            }
        }

        throw new IllegalStateException("middleSnake: no snake found (should be unreachable) N=" + N + " M=" + M);
    }
}